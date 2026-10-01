package com.cycling.rssradar.core.data

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.cycling.rssradar.core.data.db.AppDatabase
import com.cycling.rssradar.core.data.db.entity.ArticleEntity
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.core.data.db.DEFAULT_GROUP
import com.cycling.rssradar.core.data.db.entity.FeedEntity
import com.cycling.rssradar.core.data.filter.FilterRuleApplier
import com.cycling.rssradar.core.data.store.model.KeepArchived
import com.cycling.rssradar.core.domain.filter.FilterRuleEngine
import com.cycling.rssradar.core.domain.search.SearchFilters
import com.cycling.rssradar.core.domain.search.SearchQueryBuilder
import com.cycling.rssradar.core.model.MarkAsReadCondition
import com.cycling.rssradar.core.model.library.LibrarySort

/**
 * 文章流仓库：观察文章流、用户状态标记、订阅源/分组管理。
 *
 * 三类规则已经各自有了家，本类不再代持：
 * - 刷新子系统的全部规则（双路径、用户状态保护、并发、图标回填）→ [RefreshEngine]；
 * - **按需抓取**与其**抓取日志**的三条写入规则 → [OnDemandFetch]；
 * - **订阅链路**（发现/预览/落库/OPML）→ [SubscriptionFlow]。
 */
