package com.cycling.rssradar.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cycling.rssradar.core.data.db.entity.ArchivedArticleTombstoneEntity
import com.cycling.rssradar.core.data.db.projection.ArticleFeedLink

@Dao
interface ArticleCleanupDao {
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
}
