package com.cycling.rssradar.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import androidx.room.withTransaction
import com.cycling.rssradar.data.db.AppDatabase
import com.cycling.rssradar.data.db.ArticleEntity
import com.cycling.rssradar.data.db.ArticleWithFeed
import com.cycling.rssradar.data.db.DEFAULT_GROUP
import com.cycling.rssradar.data.db.FeedEntity
import com.cycling.rssradar.data.opml.OpmlParser
import com.cycling.rssradar.data.parser.ContentFetcher
import com.cycling.rssradar.data.parser.FeedProbeResult
import com.cycling.rssradar.data.parser.RssParser
import com.cycling.rssradar.ui.theme.Success
/** 订阅结果，供 UI 层区分提示文案。 */
sealed interface AddFeedResult {
    data object Success : AddFeedResult
    data object Duplicate : AddFeedResult
    data object InvalidFeed : AddFeedResult
    data object NetworkError : AddFeedResult
}

/** OPML 盲导结果（ADR-0004）。 */
data class OpmlImportResult(
    val imported: Int,
    val skipped: Int,
    /** 新入库订阅源的 id，供定向刷新补文章。 */
    val newFeedIds: List<Long>,
    /** OPML 中出现的所有非空分组名，供调用方注册进分组注册表。 */
    val groups: Set<String>,
)

/**
 * 订阅链路仓库：抓取 → 解析 → 持久化 → 观察文章流。
 * 所有数字与内容均来自真实抓取与数据库，不做任何本地捏造。
 */
