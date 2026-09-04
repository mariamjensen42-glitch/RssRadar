package com.cycling.rssradar.data

import androidx.room.withTransaction
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
import com.cycling.rssradar.data.opml.OpmlEntry
import com.cycling.rssradar.data.opml.OpmlParser
import com.cycling.rssradar.data.opml.OpmlWriter
import com.cycling.rssradar.core.domain.rss.FeedProbeResult
import com.cycling.rssradar.data.parser.RssParser
import com.cycling.rssradar.data.rss.FeedDiscovery
import com.cycling.rssradar.core.domain.rss.HttpFetcher
import com.cycling.rssradar.core.domain.rss.retryOnSlowResponse
import com.cycling.rssradar.data.store.KeepArchived
import com.cycling.rssradar.core.model.MarkAsReadCondition

/** 订阅结果，供 UI 层区分提示文案。 */
sealed interface AddFeedResult {
    data object Success : AddFeedResult
    data object Duplicate : AddFeedResult
    data object InvalidFeed : AddFeedResult
    data object NetworkError : AddFeedResult
}

/**
 * 清空文章结果（issue #8）：deleted = 真删条数，kept = 因收藏/稍后读豁免保留的条数。
 * 两个数字都来自数据库真实统计，UI 直接展示，不做估算。
 */
data class ClearArticlesResult(
    val deleted: Int,
    val kept: Int,
)

/**
 * 自动发现出来的一条 feed（#5）。字段全部来自真实抓取解析：
 * [title] 是 feed 自己的标题，[articleCount] 是解析出的文章数——不猜、不补默认值。
 */
data class DiscoveredFeed(
    val url: String,
    val title: String,
    val articleCount: Int,
)

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
 * 订阅链路仓库：观察文章流、用户状态标记、订阅源/分组管理、添加订阅与 OPML 盲导。
 *
 * 两类规则已经各自有了家，本类不再代持：
 * - 刷新子系统的全部规则（双路径、用户状态保护、并发、图标回填）→ [RefreshEngine]；
 * - **按需抓取**与其**抓取日志**的三条写入规则 → [OnDemandFetch]。
 */
