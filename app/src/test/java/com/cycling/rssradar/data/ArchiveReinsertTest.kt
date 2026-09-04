package com.cycling.rssradar.data

import com.cycling.rssradar.data.db.ArchivedArticleTombstoneEntity
import com.cycling.rssradar.data.db.ArticleDao
import com.cycling.rssradar.data.db.ArticleEntity
import com.cycling.rssradar.data.db.ArticleFeedLink
import com.cycling.rssradar.data.db.ArticleIdLink
import com.cycling.rssradar.data.db.FeedDao
import com.cycling.rssradar.data.db.FeedEntity
import com.cycling.rssradar.data.parser.RssParser
import com.cycling.rssradar.core.domain.rss.HttpFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.lang.reflect.Proxy
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME

/**
 * 回归测试（issue「归档后刷新文章复活」）：
 * 归档按保留期真删到期文章 → 用户手动刷新 → feed XML 里还挂着这些旧条目，
 * RefreshEngine 按 link 查不到 → 当成新文章重新插入。用户看到的就是「删了又回来」。
 *
 * 修复 = 墓碑（ArticleCleaner 删除前写 (feedId, link)，RefreshEngine upsert 跳过墓碑）。
 * 本测试驱动真实 ArticleCleaner + RefreshEngine（fake DAO 内存表），闭环断言：
 * 归档删掉的旧文章刷新后不复活、新鲜文章恰好一条、收藏豁免。
 */
class ArchiveReinsertTest {

    private val now: Long = 1_788_160_000_000L // 固定时刻，保证确定性
    private val day = 86_400_000L
    private val feedId = 1L
    private val oldLink = "https://example.com/old"
    private val newLink = "https://example.com/new"
    private val starredLink = "https://example.com/starred"

