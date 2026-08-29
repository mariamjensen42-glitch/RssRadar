package com.cycling.rssradar.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
/** 订阅结果，供 UI 层区分提示文案。 */
sealed interface AddFeedResult {
    data object Success : AddFeedResult
    data object Duplicate : AddFeedResult
    data object InvalidFeed : AddFeedResult
    data object NetworkError : AddFeedResult
}

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

    fun observeArticles(): Flow<List<ArticleWithFeed>> = articleDao.observeAllWithFeed()
    fun observeAllArticles(): Flow<List<ArticleWithFeed>> = articleDao.observeAllWithFeed()
    fun observeUnreadArticles(): Flow<List<ArticleWithFeed>> = articleDao.observeUnreadWithFeed()
    fun observeStarredArticles(): Flow<List<ArticleWithFeed>> = articleDao.observeStarredWithFeed()
    fun observeBookmarkedArticles(): Flow<List<ArticleWithFeed>> = articleDao.observeBookmarkedWithFeed()
    fun search(query: String): Flow<List<ArticleWithFeed>> = articleDao.search("%$query%")

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

    suspend fun getArticle(id: Long): ArticleWithFeed? = articleDao.getWithFeed(id)

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

    suspend fun addFeed(rawUrl: String, groupName: String = DEFAULT_GROUP): AddFeedResult = withContext(ioDispatcher) {
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
        val feedId = feedDao.insert(FeedEntity(url = url, title = parsed.title, createdAt = now, groupName = groupName.ifBlank { DEFAULT_GROUP }))
        val resolvedFeedId = feedId.takeIf { it != -1L } ?: feedDao.findIdByUrl(url) ?: return@withContext AddFeedResult.Duplicate

        upsertArticles(resolvedFeedId, parsed.articles, now)
        AddFeedResult.Success
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

    /** 同一 link：只更新内容状态字段，用户状态原样保留。 */
    private suspend fun upsertArticles(feedId: Long, articles: List<RssParser.ParsedArticle>, now: Long) {
        articles.forEach { article ->
            val existingId = articleDao.findIdByLink(feedId, article.link)
            val readingMinutes = article.contentText?.let { estimateReadingMinutes(it) }
            val contentSource = if (article.contentHtml != null) ArticleEntity.CONTENT_SOURCE_FEED else ArticleEntity.CONTENT_SOURCE_NONE
            if (existingId != null) {
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
            } else {
                articleDao.insertAll(
                    listOf(
                        ArticleEntity(
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
                        ),
                    ),
                )
            }
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

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val USER_AGENT = "Mozilla/5.0 (Android) RssRadar/1.0"
    }
}
