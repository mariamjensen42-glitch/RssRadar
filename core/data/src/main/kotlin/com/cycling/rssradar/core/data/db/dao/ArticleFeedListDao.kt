package com.cycling.rssradar.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed

@Dao
interface ArticleFeedListDao {
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
        $AI_VALUE_JOIN
        WHERE $GROUP_FILTER_PREDICATE_NULLABLE AND $CONTENT_TYPE_FILTER_PREDICATE AND $AI_VALUE_FILTER_PREDICATE
        """,
    )
    suspend fun countAllWithFeedFilteredByValue(
        group: String?,
        isDefaultGroup: Boolean,
        contentType: Int?,
        minValue: Int?,
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
        $AI_VALUE_JOIN
        WHERE articles.isRead = 0 AND $GROUP_FILTER_PREDICATE_NULLABLE AND $CONTENT_TYPE_FILTER_PREDICATE AND $AI_VALUE_FILTER_PREDICATE
        """,
    )
    suspend fun countUnreadWithFeedFilteredByValue(
        group: String?,
        isDefaultGroup: Boolean,
        contentType: Int?,
        minValue: Int?,
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
        $AI_VALUE_JOIN
        WHERE articles.isStarred = 1 AND $GROUP_FILTER_PREDICATE_NULLABLE AND $CONTENT_TYPE_FILTER_PREDICATE AND $AI_VALUE_FILTER_PREDICATE
        """,
    )
    suspend fun countStarredWithFeedFilteredByValue(
        group: String?,
        isDefaultGroup: Boolean,
        contentType: Int?,
        minValue: Int?,
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

    @Query(
        """
        SELECT COUNT(*) FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        $AI_VALUE_JOIN
        WHERE articles.isBookmarked = 1 AND $GROUP_FILTER_PREDICATE_NULLABLE AND $CONTENT_TYPE_FILTER_PREDICATE AND $AI_VALUE_FILTER_PREDICATE
        """,
    )
    suspend fun countBookmarkedWithFeedFilteredByValue(
        group: String?,
        isDefaultGroup: Boolean,
        contentType: Int?,
        minValue: Int?,
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
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl, $AI_VALUE_COLUMN
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        $AI_VALUE_JOIN
        WHERE $GROUP_FILTER_PREDICATE_NULLABLE AND $CONTENT_TYPE_FILTER_PREDICATE AND $AI_VALUE_FILTER_PREDICATE
        $AI_VALUE_ORDER_BY
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadAllWithFeedPagedFilteredByValue(
        group: String?,
        isDefaultGroup: Boolean,
        contentType: Int?,
        minValue: Int?,
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
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl, $AI_VALUE_COLUMN
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        $AI_VALUE_JOIN
        WHERE articles.isRead = 0 AND $GROUP_FILTER_PREDICATE_NULLABLE AND $CONTENT_TYPE_FILTER_PREDICATE AND $AI_VALUE_FILTER_PREDICATE
        $AI_VALUE_ORDER_BY
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadUnreadWithFeedPagedFilteredByValue(
        group: String?,
        isDefaultGroup: Boolean,
        contentType: Int?,
        minValue: Int?,
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
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl, $AI_VALUE_COLUMN
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        $AI_VALUE_JOIN
        WHERE articles.isStarred = 1 AND $GROUP_FILTER_PREDICATE_NULLABLE AND $CONTENT_TYPE_FILTER_PREDICATE AND $AI_VALUE_FILTER_PREDICATE
        $AI_VALUE_ORDER_BY
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadStarredWithFeedPagedFilteredByValue(
        group: String?,
        isDefaultGroup: Boolean,
        contentType: Int?,
        minValue: Int?,
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

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl, $AI_VALUE_COLUMN
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        $AI_VALUE_JOIN
        WHERE articles.isBookmarked = 1 AND $GROUP_FILTER_PREDICATE_NULLABLE AND $CONTENT_TYPE_FILTER_PREDICATE AND $AI_VALUE_FILTER_PREDICATE
        $AI_VALUE_ORDER_BY
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadBookmarkedWithFeedPagedFilteredByValue(
        group: String?,
        isDefaultGroup: Boolean,
        contentType: Int?,
        minValue: Int?,
        limit: Int,
        offset: Int,
    ): List<ArticleWithFeed>
}
