package com.cycling.rssradar.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cycling.rssradar.core.data.db.entity.ArchivedArticleTombstoneEntity
import com.cycling.rssradar.core.data.db.entity.ArticleEntity
import com.cycling.rssradar.core.data.db.projection.ArticleFeedLink
import com.cycling.rssradar.core.data.db.projection.ArticleIdContentType
import com.cycling.rssradar.core.data.db.projection.ArticleIdGroup
import com.cycling.rssradar.core.data.db.projection.ArticleIdLink
import com.cycling.rssradar.core.data.db.projection.ArticleSearchSourceRow
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.core.data.db.projection.EngagementRow
import com.cycling.rssradar.core.data.db.projection.FeedLatestTime
import com.cycling.rssradar.core.data.db.projection.FeedOpenStat
import com.cycling.rssradar.core.data.db.projection.FeedUnreadCount
import com.cycling.rssradar.core.data.db.projection.ReadingWindowStat
import com.cycling.rssradar.core.data.db.projection.RuleScanRow
import kotlinx.coroutines.flow.Flow

/**
 * 列表流专用列清单：剔除 content / contentText 两列全文（每篇可达几十上百 KB）。
 * 列表卡片只用到 title/summary/coverUrl 等轻字段，全量物化几百篇会把 Java 堆吃满
 * （OOM 诊断：observeUnreadWithFeed 的 CursorWindow.nativeGetString 分配失败）。
 * 详情页仍走 getWithFeed 的 SELECT *，正文完整。
 *
 * 缺了 content/contentText 会让 Room 报 QUERY_MISMATCH（缺列），这是**有意为之**：
 * 列表查询统一带 `@Suppress("QUERY_MISMATCH")` 声明，
 * 两列由 ArticleEntity 的 Kotlin 默认值 null 兜底。
 * 反过来——新增列必须同步加进这里，否则列表页拿到的永远是默认值（静默出错）。
 */
private const val ARTICLE_LIST_COLUMNS =
    "articles.id, articles.feedId, articles.link, articles.title, articles.summary, " +
        "articles.publishedAt, articles.fetchedAt, articles.author, articles.contentSource, " +
        "articles.isRead, articles.isStarred, articles.isBookmarked, articles.readingMinutes, " +
        "articles.coverUrl, articles.aiSummary, articles.contentIncomplete, articles.lastOpenedAt, " +
        "articles.mediaKind, articles.starredAt, articles.bookmarkedAt, articles.mediaUrl"

/**
 * 分组筛选（issue #74，issue #75 升级为可空版）的 WHERE 片段：选中分组时按 feeds.groupName 过滤。
 * 关键语义：**默认组要同时命中空串**——历史/导入数据的 feeds.groupName 可能是 ''，
 * UI 上显示为 [DEFAULT_GROUP]，只写 `groupName = :group` 会漏掉这些行
 * （与内存侧 `groupName.ifBlank { DEFAULT_GROUP }` 兜底是同一语义，见 SubscriptionsViewModel）。
 *
 * 可空短路（issue #75）：分组筛选为「全部」时 :group 传 null，谓词恒真，不加过滤开销——
 * 分组 × 分区两个维度合成同一条查询（Filtered 变体），DAO 查询总数不随维度组合膨胀。
 *
 * 绑定参数：group = 所选分组名或 null；isDefaultGroup = (group == DEFAULT_GROUP)，
 * 由仓库层判定后传入，DAO 不依赖常量字符串比较。
 *
 * 性能：JOIN 仍是逐文章行按 feeds 主键探测、在探测行上追加这个过滤条件，
 * ORDER BY 不变（publishedAt DESC, fetchedAt DESC），照走 index_articles_publishedAt_fetchedAt，
 * 不会退化成全表外排序——排序表达式零新增。
 */
private const val GROUP_FILTER_PREDICATE_NULLABLE =
    "(:group IS NULL OR (feeds.groupName = :group OR (:isDefaultGroup = 1 AND feeds.groupName = '')))"

/**
 * 内容分区（issue #75）的 WHERE 片段：选中分区时按 feeds.contentType 过滤。
 * 「全部」分区 = :contentType 传 null，谓词恒真，不加过滤开销（与分组谓词同一思路）。
 * 绑定参数由仓库层从 UI 枚举换算（ContentTypeFilter.dbValue），DAO 不依赖 UI 类型。
 */
private const val CONTENT_TYPE_FILTER_PREDICATE =
    "(:contentType IS NULL OR feeds.contentType = :contentType)"