    /** 内存版 articles 表 + 墓碑表，替代真 Room。 */
    private class Mem {
        val articles = LinkedHashMap<Long, ArticleEntity>()
        val tombstones = LinkedHashMap<Pair<Long, String>, Long>() // (feedId, link) -> archivedAt
        var nextId = 1L
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> daoProxy(crossinline handler: (String, Array<Any?>) -> Any?): T =
        Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java),
        ) { _, method, args -> handler(method.name, args ?: emptyArray()) } as T

    private fun articleDao(mem: Mem): ArticleDao = daoProxy { name, args ->
        when (name) {
            "getIdLinkPairsByFeed" ->
                mem.articles.values.map { ArticleIdLink(it.id, it.link) }
            "getTombstonedLinks" ->
                mem.tombstones.keys.filter { it.first == args[0] }.map { it.second }
            "insertTombstones" -> {
                @Suppress("UNCHECKED_CAST")
                (args[0] as List<ArchivedArticleTombstoneEntity>).forEach {
                    mem.tombstones.putIfAbsent(it.feedId to it.link, it.archivedAt)
                }
                null
            }
            "getExpiredArticleLinks" -> {
                val cutoff = args[0] as Long
                mem.articles.values
                    .filter {
                        !it.isStarred && !it.isBookmarked &&
                            (it.publishedAt ?: it.fetchedAt) < cutoff
                    }
                    .map { ArticleFeedLink(it.feedId, it.link) }
            }
            "getArticleLinksByFeed" ->
                mem.articles.values
                    .filter { it.feedId == args[0] && !it.isStarred && !it.isBookmarked }
                    .map { ArticleFeedLink(it.feedId, it.link) }
            "deleteTombstonesOlderThan" -> {
                val cutoff = args[0] as Long
                val doomed = mem.tombstones.filterValues { it < cutoff }.keys
                doomed.forEach { mem.tombstones.remove(it) }
                doomed.size
            }
            "insertAll" -> {
                @Suppress("UNCHECKED_CAST")
                (args[0] as List<ArticleEntity>).forEach { a ->
                    val id = mem.nextId++
                    mem.articles[id] = a.copy(id = id)
                }
                null
            }
            "updateContentState" -> {
                val id = args[0] as Long
                val current = mem.articles[id] ?: return@daoProxy null
                mem.articles[id] = current.copy(
                    title = args[1] as String,
                    fetchedAt = args[10] as Long,
                )
                null
            }
            // 复刻 ArticleDao.deleteExpiredArticles 的 SQL 语义
            "deleteExpiredArticles" -> {
                val cutoff = args[0] as Long
                val doomed = mem.articles.values.filter {
                    !it.isStarred && !it.isBookmarked &&
                        (it.publishedAt ?: it.fetchedAt) < cutoff
                }
                doomed.forEach { mem.articles.remove(it.id) }
                doomed.size
            }
            // 复刻 ArticleDao.deleteByFeed 的 SQL 语义
            "deleteByFeed" -> {
                val fId = args[0] as Long
                val doomed = mem.articles.values.filter {
                    it.feedId == fId && !it.isStarred && !it.isBookmarked
                }
                doomed.forEach { mem.articles.remove(it.id) }
                doomed.size
            }
            else -> throw UnsupportedOperationException(name)
        }
    }

    private fun feedDao(): FeedDao = daoProxy { name, _ ->
        when (name) {
            "getById" -> FeedEntity(
                id = feedId,
                url = "https://example.com/feed.xml",
                title = "Example",
                createdAt = 0,
                iconUrl = "https://example.com/icon.png", // 有图标，避免触发 backfill
            )
            "getSyncEnabledFeedIds" -> listOf(feedId)
            else -> throw UnsupportedOperationException(name)
        }
    }

    private fun rssXml(pubOld: String, pubNew: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel>
        <title>Example</title><link>https://example.com</link><description>d</description>
        <item><title>Old</title><link>$oldLink</link><pubDate>$pubOld</pubDate>
          <description>old content for the article body which is long enough to be considered full text content</description></item>
        <item><title>New</title><link>$newLink</link><pubDate>$pubNew</pubDate>
          <description>new content for the article body which is long enough to be considered full text content</description></item>
        </channel></rss>
    """.trimIndent()

    private fun rfc1123(millis: Long): String =
        RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneOffset.UTC))

    private fun seed(mem: Mem, link: String, publishedAt: Long, starred: Boolean = false) {
        val id = mem.nextId++
        mem.articles[id] = ArticleEntity(
            id = id,
            feedId = feedId,
            link = link,
            title = link.substringAfterLast('/'),
            summary = null,
            publishedAt = publishedAt,
            fetchedAt = publishedAt,
            content = "<p>full body</p>",
            contentText = "full body",
            contentSource = ArticleEntity.CONTENT_SOURCE_FEED,
            isStarred = starred,
        )
    }

    private fun engine(mem: Mem): RefreshEngine = RefreshEngine(
        feedDao = feedDao(),
        articleDao = articleDao(mem),
        parser = RssParser(),
        http = HttpFetcher {
            ByteArrayInputStream(
                rssXml(rfc1123(now - 5 * day), rfc1123(now - 2 * 3_600_000L)).toByteArray(),
            )
        },
    )

    @Test
    fun `归档删除的过期文章不应在刷新后复活`() = runBlocking {
        val mem = Mem()
        seed(mem, oldLink, now - 5 * day)
        seed(mem, newLink, now - 2 * 3_600_000L)
        seed(mem, starredLink, now - 5 * day, starred = true)

        // 1) 归档：保留 3 天 → 过期文章写墓碑 + 删除，收藏豁免
        val removed = ArticleCleaner(articleDao(mem))
            .archiveExpired(cutoff = now - 3 * day, now = now)
        assertTrue("归档应删掉 1 篇, 实际=$removed", removed == 1)
        assertFalse("归档后旧文章应已删除", mem.articles.values.any { it.link == oldLink })
        assertTrue("收藏豁免", mem.articles.values.any { it.link == starredLink })
        assertTrue("旧文章应有墓碑", mem.tombstones.containsKey(feedId to oldLink))

        // 2) 用户手动刷新：feed XML 还挂着这篇旧文章（feed 一般保留最近 N 条）
        engine(mem).refreshSingle(feedId)

        // 3) 症状断言：旧文章不应复活
        assertTrue(
            "刷新后旧文章复活了：${mem.articles.values.map { it.link }}",
            mem.articles.values.none { it.link == oldLink },
        )
        // 新鲜文章不应被重复插入
        assertTrue(
            "新鲜文章应恰好 1 条",
            mem.articles.values.count { it.link == newLink } == 1,
        )
    }

    @Test
    fun `清空订阅源的文章后刷新不应复活`() = runBlocking {
        val mem = Mem()
        seed(mem, oldLink, now - 5 * day)
        seed(mem, newLink, now - 2 * 3_600_000L)
        seed(mem, starredLink, now - 5 * day, starred = true)

        // 1) 清空（issue #8）：收藏豁免
        val deleted = ArticleCleaner(articleDao(mem)).clearFeed(feedId, now)
        assertTrue("清空应删掉 2 篇, 实际=$deleted", deleted == 2)
        assertTrue("收藏豁免", mem.articles.values.any { it.link == starredLink })

        // 2) 刷新
        engine(mem).refreshSingle(feedId)

        // 3) 清空的文章不应复活
        assertTrue(
            "清空后文章复活了：${mem.articles.values.map { it.link }}",
            mem.articles.values.none { it.link == oldLink } &&
                mem.articles.values.none { it.link == newLink },
        )
    }

    @Test
    fun `墓碑到龄滚动清理`() = runBlocking {
        val mem = Mem()
        seed(mem, oldLink, now - 5 * day)

        // 写墓碑（archivedAt = now - 91 天，模拟老墓碑）
        val cleaner = ArticleCleaner(articleDao(mem))
        articleDao(mem).insertTombstones(
            listOf(ArchivedArticleTombstoneEntity(feedId, oldLink, now - 91 * day)),
        )

        // cutoff = null（永久档）也要做滚动清理
        cleaner.archiveExpired(cutoff = null, now = now)

        assertTrue(
            "90 天前的墓碑应被清理",
            mem.tombstones.isEmpty(),
        )
    }
}