class FeedRepository(
    private val database: AppDatabase,
    private val engine: RefreshEngine,
    /** 站点 HTML 抓取（feed 自动发现 #5 用）。与刷新链路同一条 HTTP 缝，测试可塞 fake。 */
    private val http: HttpFetcher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val feedDao = database.feedDao()
    private val articleDao = database.articleDao()

    /** 归档/清空的统一入口（墓碑 + 真删），生产用真 Room 事务。 */
    private val cleaner = ArticleCleaner(
        articleDao,
        transactionRunner = object : TransactionRunner {
            override suspend fun <T> inTransaction(block: suspend () -> T): T =
                database.withTransaction { block() }
        },
    )

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
     * 删除前写墓碑（[ArticleCleaner]），刷新不再把删掉的文章插回来。
     * 只在自动同步完成后调用，手动刷新后不清理（避免手动刷新突兀少文章）。
     */
    suspend fun archiveExpired(keep: KeepArchived): Int {
        val now = System.currentTimeMillis()
        val cutoff = keep.cutoffMillis(now)
        return withContext(ioDispatcher) { cleaner.archiveExpired(cutoff, now) }
    }

    fun observeFeedCount(): Flow<Int> = articleDao.observeCount()
    fun observeUnreadCount(): Flow<Int> = articleDao.observeUnreadCount()

    fun observeFeeds(): Flow<List<FeedEntity>> = feedDao.observeAll()
    fun observeFeedUnreadCounts(): Flow<Map<Long, Int>> =
        articleDao.observeUnreadCountByFeed().map { rows -> rows.associate { it.feedId to it.cnt } }

    /**
     * Feed 自动发现（#5）：用户贴一个网址（可能只是站点首页），探测出可订阅的 feed。
     *
     * 三步降级，每步都靠"真能解析出文章"来判定，不看 Content-Type 之类不可靠的信号：
     * 1. 该地址本身就是 feed → 直接返回一条；
     * 2. 抓 HTML，读 `<link rel=alternate>` 声明的候选 → 逐个校验；
     * 3. 站点没声明 → 试常见路径（/feed、/rss.xml…）→ 逐个校验。
     *
     * 返回按"文章数多的在前"排序（主 feed 通常更全）。全部失败返回空表——
     * 不猜、不返回没验证过的地址。
     */
    suspend fun discoverFeeds(rawUrl: String): List<DiscoveredFeed> = withContext(ioDispatcher) {
        val url = normalizeUrl(rawUrl) ?: return@withContext emptyList()
        // 1) 本身就是 feed
        runCatching { engine.fetchAndParse(url) }.getOrNull()?.let { parsed ->
            if (parsed.articles.isNotEmpty()) {
                return@withContext listOf(
                    DiscoveredFeed(url = url, title = parsed.title, articleCount = parsed.articles.size),
                )
            }
        }
        // 2) 站点 HTML 里声明的候选
        val html = runCatching { http.fetch(url).use { it.readBytes().toString(Charsets.UTF_8) } }
            .getOrNull() ?: return@withContext emptyList()
        val declared = FeedDiscovery.candidateLinks(url, html)
        val candidates = declared.ifEmpty { FeedDiscovery.guessedLinks(url) }
        val verified = ArrayList<DiscoveredFeed>()
        for (candidate in candidates.distinct().take(FeedDiscovery.MAX_CANDIDATES)) {
            verifyFeed(candidate)?.let { verified += it }
        }
        verified.sortByDescending { it.articleCount }
        verified
    }

    /** 校验一个候选地址：抓下来能解析出文章才算数。 */
    private suspend fun verifyFeed(url: String): DiscoveredFeed? {
        val parsed = runCatching { engine.fetchAndParse(url) }.getOrNull() ?: return null
        if (parsed.articles.isEmpty()) return null
        return DiscoveredFeed(url = url, title = parsed.title, articleCount = parsed.articles.size)
    }

    /**
     * 仅抓取+解析，用于"添加订阅"页的实时预览。不写入数据库。
     * 返回 [FeedProbeResult] 供 ViewModel 决定 UI 状态。
     *
     * 重试在 [fetchParsed] / [retryOnSlowResponse] 里，这里不再叠一层——
     * 曾经两层各重试一次，实际会发 4 次请求。
     */
    suspend fun probeFeed(rawUrl: String): FeedProbeResult = withContext(ioDispatcher) {
        val url = normalizeUrl(rawUrl)
            ?: return@withContext FeedProbeResult.InvalidUrl
        runCatching { fetchParsed(url) }.fold(
            onSuccess = { FeedProbeResult.Valid(it.articles.size) },
            onFailure = FeedProbeResult::from,
        )
    }

    /**
     * 抓取+解析，等响应超时自动重试一次——订阅链路（预览 / 落库）共用，
     * 免得用户在预览时看着好好的，点「订阅」那一下反而撞上冷路由失败。
     */
    private suspend fun fetchParsed(url: String): RssParser.ParsedFeed =
        retryOnSlowResponse { engine.fetchAndParse(url) }

    suspend fun markRead(id: Long) = articleDao.markRead(id)

    /**
     * 记录一次打开（ADR-0013）：推荐画像的唯一采集信号，每次打开详情页都更新。
     * 只写 lastOpenedAt 一列，不碰已读/收藏/稍后读等用户状态。
     */
    suspend fun markOpened(id: Long) = articleDao.markOpened(id, System.currentTimeMillis())
    suspend fun setStarred(id: Long, starred: Boolean) = articleDao.setStarred(id, starred)
    suspend fun setBookmarked(id: Long, bookmarked: Boolean) = articleDao.setBookmarked(id, bookmarked)
    suspend fun markAllRead() = articleDao.markAllRead()

    /**
     * 按条件批量标记已读（#10）：[condition] 为 ALL 时清空全部未读，否则只标记
     * 早于 cutoff 的未读文章。返回真实影响行数——数字必须来自数据库，不猜。
     */
    suspend fun markReadByCondition(condition: MarkAsReadCondition): Int {
        val cutoff = condition.cutoffMillis()
        return if (cutoff == null) articleDao.markAllUnreadRead() else articleDao.markReadOlderThan(cutoff)
    }

    /** 滚动自动标记已读（#11）：给定的 id 里仍未读的置为已读，返回实际标记数。 */
    suspend fun markReadBatch(ids: List<Long>): Int =
        if (ids.isEmpty()) 0 else articleDao.markReadBatch(ids)

    /**
     * 本轮同步新进库的未读文章（#31 通知用；Feed 级开关在 SQL 层过滤）。
     * [limit] 只是通知里要展示的条数。
     */
    suspend fun newUnreadSince(since: Long, limit: Int): List<ArticleWithFeed> =
        articleDao.loadNewUnreadSince(since, limit)

    /** Feed 级通知开关（#31）。 */
    suspend fun setNotificationsEnabled(feedId: Long, enabled: Boolean) =
        feedDao.updateNotificationsEnabled(feedId, enabled)

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

    /** 批量移动订阅源到分组（issue #7）：一次 UPDATE ... WHERE id IN (...)，不逐条写。 */
    suspend fun moveFeedsToGroup(feedIds: List<Long>, groupName: String) {
        if (feedIds.isEmpty()) return
        feedDao.updateGroupForFeeds(feedIds, groupName)
    }

    /**
     * 清空单个订阅源的文章（issue #8）：只删文章，源保留。
     * 收藏/稍后读豁免（规则同归档清理）——用户主动标记的内容不因批量清空丢失。
     * 删除前写墓碑（[ArticleCleaner]），清空后刷新不再复活。
     */
    suspend fun clearFeedArticles(feedId: Long): ClearArticlesResult = withContext(ioDispatcher) {
        val kept = articleDao.countProtectedByFeed(feedId)
        ClearArticlesResult(deleted = cleaner.clearFeed(feedId, System.currentTimeMillis()), kept = kept)
    }

    /** 清空一个分组下所有订阅源的文章（issue #8），豁免规则同上，墓碑同归档。 */
    suspend fun clearGroupArticles(groupName: String): ClearArticlesResult = withContext(ioDispatcher) {
        val kept = articleDao.countProtectedByGroup(groupName)
        ClearArticlesResult(deleted = cleaner.clearGroup(groupName, System.currentTimeMillis()), kept = kept)
    }

    /** Feed 级预设：详情页是否自动抓取该源的原网页正文（issue #9）。 */
    suspend fun setFullContentEnabled(feedId: Long, enabled: Boolean) =
        feedDao.updateFullContentEnabled(feedId, enabled)

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

    suspend fun addFeed(
        rawUrl: String,
        groupName: String = DEFAULT_GROUP,
        sourceType: Int = FeedEntity.SOURCE_TYPE_RSS,
    ): AddFeedResult = withContext(ioDispatcher) {
        val url = normalizeUrl(rawUrl) ?: return@withContext AddFeedResult.InvalidFeed

        if (feedDao.findIdByUrl(url) != null) return@withContext AddFeedResult.Duplicate

        val parsed = try {
            fetchParsed(url)
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

    /**
     * OPML 导出（#4）：把全部订阅源按分组序列化成 OPML 文本。
     * 分组即 OPML 文件夹（`技术/后端` 会被 [OpmlWriter] 还原成嵌套 outline）。
     * 库里没有站点主页字段（FeedEntity 无 siteUrl），HTML 链接属性留空——不捏造。
     * 这是导入的逆操作：用户的订阅清单不被本应用绑架。
     */
    suspend fun exportOpml(): String = withContext(ioDispatcher) {
        val entries = feedDao.getAll().map { feed ->
            OpmlEntry(
                group = feed.groupName,
                title = feed.title,
                xmlUrl = feed.url,
            )
        }
        OpmlWriter.write(entries)
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