class FeedRepository(
    private val database: AppDatabase,
    private val engine: RefreshEngine,
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

    /** 过滤规则作用于存量文章：与 cleaner 共用同一套归档语义（含收藏/稍后读豁免）。 */
    private val ruleApplier = FilterRuleApplier(articleDao, cleaner)

    fun search(query: String): Flow<List<ArticleWithFeed>> = articleDao.search("%$query%")

    data class SearchPage(val hits: Int, val articles: List<ArticleWithFeed>)

    /**
     * 搜索一页 + 总命中数。查询串交给 [SearchQueryBuilder] 决定走 FTS 还是 LIKE 回退——
     * 双字组索引无法表达单个汉字，那种输入只能退回 LIKE（见 SearchQueryBuilder 的取舍）。
     */
    suspend fun searchPage(
        raw: String,
        filters: SearchFilters,
        limit: Int,
        offset: Int,
    ): SearchPage {
        val query = SearchQueryBuilder.build(raw) ?: return SearchPage(0, emptyList())
        return when (query) {
            is SearchQueryBuilder.Query.Fts -> SearchPage(
                hits = articleDao.countSearchFts(
                    match = query.match,
                    feedId = filters.feedId,
                    fromMillis = filters.fromMillis,
                    toMillis = filters.toMillis,
                    unreadOnly = filters.unreadOnly,
                    starredOnly = filters.starredOnly,
                    bookmarkedOnly = filters.bookmarkedOnly,
                ),
                articles = articleDao.searchFts(
                    match = query.match,
                    feedId = filters.feedId,
                    fromMillis = filters.fromMillis,
                    toMillis = filters.toMillis,
                    unreadOnly = filters.unreadOnly,
                    starredOnly = filters.starredOnly,
                    bookmarkedOnly = filters.bookmarkedOnly,
                    limit = limit,
                    offset = offset,
                ),
            )

            is SearchQueryBuilder.Query.Like -> {
                val pattern = "%${query.raw}%"
                SearchPage(
                    hits = articleDao.countSearchLike(
                        pattern = pattern,
                        feedId = filters.feedId,
                        fromMillis = filters.fromMillis,
                        toMillis = filters.toMillis,
                        unreadOnly = filters.unreadOnly,
                        starredOnly = filters.starredOnly,
                        bookmarkedOnly = filters.bookmarkedOnly,
                    ),
                    articles = articleDao.searchLike(
                        pattern = pattern,
                        feedId = filters.feedId,
                        fromMillis = filters.fromMillis,
                        toMillis = filters.toMillis,
                        unreadOnly = filters.unreadOnly,
                        starredOnly = filters.starredOnly,
                        bookmarkedOnly = filters.bookmarkedOnly,
                        limit = limit,
                        offset = offset,
                    ),
                )
            }
        }
    }

    suspend fun loadLibraryPage(
        starred: Boolean,
        sort: LibrarySort,
        feedId: Long?,
        fromMillis: Long?,
        toMillis: Long?,
        limit: Int,
        offset: Int,
    ): List<ArticleWithFeed> = when (sort) {
        LibrarySort.STARRED_AT ->
            articleDao.loadLibraryByAddedTime(starred, feedId, fromMillis, toMillis, limit, offset)

        LibrarySort.PUBLISHED_AT ->
            articleDao.loadLibraryByPublishedAt(starred, feedId, fromMillis, toMillis, limit, offset)

        LibrarySort.FEED ->
            articleDao.loadLibraryByFeed(starred, feedId, fromMillis, toMillis, limit, offset)
    }

    suspend fun countLibrary(
        starred: Boolean,
        feedId: Long?,
        fromMillis: Long?,
        toMillis: Long?,
    ): Int = articleDao.countLibrary(starred, feedId, fromMillis, toMillis)

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

    // —— 组合筛选入口（issue #74 分组 + issue #75 分区）：分组（null=全部，含默认组空串语义）
    // × 内容分区（null=全部）叠加。两个过滤都为空时走上面的无谓词原查询，不为空才进
    // 组合查询——「全部」不加过滤开销。isDefaultGroup 在这里统一判定
    // （group == DEFAULT_GROUP），DAO 不做字符串比较，谓词语义见 ArticleDao 注释。

    /** All tab + 组合筛选：一次取一页。 */
    suspend fun loadArticlesPageFiltered(group: String?, contentType: Int?, limit: Int, offset: Int): List<ArticleWithFeed> =
        if (group == null && contentType == null) articleDao.loadAllWithFeedPaged(limit, offset)
        else articleDao.loadAllWithFeedPagedFiltered(group, group == DEFAULT_GROUP, contentType, limit, offset)

    /** 未读 tab + 组合筛选：一次取一页。 */
    suspend fun loadUnreadPageFiltered(group: String?, contentType: Int?, limit: Int, offset: Int): List<ArticleWithFeed> =
        if (group == null && contentType == null) articleDao.loadUnreadWithFeedPaged(limit, offset)
        else articleDao.loadUnreadWithFeedPagedFiltered(group, group == DEFAULT_GROUP, contentType, limit, offset)

    /** 收藏 tab + 组合筛选：一次取一页。 */
    suspend fun loadStarredPageFiltered(group: String?, contentType: Int?, limit: Int, offset: Int): List<ArticleWithFeed> =
        if (group == null && contentType == null) articleDao.loadStarredWithFeedPaged(limit, offset)
        else articleDao.loadStarredWithFeedPagedFiltered(group, group == DEFAULT_GROUP, contentType, limit, offset)

    /** 稍后读 tab + 组合筛选：一次取一页。 */
    suspend fun loadBookmarkedPageFiltered(group: String?, contentType: Int?, limit: Int, offset: Int): List<ArticleWithFeed> =
        if (group == null && contentType == null) articleDao.loadBookmarkedWithFeedPaged(limit, offset)
        else articleDao.loadBookmarkedWithFeedPagedFiltered(group, group == DEFAULT_GROUP, contentType, limit, offset)

    // —— 总数 COUNT（滚动位置指示条）：与上面 loadXxxPageFiltered 同谓词同分支，分母保证一致 ——

    /** All tab + 组合筛选：文章总数。 */
    suspend fun countArticlesFiltered(group: String?, contentType: Int?): Int =
        if (group == null && contentType == null) articleDao.countAllWithFeed()
        else articleDao.countAllWithFeedFiltered(group, group == DEFAULT_GROUP, contentType)

    /** 未读 tab + 组合筛选：文章总数。 */
    suspend fun countUnreadFiltered(group: String?, contentType: Int?): Int =
        articleDao.countUnreadWithFeedFiltered(group, group == DEFAULT_GROUP, contentType)

    /** 收藏 tab + 组合筛选：文章总数。 */
    suspend fun countStarredFiltered(group: String?, contentType: Int?): Int =
        articleDao.countStarredWithFeedFiltered(group, group == DEFAULT_GROUP, contentType)

    /** 稍后读 tab + 组合筛选：文章总数。 */
    suspend fun countBookmarkedFiltered(group: String?, contentType: Int?): Int =
        articleDao.countBookmarkedWithFeedFiltered(group, group == DEFAULT_GROUP, contentType)

    /** 空分区空态判定（issue #75）：是否有任何该内容类型的订阅源。 */
    suspend fun hasFeedsOfType(contentType: Int): Boolean =
        feedDao.countFeedsByContentType(contentType) > 0

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
     * 供下拉刷新调用。[onProgress] 每 完成一个源 回调 (done, total)，供 UI 显示进度。
     */
    suspend fun refreshAllFeeds(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int =
        engine.refreshAll(onProgress)

    /**
     * 自动同步路径（issue #58）：只刷新参与自动同步的源（syncEnabled = 1）。
     * 与手动路径 refreshAllFeeds 的唯一差别是屏蔽源过滤；失败源静默跳过。
     */
    suspend fun refreshAutoSyncFeeds(): Int = engine.refreshAutoSyncFeeds()

    /** 定向刷新一批订阅源（盲导后补文章用），返回成功的源数。失败静默跳过。 */
    suspend fun refreshFeeds(feedIds: List<Long>): Int = engine.refreshFeeds(feedIds)

    /**
     * 失效源复探（#82）：只刷新有失败记录的源。成功 → 计数清零恢复；
     * 再失败 → 计数继续累加。每日 FeedHealthWorker 与手动复探都走这里。
     */
    suspend fun refreshUnhealthyFeeds(): Int {
        val ids = feedDao.getFeedIdsWithFailures()
        if (ids.isEmpty()) return 0
        return engine.refreshFeeds(ids)
    }

    /** 更新单源的自动同步开关（issue #58）。 */
    suspend fun setSyncEnabled(feedId: Long, enabled: Boolean) =
        feedDao.updateSyncEnabled(feedId, enabled)

    /** 更新单源的内容类型（ADR-0014）：只影响列表浏览形态，不影响数据。 */
    suspend fun setContentType(feedId: Long, contentType: Int) =
        feedDao.updateContentType(feedId, contentType)

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

    /** 每个订阅源最近一篇文章的时间（订阅列表「按最近更新」排序用）。 */
    fun observeFeedLatestTimes(): Flow<Map<Long, Long>> =
        articleDao.observeLatestTimeByFeed().map { rows -> rows.associate { it.feedId to it.latest } }

    suspend fun markRead(id: Long) = articleDao.markRead(id)

    /**
     * 记录一次打开（ADR-0013）：推荐画像的唯一采集信号，每次打开详情页都更新。
     * 只写 lastOpenedAt 一列，不碰已读/收藏/稍后读等用户状态。
     */
    suspend fun markOpened(id: Long) = articleDao.markOpened(id, System.currentTimeMillis())
    suspend fun setStarred(id: Long, starred: Boolean) =
        articleDao.setStarred(id, starred, System.currentTimeMillis())

    suspend fun setBookmarked(id: Long, bookmarked: Boolean) =
        articleDao.setBookmarked(id, bookmarked, System.currentTimeMillis())

    suspend fun unstarBatch(ids: List<Long>): Int = articleDao.unstarBatch(ids)

    suspend fun unbookmarkBatch(ids: List<Long>): Int = articleDao.unbookmarkBatch(ids)
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
     * 收藏/稍后读豁免与 kept 统计口径都在 [ArticleCleaner] 一处定义。
     */
    suspend fun clearFeedArticles(feedId: Long): ClearArticlesResult = withContext(ioDispatcher) {
        cleaner.clearFeed(feedId, System.currentTimeMillis())
    }

    /** 清空一个分组下所有订阅源的文章（issue #8），规则同上。 */
    suspend fun clearGroupArticles(groupName: String): ClearArticlesResult = withContext(ioDispatcher) {
        cleaner.clearGroup(groupName, System.currentTimeMillis())
    }

    /** Feed 级预设：详情页是否自动抓取该源的原网页正文（issue #9）。 */
    suspend fun setFullContentEnabled(feedId: Long, enabled: Boolean) =
        feedDao.updateFullContentEnabled(feedId, enabled)

    /** 重命名订阅源标题。 */
    suspend fun renameFeed(feedId: Long, title: String) = feedDao.updateTitle(feedId, title)

    /** 删除订阅源（其文章级联删除）。 */
    suspend fun deleteFeed(feedId: Long) = feedDao.deleteFeed(feedId)

    /** 批量删除订阅源（多选模式）：文章级联删除，一条 SQL 完成。 */
    suspend fun deleteFeeds(feedIds: List<Long>) {
        if (feedIds.isEmpty()) return
        feedDao.deleteFeeds(feedIds)
    }

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

    /** 同源音频条目（含播放地址），新→旧：播客的连续播放队列按源组织。 */
    suspend fun audioQueueOfFeed(feedId: Long): List<ArticleEntity> =
        articleDao.audioOfFeed(feedId, ArticleEntity.MEDIA_KIND_AUDIO)

    /**
     * 把过滤规则作用于**存量文章**（新文章在刷新入库时判定）。
     * 规则保存/启用后调用一次，用户配的「隐藏」才能立刻生效而不是等下一篇。
     */
    suspend fun applyFilterRules(engine: FilterRuleEngine): FilterRuleApplier.Result =
        ruleApplier.apply(engine)

    /** 只算不改：规则编辑时的「预计命中」。 */
    suspend fun previewFilterRules(engine: FilterRuleEngine): FilterRuleApplier.Result =
        ruleApplier.preview(engine)

    /**
     * 把 AI 提取出的正文回填到文章（AI 智能功能模块 · 自动提取全文）。
     *
     * 刻意复用抓取端的同一张口 [ArticleDao.updateFetchedContent]：
     * 内容来源、阅读时长、不完整标记的处理与 ADR-0012 的按需抓取完全一致，
     * 阅读页不需要为"这段正文是 AI 提取的"单独开一条渲染分支——
     * 提取成功就是一段完整正文，与抓来的一样显示。
     *
     * @param html 模型还原出的正文 HTML
     * @param plainText 去掉标签后的纯文本，用于检索与阅读时长
     */
    suspend fun applyExtractedContent(articleId: Long, html: String, plainText: String) {
        articleDao.updateFetchedContent(
            id = articleId,
            content = html,
            contentText = plainText,
            contentSource = ArticleEntity.CONTENT_SOURCE_WEB,
            readingMinutes = estimateReadingMinutes(plainText),
            coverUrl = null,
            contentIncomplete = false,
        )
    }
}
