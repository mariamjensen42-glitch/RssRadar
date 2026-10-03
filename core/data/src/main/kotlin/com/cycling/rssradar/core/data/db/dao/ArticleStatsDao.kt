package com.cycling.rssradar.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.cycling.rssradar.core.data.db.entity.ArticleEntity
import com.cycling.rssradar.core.data.db.projection.ArticleIdLink
import com.cycling.rssradar.core.data.db.projection.FeedLatestTime
import com.cycling.rssradar.core.data.db.projection.FeedOpenStat
import com.cycling.rssradar.core.data.db.projection.FeedUnreadCount
import com.cycling.rssradar.core.data.db.projection.ReadingWindowStat
import com.cycling.rssradar.core.data.db.projection.RuleScanRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleStatsDao {
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
