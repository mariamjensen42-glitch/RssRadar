package com.cycling.rssradar.core.data.db

import androidx.room.Dao
import androidx.room.Query

/**
 * AI 模块专用查询。
 *
 * 单独一个 DAO 而不是往 `ArticleDao` 里塞：ArticleDao 已 500 多行、承载着列表分页这条主链路，
 * AI 的批处理查询（按时间窗扫候选）与它的关注点完全不同，混在一起两边都难读。
 * 这里每条查询都限定时间窗或走主键，避免后台批处理变成全表扫描拖慢前台。
 */
@Dao

interface AiSupportDao {
    /** 批量取文章要点（不含正文）：跨文章功能（去重/聚合/简报）组 prompt 用。 */
    @Query("SELECT id, feedId, title FROM articles WHERE id IN (:ids)")
    suspend fun briefsOf(ids: List<Long>): List<AiArticleBrief>

    /**
     * 待处理文章：近期抓取且**有内容可送模型**的。
     * 排除既无正文也无摘要的条目——送空内容给模型只会换回一段编造的文字。
     */
    @Query(
        """
        SELECT id FROM articles
        WHERE fetchedAt >= :since
          AND ((contentText IS NOT NULL AND contentText != '') OR (summary IS NOT NULL AND summary != ''))
        ORDER BY fetchedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun processableIdsSince(since: Long, limit: Int): List<Long>

    /** 某订阅源在时间窗内的文章数（健康监控判断"是否停更"）。 */
    @Query("SELECT COUNT(*) FROM articles WHERE feedId = :feedId AND fetchedAt >= :since")
    suspend fun countRecentOfFeed(feedId: Long, since: Long): Int

    /** 某订阅源最后一次抓到文章的时间（健康监控判断"是否失效"）。 */
    @Query("SELECT MAX(fetchedAt) FROM articles WHERE feedId = :feedId")
    suspend fun lastFetchedOfFeed(feedId: Long): Long?

    /** 某订阅源正文不完整的文章数（健康监控判断"抓取质量下滑"）。 */
    @Query("SELECT COUNT(*) FROM articles WHERE feedId = :feedId AND contentIncomplete = 1")
    suspend fun countIncompleteOfFeed(feedId: Long): Int

    /** 某订阅源的文章总数，算不完整率的分母。 */
    @Query("SELECT COUNT(*) FROM articles WHERE feedId = :feedId")
    suspend fun countOfFeed(feedId: Long): Int

    /** 阅读习惯：时间窗内的打开时刻，统计活跃时段用。 */
    @Query(
        """
        SELECT lastOpenedAt FROM articles
        WHERE lastOpenedAt IS NOT NULL AND lastOpenedAt >= :since
        """,
    )
    suspend fun openTimesSince(since: Long): List<Long>

    /** 阅读习惯：时间窗内按订阅源的打开次数，算集中度用。 */
    @Query(
        """
        SELECT feedId, COUNT(*) AS total FROM articles
        WHERE lastOpenedAt IS NOT NULL AND lastOpenedAt >= :since
        GROUP BY feedId
        ORDER BY total DESC
        """,
    )
    suspend fun openCountsByFeedSince(since: Long): List<FeedOpenCount>

    /** 每日报告：时间窗内读过的文章数。 */
    @Query("SELECT COUNT(*) FROM articles WHERE isRead = 1 AND lastOpenedAt >= :since")
    suspend fun countReadSince(since: Long): Int

    /** 每日报告：时间窗内新增但未读的文章 id，供模型挑"错过的好文"。 */
    @Query(
        """
        SELECT id FROM articles
        WHERE isRead = 0 AND isStarred = 0 AND isBookmarked = 0 AND fetchedAt >= :since
        ORDER BY fetchedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun unreadIdsSince(since: Long, limit: Int): List<Long>
}

/** 文章要点：只带组 prompt 需要的字段，避免把正文一起查出来。 */
data class AiArticleBrief(
    val id: Long,
    val feedId: Long,
    val title: String,
)

/** 某订阅源被打开的次数，阅读习惯的集中度计算用。 */
data class FeedOpenCount(
    val feedId: Long,
    val total: Int,
)