/**
 * 搜索的二次筛选谓词（源 / 时间范围 / 未读 / 收藏 / 稍后读）。
 * 时间基准沿用 `COALESCE(publishedAt, fetchedAt)`，与归档清理、批量标已读一致——
 * 否则无发布日期的文章会在筛选里凭空消失。
 */
private const val SEARCH_FILTER_PREDICATE =
    "(:feedId IS NULL OR articles.feedId = :feedId) " +
        "AND (:fromMillis IS NULL OR COALESCE(articles.publishedAt, articles.fetchedAt) >= :fromMillis) " +
        "AND (:toMillis IS NULL OR COALESCE(articles.publishedAt, articles.fetchedAt) <= :toMillis) " +
        "AND (:unreadOnly = 0 OR articles.isRead = 0) " +
        "AND (:starredOnly = 0 OR articles.isStarred = 1) " +
        "AND (:bookmarkedOnly = 0 OR articles.isBookmarked = 1)"

/**
 * 收藏页（收藏 / 稍后读同一套排序与筛选）的通用条件：:starred 选集合，时间范围与来源同搜索。
 */
private const val LIBRARY_CONDITION =
    "(CASE WHEN :starred = 1 THEN articles.isStarred ELSE articles.isBookmarked END) = 1 " +
        "AND (:feedId IS NULL OR articles.feedId = :feedId) " +
        "AND (:fromMillis IS NULL OR COALESCE(articles.publishedAt, articles.fetchedAt) >= :fromMillis) " +
        "AND (:toMillis IS NULL OR COALESCE(articles.publishedAt, articles.fetchedAt) <= :toMillis)"

