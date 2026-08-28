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
) {
    private val feedDao = database.feedDao()
    private val articleDao = database.articleDao()

    fun observeArticles(): Flow<List<ArticleWithFeed>> = articleDao.observeAllWithFeed()
    fun observeAllArticles(): Flow<List<ArticleWithFeed>> = articleDao.observeAllWithFeed()
    fun observeUnreadArticles(): Flow<List<ArticleWithFeed>> = articleDao.observeUnreadWithFeed()
    fun observeStarredArticles(): Flow<List<ArticleWithFeed>> = articleDao.observeStarredWithFeed()
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

        articleDao.insertAll(
            parsed.articles.map { article ->
                ArticleEntity(
                    feedId = resolvedFeedId,
                    link = article.link,
                    title = article.title,
                    summary = article.summary,
                    publishedAt = article.publishedAt,
                    fetchedAt = now,
                )
            },
        )
        AddFeedResult.Success
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
