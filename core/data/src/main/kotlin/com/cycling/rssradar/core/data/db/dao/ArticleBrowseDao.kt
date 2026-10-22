package com.cycling.rssradar.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.cycling.rssradar.core.data.db.projection.ArticleIdContentType
import com.cycling.rssradar.core.data.db.projection.ArticleIdGroup
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.core.data.db.projection.EngagementRow
import com.cycling.rssradar.core.data.db.projection.FeedUnreadCount

@Dao
interface ArticleBrowseDao {
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
     * 推荐候选池：未读 且 发布时间在窗口内。
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
     * 画像样本：真实表达过兴趣的文章——打开过、收藏或稍后读。
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

    /** 窗口内每个订阅源的文章总数：源亲和度的分母（打开率）。 */
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
     * 记录一次打开：每次打开详情页都更新，画像靠它做时间衰减。
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
}
