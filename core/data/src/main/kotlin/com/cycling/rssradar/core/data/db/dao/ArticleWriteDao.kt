package com.cycling.rssradar.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cycling.rssradar.core.data.db.entity.ArticleEntity

@Dao
interface ArticleWriteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(articles: List<ArticleEntity>): List<Long>

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
}
