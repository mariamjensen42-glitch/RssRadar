package com.cycling.rssradar.core.data

import com.cycling.rssradar.core.data.db.ArticleDao
import com.cycling.rssradar.core.data.db.ArticleEntity
import com.cycling.rssradar.core.data.db.ArticleWithFeed
import com.cycling.rssradar.core.data.db.ContentFetchLogDao
import com.cycling.rssradar.core.data.db.ContentFetchLogEntity
import com.cycling.rssradar.core.data.db.FeedDao
import com.cycling.rssradar.core.data.db.FeedEntity
import com.cycling.rssradar.core.data.parser.ExtractionIssue
import com.cycling.rssradar.core.data.parser.Extractor
import com.cycling.rssradar.core.data.parser.FetchFailure
import com.cycling.rssradar.core.data.parser.FetchOutcome
import com.cycling.rssradar.core.data.parser.FetchReport
import com.cycling.rssradar.core.data.parser.FetchedContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * 按需抓取模块三条写入规则的 JVM 证明（此前它们住在 FeedRepository 抽屉里，零测试）：
 * 1. 够格的不重抓（摘要不算够格——摘要型 feed 必须能抓到原文）；
 * 2. 抓短了不覆盖（不把全文越抓越少）；
 * 3. 每次抓取都留痕（成功/不完整/失败三种 outcome 都写日志）。
 *
 * ContentFetcher 是 final 类无法 fake，这里只注入「链接 → 抓取结果」的 suspend 缝
 * （与 AutoSyncTest 同模式）；三个 DAO 用 [Proxy] 走内存表。
 */
class OnDemandFetchTest {

    private val articleId = 1L
    private val feedId = 7L
    private val link = "https://example.com/post/1"