class FeedRepository(
    private val database: AppDatabase,
    private val parser: RssParser,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** 为 null 时按需抓取不可用（见 [fetchFullContent]）。 */
    private val contentFetcher: ContentFetcher? = null,
) {
    private val feedDao = database.feedDao()
    private val articleDao = database.articleDao()

    companion object {
        /** 刷新的有界并发度（#48）：8 路并行，几百个源不再串行排队几十分钟。 */
        private const val REFRESH_CONCURRENCY = 8
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val USER_AGENT = "Mozilla/5.0 (Android) RssRadar/1.0"
    }

    private val refreshSemaphore = Semaphore(REFRESH_CONCURRENCY)

    fun search(query: String): Flow<List<ArticleWithFeed>> = articleDao.search("%$query%")

    // —— 信息流四个 tab 统一分页（规模：源 1000+、文章数万条，全量 observe 不可行） ——

    /** All tab：一次取一页。 */
    suspend fun loadArticlesPage(limit: Int, offset: Int): List<ArticleWithFeed> =
        articleDao.loadAllWithFeedPaged(limit, offset)

    /** 未读 tab：一次取一页。 */
    suspend fun loadUnreadPage(limit: Int, offset: Int): List<ArticleWithFeed> =
        articleDao.loadUnreadWithFeedPaged(limit, offset)

    /** 收藏 tab：一次取一页。 */
    suspend fun loadStarredPage(limit: Int, offset: Int): List<ArticleWithFeed> =
        articleDao.loadStarredWithFeedPaged(limit, offset)

    /** 稍后读 tab：一次取一页。 */
    suspend fun loadBookmarkedPage(limit: Int, offset: Int): List<ArticleWithFeed> =
        articleDao.loadBookmarkedWithFeedPaged(limit, offset)

    /** 订阅源文章列表（issue #51）：单源全部文章，一次取一页。 */
    suspend fun loadFeedPage(feedId: Long, limit: Int, offset: Int): List<ArticleWithFeed> =
        articleDao.loadFeedWithFeedPaged(feedId, limit, offset)

    /** 单个订阅源实体（订阅源文章列表的顶栏标题用）。 */
    suspend fun getFeed(feedId: Long): FeedEntity? = feedDao.getById(feedId)

    /** 单源刷新（订阅源文章列表顶栏动作用），返回是否成功。 */
    suspend fun refreshSingleFeed(feedId: Long): Boolean = refreshFeed(feedId)

    /**
     * 刷新全部订阅源，返回成功刷新的源数。失败源静默跳过（保留已有数据），
     * 供下拉刷新调用。
     */
    suspend fun refreshAllFeeds(): Int {
        val feeds = feedDao.getAll()
        return refreshInParallel(feeds.map { it.id })
    }

    /** 是否已有订阅源。供 UI 区分「没有源」和「刷新失败」。 */
    suspend fun hasFeeds(): Boolean = feedDao.getAll().isNotEmpty()

    fun observeFeedCount(): Flow<Int> = articleDao.observeCount()
    fun observeUnreadCount(): Flow<Int> = articleDao.observeUnreadCount()

    fun observeFeeds(): Flow<List<FeedEntity>> = feedDao.observeAll()
    fun observeFeedUnreadCounts(): Flow<Map<Long, Int>> =
        articleDao.observeUnreadCountByFeed().map { rows -> rows.associate { it.feedId to it.cnt } }

    /**
     * 仅抓取+解析一次，用于"添加订阅"页的实时预览。不写入数据库。
     * 返回 [FeedProbeResult] 供 ViewModel 决定 UI 状态。
     */
    suspend fun probeFeed(rawUrl: String): FeedProbeResult = withContext(ioDispatcher) {
        val url = normalizeUrl(rawUrl)
            ?: return@withContext FeedProbeResult.InvalidUrl
        val parsed = try {
            fetch(url).use { parser.parse(it) }
        } catch (_: IllegalArgumentException) {
            return@withContext FeedProbeResult.InvalidFeed
        } catch (_: IOException) {
            return@withContext FeedProbeResult.NetworkError
        }
        FeedProbeResult.Valid(parsed.articles.size)
    }

    suspend fun markRead(id: Long) = articleDao.markRead(id)
    suspend fun setStarred(id: Long, starred: Boolean) = articleDao.setStarred(id, starred)
    suspend fun setBookmarked(id: Long, bookmarked: Boolean) = articleDao.setBookmarked(id, bookmarked)
    suspend fun markAllRead() = articleDao.markAllRead()

    /** 已读/未读互切（长按菜单，issue #46）。 */
    suspend fun setRead(id: Long, read: Boolean) = articleDao.setRead(id, read)

    /**
     * 删除单篇文章，返回被删实体供撤销；文章不存在返回 null。
     * 只删文章本身，不碰订阅源（区别于 deleteFeed 的级联删除）。
     */
    suspend fun deleteArticle(id: Long): ArticleEntity? {
        val entity = articleDao.getWithFeed(id)?.article ?: return null
        articleDao.deleteById(id)
        return entity
    }

    /** 撤销删除：带原 id 原样插回。 */
    suspend fun restoreArticle(entity: ArticleEntity) = articleDao.restore(entity)

    // —— 订阅源 / 分组管理（issue #6） ——

    /** 移动订阅源到其他分组。 */
    suspend fun moveFeed(feedId: Long, groupName: String) = feedDao.updateGroup(feedId, groupName)

    /** 重命名订阅源标题。 */
    suspend fun renameFeed(feedId: Long, title: String) = feedDao.updateTitle(feedId, title)

    /** 删除订阅源（其文章级联删除）。 */
    suspend fun deleteFeed(feedId: Long) = feedDao.deleteFeed(feedId)

    /** 分组重命名：注册表 + feeds.groupName 批量更新。 */
    suspend fun renameGroup(oldName: String, newName: String) {
        feedDao.renameGroup(oldName, newName)
    }

    /** 删除分组：注册表删名 + feed 移回默认组。 */
    suspend fun deleteGroup(groupName: String) {
        feedDao.moveGroupToDefault(groupName, DEFAULT_GROUP)
    }

    suspend fun getArticle(id: Long): ArticleWithFeed? = articleDao.getWithFeed(id)

    /** 同源文章 id（列表序：新→旧），详情页上一篇/下一篇导航用。 */
    suspend fun getFeedArticleIds(feedId: Long): List<Long> = articleDao.getFeedArticleIds(feedId)

    /**
     * 按需抓取原网页正文（ADR-0001）：文章没有 feed 自带正文时调用。
     * 失败返回 false，调用方静默降级——这是常态（反爬/JS 页），不是错误。
     */
    suspend fun fetchFullContent(id: Long): Boolean = withContext(ioDispatcher) {
        val fetcher = contentFetcher ?: return@withContext false
        val item = articleDao.getWithFeed(id) ?: return@withContext false
        // 已有正文（feed 自带或之前抓取过）就不重复抓
        if (item.article.contentSource != ArticleEntity.CONTENT_SOURCE_NONE && item.article.content != null) {
            return@withContext true
        }
        val fetched = fetcher.fetch(item.article.link) ?: return@withContext false
        val readingMinutes = fetched.contentText.let { estimateReadingMinutes(it) }
        articleDao.updateFetchedContent(
            id = id,
            content = fetched.contentHtml,
            contentText = fetched.contentText,
            contentSource = ArticleEntity.CONTENT_SOURCE_WEB,
            readingMinutes = readingMinutes,
            coverUrl = fetched.coverUrl,
        )
        true
    }

    suspend fun addFeed(
        rawUrl: String,
        groupName: String = DEFAULT_GROUP,
        sourceType: Int = FeedEntity.SOURCE_TYPE_RSS,
    ): AddFeedResult = withContext(ioDispatcher) {
        val url = normalizeUrl(rawUrl) ?: return@withContext AddFeedResult.InvalidFeed

        if (feedDao.findIdByUrl(url) != null) return@withContext AddFeedResult.Duplicate

        val parsed = try {
            fetch(url).use { parser.parse(it) }
        } catch (_: IllegalArgumentException) {
            return@withContext AddFeedResult.InvalidFeed
        } catch (_: IOException) {
            return@withContext AddFeedResult.NetworkError
        }

        val now = System.currentTimeMillis()
        val feedId = feedDao.insert(
            FeedEntity(
                url = url,
                title = parsed.title,
                createdAt = now,
                groupName = groupName.ifBlank { DEFAULT_GROUP },
                sourceType = sourceType,
            ),
        )
        val resolvedFeedId = feedId.takeIf { it != -1L } ?: feedDao.findIdByUrl(url) ?: return@withContext AddFeedResult.Duplicate

        upsertArticles(resolvedFeedId, parsed.articles, now)
        AddFeedResult.Success
    }

    /**
     * OPML 盲导（ADR-0004）：解析 [stream] 后直接入库，不联网校验。
     * 标题取 OPML text/title，分组取 outline 嵌套路径；重复（规范化 URL 精确匹配）跳过计数。
     * 根元素非 OPML 时抛 [IllegalArgumentException]，由调用方转为提示。
     */
    suspend fun importOpml(stream: InputStream): OpmlImportResult = withContext(ioDispatcher) {
        val entries = OpmlParser.parse(stream)
        var imported = 0
        var skipped = 0
        val newIds = mutableListOf<Long>()
        val now = System.currentTimeMillis()
        entries.forEach { entry ->
            val url = normalizeUrl(entry.xmlUrl) ?: run { skipped++; return@forEach }
            if (feedDao.findIdByUrl(url) != null) { skipped++; return@forEach }
            val feedId = feedDao.insert(
                FeedEntity(
                    url = url,
                    title = entry.title,
                    createdAt = now,
                    groupName = entry.group.ifBlank { DEFAULT_GROUP },
                    sourceType = FeedEntity.SOURCE_TYPE_RSS,
                ),
            )
            val resolved = feedId.takeIf { it != -1L } ?: feedDao.findIdByUrl(url)
            if (resolved == null) { skipped++; return@forEach }
            imported++
            newIds += resolved
        }
        OpmlImportResult(
            imported = imported,
            skipped = skipped,
            newFeedIds = newIds,
            groups = entries.map { it.group }.filter { it.isNotBlank() }.toSet(),
        )
    }

    /**
     * 定向刷新一批订阅源（盲导后补文章用），返回成功的源数。
     * 失败静默跳过，语义同 [refreshAllFeeds]。
     */
    suspend fun refreshFeeds(feedIds: List<Long>): Int = refreshInParallel(feedIds)

    /**
     * 有界并发刷新（#48）：Semaphore(8) 同时处理 8 个源。
     * HttpURLConnection 无状态、Room 写入天然串行，并发安全；
     * 几百个源的总耗时从「串行累加」降为约 1/8。
     * 整体跑在 ioDispatcher 上，不让并发骨架占用调用方（Main）线程。
     */
    private suspend fun refreshInParallel(feedIds: List<Long>): Int = withContext(ioDispatcher) {
        coroutineScope {
            feedIds.map { feedId ->
                async {
                    refreshSemaphore.withPermit { refreshFeed(feedId) }
                }
            }.awaitAll().count { it }
        }
    }

    /**
     * 增量刷新：重新抓取并按 link 更新文章的内容状态。
     * 绝不覆盖用户状态（已读/收藏/稍后读），见 CONTEXT.md「用户状态」。
     * 返回是否成功抓取（网络失败 / 源失效返回 false，由调用方决定提示）。
     */
    suspend fun refreshFeed(feedId: Long): Boolean = withContext(ioDispatcher) {
        val feed = feedDao.getById(feedId) ?: return@withContext false
        val parsed = try {
            fetch(feed.url).use { parser.parse(it) }
        } catch (_: IllegalArgumentException) {
            return@withContext false
        } catch (_: IOException) {
            return@withContext false
        }
        upsertArticles(feedId, parsed.articles, System.currentTimeMillis())
        true
    }

    /** 同一 link：只更新内容状态字段，用户状态原样保留。整源一次事务（#48）。 */
    private suspend fun upsertArticles(feedId: Long, articles: List<RssParser.ParsedArticle>, now: Long) {
        if (articles.isEmpty()) return
        database.withTransaction {
            // 一次查询建 link→id 映射，替代逐篇 findIdByLink（#48：消除 N+1 写放大）
            val existing = articleDao.getIdLinkPairsByFeed(feedId).associate { it.link to it.id }
            val newArticles = mutableListOf<ArticleEntity>()
            articles.forEach { article ->
                val readingMinutes = article.contentText?.let { estimateReadingMinutes(it) }
                val contentSource = if (article.contentHtml != null) ArticleEntity.CONTENT_SOURCE_FEED else ArticleEntity.CONTENT_SOURCE_NONE
                val existingId = existing[article.link]
                if (existingId == null) {
                    newArticles += ArticleEntity(
                        feedId = feedId,
                        link = article.link,
                        title = article.title,
                        summary = article.summary,
                        content = article.contentHtml,
                        contentText = article.contentText,
                        author = article.author,
                        publishedAt = article.publishedAt,
                        fetchedAt = now,
                        coverUrl = article.coverUrl,
                        readingMinutes = readingMinutes,
                        contentSource = contentSource,
                    )
                } else {
                    articleDao.updateContentState(
                        id = existingId,
                        title = article.title,
                        summary = article.summary,
                        content = article.contentHtml,
                        contentText = article.contentText,
                        author = article.author,
                        publishedAt = article.publishedAt,
                        coverUrl = article.coverUrl,
                        readingMinutes = readingMinutes,
                        contentSource = contentSource,
                        fetchedAt = now,
                    )
                }
            }
            if (newArticles.isNotEmpty()) articleDao.insertAll(newArticles)
        }
    }

    /** 中文按 300 字/分钟，非 CJK 按 200 词/分钟，混排取较大值。来自真实正文字数，不虚构。 */
    internal fun estimateReadingMinutes(text: String): Int {
        val cjkChars = text.count { it.code in 0x4E00..0x9FFF }
        val otherWords = text.count { !((it.code in 0x4E00..0x9FFF) || it.isWhitespace()) } / 6
        val minutes = maxOf(cjkChars / 300, otherWords / 200)
        return (minutes + 1).coerceAtLeast(1)
    }

    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return try {
            URL(withScheme).toString().takeIf { it.startsWith("http") }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetch(url: String): java.io.InputStream {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            throw IOException("HTTP ${connection.responseCode}")
        }
        return connection.inputStream
    }
}
