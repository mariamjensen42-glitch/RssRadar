package com.cycling.rssradar.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.URL
import com.cycling.rssradar.data.db.AppDatabase
import com.cycling.rssradar.data.db.ArticleEntity
import com.cycling.rssradar.data.db.ArticleWithFeed
import com.cycling.rssradar.data.db.DEFAULT_GROUP
import com.cycling.rssradar.data.db.FeedEntity
import com.cycling.rssradar.data.opml.OpmlParser
import com.cycling.rssradar.data.parser.ContentFetcher
import com.cycling.rssradar.data.parser.FeedProbeResult
import com.cycling.rssradar.data.store.KeepArchived

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
 * 订阅链路仓库（深化后的门面）：观察文章流、用户状态标记、订阅源/分组管理、
 * 添加订阅与 OPML 盲导。刷新子系统的全部规则（双路径、用户状态保护、并发、
 * 图标回填）已下沉 [RefreshEngine]，本类只做转发，不再承担刷新规则。
 */
class FeedRepository(
    private val database: AppDatabase,
    private val engine: RefreshEngine,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** 为 null 时按需抓取不可用（见 [fetchFullContent]）。 */
    private val contentFetcher: ContentFetcher? = null,
) {
    private val feedDao = database.feedDao()
    private val articleDao = database.articleDao()

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

    // —— 刷新：全部转发 [RefreshEngine]，规则（双路径/屏蔽/并发/状态保护）在那边 ——

    /** 单源刷新（订阅源文章列表顶栏动作用），返回是否成功。 */
    suspend fun refreshSingleFeed(feedId: Long): Boolean = engine.refreshSingle(feedId)

    /**
     * 刷新全部订阅源，返回成功刷新的源数。失败源静默跳过（保留已有数据），
     * 供下拉刷新调用。
     */
    suspend fun refreshAllFeeds(): Int = engine.refreshAll()

    /**
     * 自动同步路径（issue #58）：只刷新参与自动同步的源（syncEnabled = 1）。
     * 与手动路径 refreshAllFeeds 的唯一差别是屏蔽源过滤；失败源静默跳过。
     */
    suspend fun refreshAutoSyncFeeds(): Int = engine.refreshAutoSyncFeeds()

    /** 定向刷新一批订阅源（盲导后补文章用），返回成功的源数。失败静默跳过。 */
    suspend fun refreshFeeds(feedIds: List<Long>): Int = engine.refreshFeeds(feedIds)

    /** 更新单源的自动同步开关（issue #58）。 */
    suspend fun setSyncEnabled(feedId: Long, enabled: Boolean) =
        feedDao.updateSyncEnabled(feedId, enabled)

    /** 是否已有订阅源。供 UI 区分「没有源」和「刷新失败」。 */
    suspend fun hasFeeds(): Boolean = feedDao.getAll().isNotEmpty()

    /**
     * 归档清理（issue #57）：按保留档位真删到期文章（starred/bookmarked 豁免，
     * 见 ArticleDao.deleteExpiredArticles）。ALWAYS 不清理。返回删除条数。
     * 只在自动同步完成后调用，手动刷新后不清理（避免手动刷新突兀少文章）。
     */
    suspend fun archiveExpired(keep: KeepArchived): Int {
        val cutoff = keep.cutoffMillis(System.currentTimeMillis()) ?: return 0
        return withContext(ioDispatcher) { articleDao.deleteExpiredArticles(cutoff) }
    }

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
            engine.fetchAndParse(url)
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
            engine.fetchAndParse(url)
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

        engine.persistArticles(resolvedFeedId, parsed.articles, now)
        engine.backfillIcon(resolvedFeedId, parsed.siteUrl)
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
}