    /** 内存版 articles / feeds / content_fetch_log 三张表（嵌套类，自带实体构造）。 */
    private class Mem(
        article: ArticleEntity? = null,
        feed: FeedEntity? = null,
    ) {
        var article: ArticleEntity = article ?: defaultArticle()
        var feed: FeedEntity = feed ?: defaultFeed()
        val logs = mutableListOf<ContentFetchLogEntity>()

        companion object {
            fun defaultArticle(
                content: String? = null,
                contentText: String? = null,
                contentSource: Int = ArticleEntity.CONTENT_SOURCE_NONE,
            ) = ArticleEntity(
                id = 1L,
                feedId = 7L,
                link = "https://example.com/post/1",
                title = "标题",
                summary = "摘要",
                publishedAt = null,
                fetchedAt = 1L,
                content = content,
                contentText = contentText,
                contentSource = contentSource,
            )

            fun defaultFeed(fullContentEnabled: Boolean = true) = FeedEntity(
                id = 7L,
                url = "https://example.com/rss",
                title = "源",
                createdAt = 1L,
                fullContentEnabled = fullContentEnabled,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> daoProxy(crossinline handler: (String, Array<Any?>) -> Any?): T =
        Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java),
        ) { _, method, args -> handler(method.name, args ?: emptyArray()) } as T

    private fun articleDao(mem: Mem) = daoProxy<ArticleDao> { name, args ->
        when (name) {
            // 语义按 id 查表，不是按固定 articleId 匹配
            "getWithFeed" -> if (args[0] == mem.article.id) {
                ArticleWithFeed(
                    article = mem.article,
                    feedTitle = mem.feed.title,
                    feedGroup = mem.feed.groupName,
                    feedIconUrl = mem.feed.iconUrl,
                )
            } else {
                null
            }
            "updateFetchedContent" -> {
                // 位置参数：id, content, contentText, contentSource, readingMinutes, coverUrl, contentIncomplete
                mem.article = mem.article.copy(
                    content = args[1] as String?,
                    contentText = args[2] as String?,
                    contentSource = args[3] as Int,
                    contentIncomplete = args[6] as Boolean,
                )
                Unit
            }
            else -> null
        }
    }

    private fun feedDao(mem: Mem) = daoProxy<FeedDao> { name, args ->
        when (name) {
            "getById" -> if (args[0] == feedId) mem.feed else null
            else -> null
        }
    }

    private fun logDao(mem: Mem) = daoProxy<ContentFetchLogDao> { name, args ->
        when (name) {
            "insert" -> {
                mem.logs.add(args[0] as ContentFetchLogEntity)
                Unit
            }
            "observeProblems", "observeHostStats" ->
                kotlinx.coroutines.flow.flowOf(emptyList<ContentFetchLogEntity>())
            "clear" -> {
                mem.logs.clear()
                Unit
            }
            "historyOf" -> mem.logs.filter { it.link == args[0] }
            else -> null
        }
    }

    private fun module(mem: Mem, outcome: suspend (String) -> FetchOutcome) = OnDemandFetch(
        articleDao = articleDao(mem),
        feedDao = feedDao(mem),
        contentFetchLogDao = logDao(mem),
        fetchOutcome = outcome,
    )

    private fun success(
        text: String,
        issue: ExtractionIssue = ExtractionIssue.NONE,
    ) = FetchOutcome.Success(
        content = FetchedContent(
            contentHtml = "<p>$text</p>",
            contentText = text,
            coverUrl = null,
            title = "标题",
            author = null,
            publishedAt = null,
            pages = 1,
            isComplete = issue == ExtractionIssue.NONE || issue == ExtractionIssue.METADATA_MISSING,
            issue = issue,
            extractor = Extractor.JSOUP_FALLBACK,
        ),
        report = FetchReport(
            url = link,
            finalUrl = link,
            host = "example.com",
            statusCode = 200,
            attempts = 1,
            pages = 1,
            durationMs = 10L,
            bytes = text.length,
            contentChars = text.length,
            extractor = Extractor.JSOUP_FALLBACK,
            issue = issue,
        ),
    )

    private fun failure(kind: FetchFailure = FetchFailure.HTTP_403) = FetchOutcome.Failure(
        kind = kind,
        report = FetchReport(
            url = link,
            finalUrl = link,
            host = "example.com",
            statusCode = 403,
            attempts = 1,
            pages = 0,
            durationMs = 10L,
            bytes = 0,
            contentChars = 0,
            extractor = null,
            issue = null,
            failure = kind,
        ),
    )

    // ---- 规则 1：够格的不重抓 ----

    @Test
    fun `feed-provided full content is never refetched`() = runBlocking {
        val mem = Mem(
            article = Mem.defaultArticle(
                content = "<p>全文</p>",
                contentText = "全文",
                contentSource = ArticleEntity.CONTENT_SOURCE_FEED,
            ),
        )
        var fetched = 0
        val m = module(mem) { fetched++; success("更长的抓取正文") }

        val result = m.fetch(articleId)

        assertTrue(result)
        assertEquals(0, fetched)
        assertEquals(0, mem.logs.size) // 没抓就不该有日志
    }

    @Test
    fun `summary-level content is not qualified and does get fetched`() = runBlocking {
        // 摘要型 feed：content 列里有东西但 contentSource 是 NONE。
        // 旧实现只要 content != null 就跳过，于是这类 feed 永远抓不到原文。
        val mem = Mem(
            article = Mem.defaultArticle(
                content = "<p>只有摘要</p>",
                contentText = "只有摘要",
                contentSource = ArticleEntity.CONTENT_SOURCE_NONE,
            ),
        )
        var fetched = 0
        val m = module(mem) { fetched++; success("比摘要长得多的抓取正文，够长够长够长") }

        val result = m.fetch(articleId)

        assertTrue(result)
        assertEquals(1, fetched)
        assertEquals(1, mem.logs.size)
        assertTrue(mem.logs.single().ok)
    }

    // ---- Feed 级预设 ----

    @Test
    fun `full-content opt-out skips fetching entirely`() = runBlocking {
        val mem = Mem(feed = Mem.defaultFeed(fullContentEnabled = false))
        var fetched = 0
        val m = module(mem) { fetched++; success("随便什么") }

        val result = m.fetch(articleId)

        assertFalse(result)
        assertEquals(0, fetched)
        assertEquals(0, mem.logs.size)
    }

    // ---- 规则 2：抓短了不覆盖 ----

    @Test
    fun `shorter fetch is discarded instead of shrinking existing content`() = runBlocking {
        val mem = Mem(
            article = Mem.defaultArticle(
                contentText = "已有的很长很长的正文内容",
                contentSource = ArticleEntity.CONTENT_SOURCE_NONE,
            ),
        )
        val m = module(mem) { success("短") }

        val result = m.fetch(articleId)

        assertFalse(result)
        assertEquals("已有的很长很长的正文内容", mem.article.contentText) // 原文没被动
        assertNull(mem.article.content)
        assertEquals(1, mem.logs.size) // 但这次抓取留了痕
        assertTrue(mem.logs.single().ok)
    }

    // ---- 规则 3：三种 outcome 都留痕 ----

    @Test
    fun `failure writes a log and no content`() = runBlocking {
        val mem = Mem()
        val m = module(mem) { failure(FetchFailure.HTTP_403) }

        val result = m.fetch(articleId)

        assertFalse(result)
        assertEquals(1, mem.logs.size)
        val log = mem.logs.single()
        assertFalse(log.ok)
        assertEquals("HTTP_403", log.failure)
        assertNull(log.issue)
        assertNull(mem.article.content) // 一个字都没抓到，不写空正文
    }

    @Test
    fun `incomplete success is written and flagged`() = runBlocking {
        val mem = Mem()
        val m = module(mem) { success("很短", issue = ExtractionIssue.PAYWALL) }

        val result = m.fetch(articleId)

        assertTrue(result)
        assertEquals("很短", mem.article.contentText)
        assertTrue(mem.article.contentIncomplete) // 内容照写，但如实标记
        val log = mem.logs.single()
        assertTrue(log.ok)
        assertEquals("PAYWALL", log.issue)
    }

    @Test
    fun `unknown article is a silent no-op`() = runBlocking {
        val mem = Mem(article = Mem.defaultArticle().copy(id = 999L)) // 查不到 id=1
        var fetched = 0
        val m = module(mem) { fetched++; success("x") }

        val result = m.fetch(articleId)

        assertFalse(result)
        assertEquals(0, fetched)
        assertEquals(0, mem.logs.size)
    }
}
