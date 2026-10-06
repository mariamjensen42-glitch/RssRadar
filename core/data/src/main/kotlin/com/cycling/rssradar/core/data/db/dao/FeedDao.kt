package com.cycling.rssradar.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cycling.rssradar.core.data.db.DEFAULT_GROUP
import com.cycling.rssradar.core.data.db.entity.FeedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(feed: FeedEntity): Long

    @Query("SELECT id FROM feeds WHERE url = :url LIMIT 1")
    suspend fun findIdByUrl(url: String): Long?

    @Query("SELECT * FROM feeds WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FeedEntity?

    @Query("SELECT * FROM feeds ORDER BY groupName ASC, title ASC")
    fun observeAll(): Flow<List<FeedEntity>>

    /** 订阅源总数（「我的」页统计条用）。 */
    @Query("SELECT COUNT(*) FROM feeds")
    fun observeFeedCount(): Flow<Int>

    @Query("SELECT * FROM feeds")
    suspend fun getAll(): List<FeedEntity>

    @Query("UPDATE feeds SET groupName = :groupName WHERE id = :feedId")
    suspend fun updateGroup(feedId: Long, groupName: String)

    /** 批量移动订阅源到分组（issue #7）。 */
    @Query("UPDATE feeds SET groupName = :groupName WHERE id IN (:feedIds)")
    suspend fun updateGroupForFeeds(feedIds: List<Long>, groupName: String)

    /** 重命名分组：该组所有 feed 的 groupName 批量改。 */
    @Query("UPDATE feeds SET groupName = :newName WHERE groupName = :oldName")
    suspend fun renameGroup(oldName: String, newName: String)

    /** 删除分组：该组 feed 全部移回默认组（不删源）。 */
    @Query("UPDATE feeds SET groupName = :defaultGroup WHERE groupName = :groupName")
    suspend fun moveGroupToDefault(groupName: String, defaultGroup: String = DEFAULT_GROUP)

    @Query("UPDATE feeds SET title = :title WHERE id = :feedId")
    suspend fun updateTitle(feedId: Long, title: String)

    /**
     * 失效源自愈（对标 ReadYou 的地址纠错）：旧地址解析不出 feed、但从其 HTML
     * autodiscovery 验证出新地址后改写。同时清掉旧地址的 ETag/Last-Modified 协商凭证
     * ——凭证是旧 URL 的，带着去请求新 URL 语义错误（服务器若碰巧命中 304 会丢整轮更新）。
     */
    @Query("UPDATE feeds SET url = :url, etag = NULL, lastModified = NULL WHERE id = :feedId")
    suspend fun updateUrl(feedId: Long, url: String)

    /** 站点图标回填（只在为 null 时抓，写入后不再覆盖，见 CONTEXT.md「站点图标」）。 */
    @Query("UPDATE feeds SET iconUrl = :iconUrl WHERE id = :feedId")
    suspend fun updateIconUrl(feedId: Long, iconUrl: String)

    /** 删除订阅源：articles 经外键 CASCADE 级联删除。 */
    @Query("DELETE FROM feeds WHERE id = :feedId")
    suspend fun deleteFeed(feedId: Long)

    /** 批量删除订阅源（多选模式）：一条 DELETE ... WHERE id IN，articles 仍走 CASCADE。 */
    @Query("DELETE FROM feeds WHERE id IN (:feedIds)")
    suspend fun deleteFeeds(feedIds: List<Long>)

    /** 自动同步开关（issue #58）：屏蔽后不参与自动同步，手动刷新照常。 */
    @Query("UPDATE feeds SET syncEnabled = :enabled WHERE id = :feedId")
    suspend fun updateSyncEnabled(feedId: Long, enabled: Boolean)

    /** 内容类型（ADR-0014）：只影响列表浏览形态，不影响数据。 */
    @Query("UPDATE feeds SET contentType = :contentType WHERE id = :feedId")
    suspend fun updateContentType(feedId: Long, contentType: Int)

    /** Feed 级预设：全文抓取开关（issue #9）。 */
    @Query("UPDATE feeds SET fullContentEnabled = :enabled WHERE id = :feedId")
    suspend fun updateFullContentEnabled(feedId: Long, enabled: Boolean)

    /** Feed 级通知开关（#31）。 */
    @Query("UPDATE feeds SET notificationsEnabled = :enabled WHERE id = :feedId")
    suspend fun updateNotificationsEnabled(feedId: Long, enabled: Boolean)

    /** 参与自动同步的源 id 清单（issue #58）。 */
    @Query("SELECT id FROM feeds WHERE syncEnabled = 1")
    suspend fun getSyncEnabledFeedIds(): List<Long>

    /** 空分区空态判定（issue #75）：该内容类型的订阅源数量。 */
    @Query("SELECT COUNT(*) FROM feeds WHERE contentType = :contentType")
    suspend fun countFeedsByContentType(contentType: Int): Int

    /**
     * 刷新后记录 HTTP 协商凭证（ETag / Last-Modified）。服务器没回就传 null 清空，
     * 避免拿陈旧凭证反复请求导致 304 误判「源没变」。
     */
    @Query("UPDATE feeds SET etag = :etag, lastModified = :lastModified WHERE id = :feedId")
    suspend fun updateValidators(feedId: Long, etag: String?, lastModified: String?)

    // —— 失效源检测（#82）：判定规则在 core/domain FeedHealth，这里只做计数 ——
    // 写放大权衡（1000+ 源每次全量刷新都过这里）：成功路径只在「确实有失败要清」
    // 或「从未记过成功」时才写，健康源的常规刷新（200/304）零额外写。

    /** 刷新成功（含 304）：清零失败计数并记录恢复时间。健康源谓词不命中，不产生写入。 */
    @Query(
        "UPDATE feeds SET consecutiveFailures = 0, failureReason = NULL, lastSuccessAt = :now " +
            "WHERE id = :feedId AND (consecutiveFailures > 0 OR lastSuccessAt IS NULL)",
    )
    suspend fun recordRefreshSuccess(feedId: Long, now: Long)

    /** 刷新失败：计数 +1 并覆盖失败分类。分类由 RefreshEngine 用 FeedProbeResult.from 归出。 */
    @Query(
        "UPDATE feeds SET consecutiveFailures = consecutiveFailures + 1, failureReason = :reason " +
            "WHERE id = :feedId",
    )
    suspend fun recordRefreshFailure(feedId: Long, reason: String?)

    /** 有失败记录的源 id（每日探测 Worker 只盯伤员，#80）。 */
    @Query("SELECT id FROM feeds WHERE consecutiveFailures > 0")
    suspend fun getFeedIdsWithFailures(): List<Long>
}