@Dao
interface ArticleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(articles: List<ArticleEntity>): List<Long>

    // —— 信息流列表：轻量投影 + LIMIT/OFFSET 分页，四个 tab 统一 ——
    // 规模现实：订阅源 1000+、文章数万条。任何"全表 observe 全量物化"的列表流
    // 都会在每次 DB 写失效时重查数万行，内存与主线程都扛不住（OOM 诊断结论）。
    //
    // 排序一律 `publishedAt DESC, fetchedAt DESC`，走 index_articles_publishedAt_fetchedAt（#65）。
    // 别写成 `publishedAt IS NULL, publishedAt DESC, ...`：那个 IS NULL 是表达式，
    // 会让整条 ORDER BY 退化为全表外排序，索引直接失效。
    // 语义等价：SQLite 里 NULL 最小，DESC 时 NULL 天然沉底（即「无日期沉底」），
    // NULL 组内再由 fetchedAt DESC 兜底，与原来分组排序的结果逐行一致。

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        ORDER BY articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadAllWithFeedPaged(limit: Int, offset: Int): List<ArticleWithFeed>

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.isRead = 0
        ORDER BY articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadUnreadWithFeedPaged(limit: Int, offset: Int): List<ArticleWithFeed>

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.isStarred = 1
        ORDER BY articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadStarredWithFeedPaged(limit: Int, offset: Int): List<ArticleWithFeed>

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.isBookmarked = 1
        ORDER BY articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadBookmarkedWithFeedPaged(limit: Int, offset: Int): List<ArticleWithFeed>

    // —— 总数 COUNT（滚动位置指示条）：谓词与上面的分页查询严格同构，保证分母一致。
    // 翻页只追加不重算总数，指示条 thumb 不随 totalItemsCount 增长而跳变。 ——

    @Query("SELECT COUNT(*) FROM articles JOIN feeds ON articles.feedId = feeds.id")
    suspend fun countAllWithFeed(): Int

    @Query(
        """
        SELECT COUNT(*) FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE $GROUP_FILTER_PREDICATE_NULLABLE AND $CONTENT_TYPE_FILTER_PREDICATE
        """,
    )
    suspend fun countAllWithFeedFiltered(
        group: String?,
        isDefaultGroup: Boolean,
        contentType: Int?,
    ): Int

    @Query(
        """
        SELECT COUNT(*) FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.isRead = 0 AND $GROUP_FILTER_PREDICATE_NULLABLE AND $CONTENT_TYPE_FILTER_PREDICATE
        """,
    )
    suspend fun countUnreadWithFeedFiltered(
        group: String?,
        isDefaultGroup: Boolean,
        contentType: Int?,
    ): Int

    @Query(
        """
        SELECT COUNT(*) FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.isStarred = 1 AND $GROUP_FILTER_PREDICATE_NULLABLE AND $CONTENT_TYPE_FILTER_PREDICATE
        """,
    )
    suspend fun countStarredWithFeedFiltered(
        group: String?,
        isDefaultGroup: Boolean,
        contentType: Int?,
    ): Int

    @Query(
        """
        SELECT COUNT(*) FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.isBookmarked = 1 AND $GROUP_FILTER_PREDICATE_NULLABLE AND $CONTENT_TYPE_FILTER_PREDICATE
        """,
    )
    suspend fun countBookmarkedWithFeedFiltered(
        group: String?,
        isDefaultGroup: Boolean,
        contentType: Int?,
    ): Int

    // —— 组合筛选变体（issue #74 分组 + issue #75 分区）：两个过滤维度收进同一条查询，
    // 四个常规 tab 各一条，DAO 查询总数不随维度组合膨胀 ——
    // 筛选语义（默认组同时命中空串、分区「全部」短路）与排序约定统一收口在
    // GROUP_FILTER_PREDICATE_NULLABLE / CONTENT_TYPE_FILTER_PREDICATE 注释里；
    // isDefaultGroup 由仓库层按 group == DEFAULT_GROUP 传入，DAO 不做字符串比较。

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE $GROUP_FILTER_PREDICATE_NULLABLE AND $CONTENT_TYPE_FILTER_PREDICATE
        ORDER BY articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadAllWithFeedPagedFiltered(
        group: String?,
        isDefaultGroup: Boolean,
        contentType: Int?,
        limit: Int,
        offset: Int,
    ): List<ArticleWithFeed>

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.isRead = 0 AND $GROUP_FILTER_PREDICATE_NULLABLE AND $CONTENT_TYPE_FILTER_PREDICATE
        ORDER BY articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadUnreadWithFeedPagedFiltered(
        group: String?,
        isDefaultGroup: Boolean,
        contentType: Int?,
        limit: Int,
        offset: Int,
    ): List<ArticleWithFeed>

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.isStarred = 1 AND $GROUP_FILTER_PREDICATE_NULLABLE AND $CONTENT_TYPE_FILTER_PREDICATE
        ORDER BY articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadStarredWithFeedPagedFiltered(
        group: String?,
        isDefaultGroup: Boolean,
        contentType: Int?,
        limit: Int,
        offset: Int,
    ): List<ArticleWithFeed>

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.isBookmarked = 1 AND $GROUP_FILTER_PREDICATE_NULLABLE AND $CONTENT_TYPE_FILTER_PREDICATE
        ORDER BY articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadBookmarkedWithFeedPagedFiltered(
        group: String?,
        isDefaultGroup: Boolean,
        contentType: Int?,
        limit: Int,
        offset: Int,
    ): List<ArticleWithFeed>

    /**
     * 文章 id → 所属分组名（issue #74）：推荐流的推荐序在内存里，按分组过滤时
     * 只需要每个 id 的 groupName，两列轻量查询，不物化文章行。
     */
    @Query(
        """
        SELECT articles.id AS id, feeds.groupName AS groupName
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.id IN (:ids)
        """,
    )
    suspend fun groupOfArticles(ids: List<Long>): List<ArticleIdGroup>

    /**
     * 文章 id → 所属源内容类型（issue #75）：推荐流的推荐序在内存里，按分区过滤时
     * 只需要每个 id 的 contentType，两列轻量查询，不物化文章行（模式同 [groupOfArticles]）。
     */
    @Query(
        """
        SELECT articles.id AS id, feeds.contentType AS contentType
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.id IN (:ids)
        """,
    )
    suspend fun contentTypeOfArticles(ids: List<Long>): List<ArticleIdContentType>

    /** 订阅源文章列表（CONTEXT.md「Feed article list」）：单源全部文章，新→旧，分页。 */
    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.feedId = :feedId
        ORDER BY articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadFeedWithFeedPaged(feedId: Long, limit: Int, offset: Int): List<ArticleWithFeed>

    /**
     * 推荐候选池（ADR-0013）：未读 且 发布时间在窗口内。
     * 已读文章永不进推荐。窗口基准与归档一致（COALESCE(publishedAt, fetchedAt)）。
     * [limit] 是候选池上限（不是分页），打分在内存里做。
     */
    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.isRead = 0
            AND COALESCE(articles.publishedAt, articles.fetchedAt) >= :since
        ORDER BY COALESCE(articles.publishedAt, articles.fetchedAt) DESC
        LIMIT :limit
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadRecommendationCandidates(since: Long, limit: Int): List<ArticleWithFeed>

    /**
     * 相关阅读候选池（AiFeature.RELATED）：近期文章（已读未读都算——读过的相关文章
     * 恰恰是最该推荐的），排除焦点文章本身。窗口基准与推荐池一致（COALESCE）。
     */
    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.id != :excludeId
            AND COALESCE(articles.publishedAt, articles.fetchedAt) >= :since
        ORDER BY COALESCE(articles.publishedAt, articles.fetchedAt) DESC
        LIMIT :limit
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadRelatedCandidates(excludeId: Long, since: Long, limit: Int): List<ArticleWithFeed>

    /**
     * 画像样本（ADR-0013）：真实表达过兴趣的文章——打开过、收藏或稍后读。
     * 按最近一次打开时间倒序取前 [limit] 条，越近的行为在画像里权重越高。
     */
    @Query(
        """
        SELECT articles.id, articles.feedId, articles.title, articles.summary,
               articles.lastOpenedAt, articles.isStarred, articles.isBookmarked
        FROM articles
        WHERE articles.lastOpenedAt IS NOT NULL
            OR articles.isStarred = 1 OR articles.isBookmarked = 1
        ORDER BY COALESCE(articles.lastOpenedAt, articles.fetchedAt) DESC
        LIMIT :limit
        """,
    )
    suspend fun loadEngagementSamples(limit: Int): List<EngagementRow>

    /** 窗口内每个订阅源的文章总数：源亲和度的分母（打开率，ADR-0013）。 */
    @Query(
        """
        SELECT feedId AS feedId, COUNT(*) AS cnt
        FROM articles
        WHERE COALESCE(publishedAt, fetchedAt) >= :since
        GROUP BY feedId
        """,
    )
    suspend fun countByFeedSince(since: Long): List<FeedUnreadCount>

    /** 按 id 批量取文章（推荐流把打分后的 id 序还原成列表，顺序由调用方还原）。 */
    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.id IN (:ids)
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadByIds(ids: List<Long>): List<ArticleWithFeed>

    /**
     * 记录一次打开（ADR-0013）：每次打开详情页都更新，画像靠它做时间衰减。
     * 只写这一列，不碰用户状态（已读/收藏/稍后读）。
     */
    @Query("UPDATE articles SET lastOpenedAt = :now WHERE id = :id")
    suspend fun markOpened(id: Long, now: Long)

    /** 文章所属订阅源（推荐流「减少此类」按 feed 级降权，需要这一列）。 */
    @Query("SELECT feedId FROM articles WHERE id = :id LIMIT 1")
    suspend fun feedIdOf(id: Long): Long?

    @Query(
        """
        SELECT articles.*, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.id = :id
        LIMIT 1
        """,
    )
    suspend fun getWithFeed(id: Long): ArticleWithFeed?

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE (articles.title LIKE :query OR articles.summary LIKE :query
            OR articles.contentText LIKE :query OR feeds.title LIKE :query)
        ORDER BY articles.publishedAt DESC, articles.fetchedAt DESC
        """,
    )
    @Suppress("QUERY_MISMATCH")
    fun search(query: String): Flow<List<ArticleWithFeed>>

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        JOIN articles_fts ON articles_fts.rowid = articles.id
        WHERE articles_fts MATCH :match AND $SEARCH_FILTER_PREDICATE
        ORDER BY articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun searchFts(
        match: String,
        feedId: Long?,
        fromMillis: Long?,
        toMillis: Long?,
        unreadOnly: Boolean,
        starredOnly: Boolean,
        bookmarkedOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<ArticleWithFeed>

    @Query(
        """
        SELECT COUNT(*)
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        JOIN articles_fts ON articles_fts.rowid = articles.id
        WHERE articles_fts MATCH :match AND $SEARCH_FILTER_PREDICATE
        """,
    )
    suspend fun countSearchFts(
        match: String,
        feedId: Long?,
        fromMillis: Long?,
        toMillis: Long?,
        unreadOnly: Boolean,
        starredOnly: Boolean,
        bookmarkedOnly: Boolean,
    ): Int

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE (articles.title LIKE :pattern ESCAPE '\' OR articles.summary LIKE :pattern ESCAPE '\'
            OR articles.contentText LIKE :pattern ESCAPE '\' OR feeds.title LIKE :pattern ESCAPE '\')
            AND $SEARCH_FILTER_PREDICATE
        ORDER BY articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun searchLike(
        pattern: String,
        feedId: Long?,
        fromMillis: Long?,
        toMillis: Long?,
        unreadOnly: Boolean,
        starredOnly: Boolean,
        bookmarkedOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<ArticleWithFeed>

    @Query(
        """
        SELECT COUNT(*)
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE (articles.title LIKE :pattern ESCAPE '\' OR articles.summary LIKE :pattern ESCAPE '\'
            OR articles.contentText LIKE :pattern ESCAPE '\' OR feeds.title LIKE :pattern ESCAPE '\')
            AND $SEARCH_FILTER_PREDICATE
        """,
    )
    suspend fun countSearchLike(
        pattern: String,
        feedId: Long?,
        fromMillis: Long?,
        toMillis: Long?,
        unreadOnly: Boolean,
        starredOnly: Boolean,
        bookmarkedOnly: Boolean,
    ): Int

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE $LIBRARY_CONDITION
        ORDER BY CASE WHEN :starred = 1 THEN articles.starredAt ELSE articles.bookmarkedAt END DESC,
                 articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadLibraryByAddedTime(
        starred: Boolean,
        feedId: Long?,
        fromMillis: Long?,
        toMillis: Long?,
        limit: Int,
        offset: Int,
    ): List<ArticleWithFeed>

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE $LIBRARY_CONDITION
        ORDER BY articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadLibraryByPublishedAt(
        starred: Boolean,
        feedId: Long?,
        fromMillis: Long?,
        toMillis: Long?,
        limit: Int,
        offset: Int,
    ): List<ArticleWithFeed>

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE $LIBRARY_CONDITION
        ORDER BY feeds.title ASC, articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadLibraryByFeed(
        starred: Boolean,
        feedId: Long?,
        fromMillis: Long?,
        toMillis: Long?,
        limit: Int,
        offset: Int,
    ): List<ArticleWithFeed>

    @Query("SELECT COUNT(*) FROM articles WHERE $LIBRARY_CONDITION")
    suspend fun countLibrary(
        starred: Boolean,
        feedId: Long?,
        fromMillis: Long?,
        toMillis: Long?,
    ): Int

    @Query("SELECT id, title, summary, contentText FROM articles WHERE searchText IS NULL OR searchText = '' ORDER BY id ASC LIMIT :limit")
    suspend fun articlesMissingSearchText(limit: Int): List<ArticleSearchSourceRow>

    @Query("UPDATE articles SET searchText = :searchText WHERE id = :id")
    suspend fun setSearchText(id: Long, searchText: String)

    @Query("UPDATE articles SET searchText = NULL")
    suspend fun clearSearchText()

    @Query("SELECT COUNT(*) FROM articles WHERE searchText IS NOT NULL AND searchText != ''")
    suspend fun countIndexed(): Int

    @Query("SELECT COUNT(*) FROM articles")
    suspend fun countAll(): Int

    @Query("SELECT * FROM articles ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun pageAllArticles(limit: Int, offset: Int): List<ArticleEntity>

    /**
     * 同一订阅源的音频条目（含播放地址），新→旧。
     *
     * 播放队列按源而不是按全库：播客是一个节目一个队列，跨源串烧没有意义，
     * 而"听完这集接着听这个节目的下一集"是播客的标准行为。
     */
    @Query(
        "SELECT * FROM articles WHERE feedId = :feedId AND mediaKind = :mediaKind " +
            "AND mediaUrl IS NOT NULL AND mediaUrl <> '' " +
            "ORDER BY publishedAt DESC, id DESC",
    )
    suspend fun audioOfFeed(feedId: Long, mediaKind: Int): List<ArticleEntity>

    /**
     * 过滤规则扫描：按 id 游标推进（不是 OFFSET）。
     *
     * HIDE 会**真删行**，用 OFFSET 分页会跳过后面的文章；按 id 递增走则不受影响。
     * 排序必须是裸列 `articles.id`——ORDER BY 里带表达式 Room 解析不了。
     */
    @Query(
        "SELECT articles.id AS id, articles.feedId AS feedId, feeds.groupName AS feedGroup, " +
            "articles.title AS title, articles.summary AS summary, articles.contentText AS contentText, " +
            "articles.author AS author, articles.isStarred AS isStarred, " +
            "articles.isBookmarked AS isBookmarked " +
            "FROM articles JOIN feeds ON feeds.id = articles.feedId " +
            "WHERE articles.id > :afterId ORDER BY articles.id ASC LIMIT :limit",
    )
    suspend fun scanForRules(afterId: Long, limit: Int): List<RuleScanRow>

    @Query("SELECT id FROM articles WHERE feedId = :feedId AND link = :link LIMIT 1")
    suspend fun findIdByFeedAndLink(feedId: Long, link: String): Long?

    @Query(
        "SELECT COUNT(*) FROM articles WHERE title LIKE :pattern ESCAPE '\\' " +
            "OR summary LIKE :pattern ESCAPE '\\' OR contentText LIKE :pattern ESCAPE '\\'",
    )
    suspend fun countMatching(pattern: String): Int

    @Query("SELECT COUNT(*) FROM articles")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM articles WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query(
        """
        SELECT feedId AS feedId, COUNT(*) AS cnt
        FROM articles
        WHERE isRead = 0
        GROUP BY feedId
        """,
    )
    fun observeUnreadCountByFeed(): Flow<List<FeedUnreadCount>>

    /**
     * 每个订阅源最近一篇文章的时间（无发布时间用抓取时间兜底，COALESCE 里 NULL 沉底）。
     * 订阅列表「按最近更新」排序用。列名对齐 [FeedLatestTime]。
     */
    @Query(
        """
        SELECT feedId AS feedId, MAX(COALESCE(publishedAt, fetchedAt)) AS latest
        FROM articles
        GROUP BY feedId
        """,
    )
    fun observeLatestTimeByFeed(): Flow<List<FeedLatestTime>>

    @Query("SELECT id FROM articles WHERE feedId = :feedId AND link = :link LIMIT 1")
    suspend fun findIdByLink(feedId: Long, link: String): Long?

    /** 该源全部已有文章的 id/link 对，一次查询建映射（#48 批量 upsert）。 */
    @Query("SELECT id, link FROM articles WHERE feedId = :feedId")
    suspend fun getIdLinkPairsByFeed(feedId: Long): List<ArticleIdLink>

    /** 同源文章 id（列表序：新→旧），详情页上一篇/下一篇导航用。 */
    @Query(
        """
        SELECT id FROM articles WHERE feedId = :feedId
        ORDER BY publishedAt IS NULL, publishedAt DESC, fetchedAt DESC
        """,
    )
    suspend fun getFeedArticleIds(feedId: Long): List<Long>

    /**
     * 增量刷新：只更新内容状态（标题/时间/摘要/正文），绝不触碰用户状态
     * （isRead/isStarred/isBookmarked）。见 CONTEXT.md「用户状态」。
     */
    @Query(
        """
        UPDATE articles SET
            title = :title, summary = :summary, content = :content, contentText = :contentText,
            author = :author, publishedAt = :publishedAt, coverUrl = :coverUrl,
            readingMinutes = :readingMinutes, contentSource = :contentSource, fetchedAt = :fetchedAt,
            mediaKind = :mediaKind, mediaUrl = :mediaUrl, searchText = :searchText
        WHERE id = :id
        """,
    )
    suspend fun updateContentState(
        id: Long,
        title: String,
        summary: String?,
        content: String?,
        contentText: String?,
        author: String?,
        publishedAt: Long?,
        coverUrl: String?,
        readingMinutes: Int?,
        contentSource: Int,
        fetchedAt: Long,
        mediaKind: Int,
        mediaUrl: String?,
        searchText: String?,
    )

    /**
     * 抓取原网页正文后回填。同样不触碰用户状态；封面只在原本没有时才补 og:image。
     * [contentIncomplete] 由抓取端判定（ADR-0012）：正文过短/无段落/JS 空壳/付费墙时置 1，
     * 内容照写，但 UI 必须如实提示"不完整"。
     */
    @Query(
        """
        UPDATE articles SET
            content = :content, contentText = :contentText, contentSource = :contentSource,
            readingMinutes = :readingMinutes, contentIncomplete = :contentIncomplete,
            coverUrl = COALESCE(coverUrl, :coverUrl)
        WHERE id = :id
        """,
    )
    suspend fun updateFetchedContent(
        id: Long,
        content: String?,
        contentText: String?,
        contentSource: Int,
        readingMinutes: Int?,
        coverUrl: String?,
        contentIncomplete: Boolean,
    )

    @Query("UPDATE articles SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    /** 已读/未读互切（长按菜单，issue #46）。 */
    @Query("UPDATE articles SET isRead = :read WHERE id = :id")
    suspend fun setRead(id: Long, read: Boolean)

    /**
     * 按条件批量标记已读（#10）：只更新未读行，返回真实影响行数（UI 如实汇报数字）。
     * 时间基准 = COALESCE(publishedAt, fetchedAt)，与归档清理、MarkAsReadCondition 一致。
     */
    @Query(
        "UPDATE articles SET isRead = 1 WHERE isRead = 0 " +
            "AND COALESCE(publishedAt, fetchedAt) < :cutoff",
    )
    suspend fun markReadOlderThan(cutoff: Long): Int

    /** 全部未读 → 已读，返回真实影响行数。 */
    @Query("UPDATE articles SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllUnreadRead(): Int

    /** 滚动自动标记已读（#11）用：只更新给定 id 里仍未读的行。 */
    @Query("UPDATE articles SET isRead = 1 WHERE isRead = 0 AND id IN (:ids)")
    suspend fun markReadBatch(ids: List<Long>): Int

    /**
     * 同步后新进库且未读的文章（#31 通知用）："入库时间 ≥ 本轮同步起点"即为新文章，
     * 逐个源的通知开关在这一层过滤——关掉的源一条都不进通知。
     * [limit] 只是通知里要展示的条数上限。
     */
    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.fetchedAt >= :since AND articles.isRead = 0 AND feeds.notificationsEnabled = 1
        ORDER BY articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadNewUnreadSince(since: Long, limit: Int): List<ArticleWithFeed>

    /** 删除单篇文章（撤销由 restore 带原 id 插回）。 */
    @Query("DELETE FROM articles WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 撤销删除：原样插回（REPLACE 保 id 不变；订阅源未动，外键不悬空）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restore(article: ArticleEntity)

    @Query(
        "UPDATE articles SET isStarred = :starred, " +
            "starredAt = CASE WHEN :starred = 1 THEN :now ELSE NULL END WHERE id = :id",
    )
    suspend fun setStarred(id: Long, starred: Boolean, now: Long)

    @Query(
        "UPDATE articles SET isBookmarked = :bookmarked, " +
            "bookmarkedAt = CASE WHEN :bookmarked = 1 THEN :now ELSE NULL END WHERE id = :id",
    )
    suspend fun setBookmarked(id: Long, bookmarked: Boolean, now: Long)

    @Query("UPDATE articles SET isStarred = 0, starredAt = NULL WHERE id IN (:ids)")
    suspend fun unstarBatch(ids: List<Long>): Int

    @Query("UPDATE articles SET isBookmarked = 0, bookmarkedAt = NULL WHERE id IN (:ids)")
    suspend fun unbookmarkBatch(ids: List<Long>): Int

    // 过滤规则的批量动作：加标记时同时写时间戳，与单篇操作的口径一致
    // （「按收藏时间排序」依赖 starredAt 非空，规则加星却留空会让排序把它甩到最后）

    @Query("UPDATE articles SET isStarred = 1, starredAt = :now WHERE id IN (:ids)")
    suspend fun starBatch(ids: List<Long>, now: Long): Int

    @Query("UPDATE articles SET isBookmarked = 1, bookmarkedAt = :now WHERE id IN (:ids)")
    suspend fun bookmarkBatch(ids: List<Long>, now: Long): Int

    @Query("UPDATE articles SET isRead = 1")
    suspend fun markAllRead()

    /** 写入 AI 摘要。生成物不参与内容状态刷新，只由 AI 功能写入/清空。 */
    @Query("UPDATE articles SET aiSummary = :aiSummary WHERE id = :id")
    suspend fun updateAiSummary(id: Long, aiSummary: String?)

    /**
     * 归档清理（issue #57）：删除早于 cutoff 的文章，真删。
     * 豁免 = 用户主动标记（收藏/稍后读）永不自动删除；已读状态不豁免。
     * 保留期基准 = COALESCE(publishedAt, fetchedAt)，与 KeepArchived.cutoffMillis 一致。
     */
    @Query(
        "DELETE FROM articles WHERE isStarred = 0 AND isBookmarked = 0 " +
            "AND COALESCE(publishedAt, fetchedAt) < :cutoff",
    )
    suspend fun deleteExpiredArticles(cutoff: Long): Int

    // —— 墓碑（issue「归档后刷新文章复活」）：删除前抓名单，删除后刷新不再复活 ——

    /** 归档即将删除的文章名单（豁免规则与 deleteExpiredArticles 完全一致）。 */
    @Query(
        "SELECT feedId, link FROM articles WHERE isStarred = 0 AND isBookmarked = 0 " +
            "AND COALESCE(publishedAt, fetchedAt) < :cutoff",
    )
    suspend fun getExpiredArticleLinks(cutoff: Long): List<ArticleFeedLink>

    /** 清空单源前抓名单（豁免规则与 deleteByFeed 一致）。 */
    @Query(
        "SELECT feedId, link FROM articles WHERE feedId = :feedId " +
            "AND isStarred = 0 AND isBookmarked = 0",
    )    suspend fun getArticleLinksByFeed(feedId: Long): List<ArticleFeedLink>

    /** 清空分组前抓名单（豁免规则与 deleteByGroup 一致）。 */
    @Query(
        "SELECT feedId, link FROM articles WHERE isStarred = 0 AND isBookmarked = 0 " +
            "AND feedId IN (SELECT id FROM feeds WHERE groupName = :groupName)",
    )
    suspend fun getArticleLinksByGroup(groupName: String): List<ArticleFeedLink>

    /**
     * 按 id 抓归档名单（过滤规则的 HIDE 动作用）。豁免条件写在这里，
     * 与 [deleteByIds] 保持**逐字一致**——两处不一致就会出现「写了墓碑但没删」
     * 或「删了但没墓碑，下次刷新又复活」。
     */
    @Query("SELECT feedId, link FROM articles WHERE id IN (:ids) AND isStarred = 0 AND isBookmarked = 0")
    suspend fun getArticleLinksByIds(ids: List<Long>): List<ArticleFeedLink>

    @Query("DELETE FROM articles WHERE id IN (:ids) AND isStarred = 0 AND isBookmarked = 0")
    suspend fun deleteByIds(ids: List<Long>): Int

    /** 写墓碑；同一篇重复删除时 IGNORE（保留首次时间，滚动清理按最早一笔算）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTombstones(items: List<ArchivedArticleTombstoneEntity>)

    /** 某 feed 下的墓碑 link 集合，刷新 upsert 用它过滤「删了又回来」。 */
    @Query("SELECT link FROM archived_article_tombstones WHERE feedId = :feedId")
    suspend fun getTombstonedLinks(feedId: Long): List<String>

    /** 墓碑滚动清理：早于 cutoff 的墓碑删除，防止表无限增长。 */
    @Query("DELETE FROM archived_article_tombstones WHERE archivedAt < :cutoff")
    suspend fun deleteTombstonesOlderThan(cutoff: Long): Int

    // —— 清空（issue #8）：只删文章不删源，豁免规则同归档清理 ——
    // 用户主动标记的（收藏/稍后读）不因批量清空丢失，这是「清空」与「删除订阅」的差别：
    // 后者走外键 CASCADE，一律真删。

    /** 清空单个订阅源的文章，返回删除条数。 */
    @Query("DELETE FROM articles WHERE feedId = :feedId AND isStarred = 0 AND isBookmarked = 0")
    suspend fun deleteByFeed(feedId: Long): Int

    /** 清空一个分组下所有订阅源的文章，返回删除条数。 */
    @Query(
        """
        DELETE FROM articles
        WHERE isStarred = 0 AND isBookmarked = 0
            AND feedId IN (SELECT id FROM feeds WHERE groupName = :groupName)
        """,
    )
    suspend fun deleteByGroup(groupName: String): Int

    /** 清空时被豁免保留的条数，供 UI 如实汇报（数字必须真实）。 */
    @Query("SELECT COUNT(*) FROM articles WHERE feedId = :feedId AND (isStarred = 1 OR isBookmarked = 1)")
    suspend fun countProtectedByFeed(feedId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM articles
        WHERE (isStarred = 1 OR isBookmarked = 1)
            AND feedId IN (SELECT id FROM feeds WHERE groupName = :groupName)
        """,
    )
    suspend fun countProtectedByGroup(groupName: String): Int

    // —— 阅读统计仪表盘（#83）：口径 = lastOpenedAt（真实打开），见 #81 定稿 ——

    /** 窗口内打开篇数 + 估算阅读分钟合计（SUM 忽略未估算行）。 */
    @Query(
        "SELECT COUNT(*) AS cnt, SUM(readingMinutes) AS minutes " +
            "FROM articles WHERE lastOpenedAt >= :since",
    )
    suspend fun readingWindowStat(since: Long): ReadingWindowStat

    /** 全部打开时间戳（活跃时段 + streak 的原料）。单列查询，量级 = 打开过的文章数。 */
    @Query("SELECT lastOpenedAt FROM articles WHERE lastOpenedAt IS NOT NULL")
    suspend fun allOpenedTimestamps(): List<Long>

    /** 窗口内每个订阅源的打开次数（源集中度的原料；top 打开源也由此排序）。 */
    @Query(
        """
        SELECT feedId AS feedId, COUNT(*) AS cnt
        FROM articles
        WHERE lastOpenedAt >= :since
        GROUP BY feedId
        ORDER BY cnt DESC
        """,
    )
    suspend fun openedCountsByFeedSince(since: Long): List<FeedUnreadCount>

    /** 窗口内打开最多的前 [limit] 个订阅源（标题来自 JOIN feeds）。 */
    @Query(
        """
        SELECT feeds.title AS feedTitle, COUNT(*) AS cnt
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.lastOpenedAt >= :since
        GROUP BY articles.feedId
        ORDER BY cnt DESC
        LIMIT :limit
        """,
    )
    suspend fun topOpenedFeeds(since: Long, limit: Int): List<FeedOpenStat>

    /** 收藏存量（统计卡）。 */
    @Query("SELECT COUNT(*) FROM articles WHERE isStarred = 1")
    suspend fun starredCount(): Int

    /** 稍后读存量（统计卡）。 */
    @Query("SELECT COUNT(*) FROM articles WHERE isBookmarked = 1")
    suspend fun bookmarkedCount(): Int
}
