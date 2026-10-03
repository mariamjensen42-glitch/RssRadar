package com.cycling.rssradar.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.cycling.rssradar.core.model.AiFeature

/**
 * AI 任务队列。
 *
 * 存在的理由只有一个：**AI 调用又慢又要钱，不能跟着刷新同步跑**。
 * 刷新完 200 篇文章就同步发 200 次请求，主线程会卡、额度会瞬间烧穿。
 * 所有 BATCH 触发的功能都先进这张表，由 `AiDailyWorker` 在后台
 * 按并发上限、最小间隔和日预算慢慢消化；失败按指数退避重试，超过上限转终态并留错误。
 *
 * 去重靠 `dedupeKey`（`kind:targetId:scope` 形态），入队用 REPLACE：
 * 重复入队等于「重新排队」而不是堆两条，避免同一篇文章被批处理反复挑中。
 */
@Entity(
    tableName = "ai_tasks",
    indices = [
        // 领取任务的查询：待执行 + 到点，按优先级与时间排序。
        Index(value = ["status", "runAfter"]),
        // 去重键：入队前查是否已存在。
        Index(value = ["dedupeKey"]),
    ],
)

data class AiTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [AiFeature.dbValue]。 */
    val kind: Int,
    /** 目标 id：文章 id / 订阅源 id / 全局任务为 0。 */
    val targetId: Long,
    /** 附加参数 JSON（如问答的问题、过滤规则的自然语言描述）。 */
    val payload: String = "",
    /** [STATUS_PENDING] / [STATUS_RUNNING] / [STATUS_DONE] / [STATUS_FAILED]。 */
    val status: Int = STATUS_PENDING,
    /** 已尝试次数，达到 MAX_ATTEMPTS 转终态。 */
    val attempts: Int = 0,
    /** 越大越先跑，取自订阅源优先级或功能默认优先级。 */
    @ColumnInfo(defaultValue = "0") val priority: Int = 0,
    val createdAt: Long,
    /** 限速用：早于这个时间戳不领取。重试时按退避往后推。 */
    val runAfter: Long,
    val updatedAt: Long,
    val lastError: String? = null,
    /** `kind:targetId`；同一目标同一功能只保留一条在队。 */
    val dedupeKey: String,
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_RUNNING = 1
        const val STATUS_DONE = 2
        const val STATUS_FAILED = 3

        /** 失败重试上限。取 3 而不是更大：AI 失败多为内容问题（太长/无正文），重试再多也一样。 */
        const val MAX_ATTEMPTS = 3

        /** 终态任务保留时长（7 天）——留着让用户在队列页看见失败原因，之后自动清理。 */
        const val FINISHED_RETENTION_MS = 7 * 24 * 60 * 60 * 1000L

        /** RUNNING 超过这个时长视为上次进程被杀，重置回 PENDING 重跑。 */
        const val STALE_RUNNING_MS = 30 * 60 * 1000L

        fun dedupeKey(kind: Int, targetId: Long): String = "$kind:$targetId"
    }
}

@Dao

interface AiTaskDao {
    /** 入队。REPLACE 语义 = 同 dedupeKey 重新排队（重置为待执行），不会堆重复任务。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(task: AiTaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueAll(tasks: List<AiTaskEntity>)

    /** 入队前查重：已有非终态任务就不再插一条。 */
    @Query("SELECT * FROM ai_tasks WHERE dedupeKey = :dedupeKey LIMIT 1")
    suspend fun findByDedupeKey(dedupeKey: String): AiTaskEntity?

    @Query("SELECT * FROM ai_tasks WHERE dedupeKey IN (:keys)")
    suspend fun findByDedupeKeys(keys: List<String>): List<AiTaskEntity>

    /**
     * 领取一批可执行任务：待执行且已到点，优先级高、入队早的先跑。
     * limit 由并发上限决定——一次领太多会在进程被杀时白烧一批。
     */
    @Query(
        """
        SELECT * FROM ai_tasks
        WHERE status = 0 AND runAfter <= :now
        ORDER BY priority DESC, runAfter ASC, id ASC
        LIMIT :limit
        """,
    )
    suspend fun claimable(now: Long, limit: Int): List<AiTaskEntity>

    @Query("UPDATE ai_tasks SET status = 1, attempts = :attempts, updatedAt = :now WHERE id = :id")
    suspend fun markRunning(id: Long, attempts: Int, now: Long)

    @Query(
        """
        UPDATE ai_tasks
        SET status = :status, attempts = :attempts, updatedAt = :now, lastError = :error
        WHERE id = :id
        """,
    )
    suspend fun finish(id: Long, status: Int, attempts: Int, now: Long, error: String?)

    /** 失败后按退避重新排队。 */
    @Query("UPDATE ai_tasks SET status = 0, runAfter = :runAfter, updatedAt = :now WHERE id = :id")
    suspend fun requeue(id: Long, runAfter: Long, now: Long)

    @Query("SELECT * FROM ai_tasks WHERE status = :status ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun ofStatus(status: Int, limit: Int): List<AiTaskEntity>

    @Query("SELECT COUNT(*) FROM ai_tasks WHERE status = :status")
    suspend fun countOfStatus(status: Int): Int

    @Query("SELECT * FROM ai_tasks ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<AiTaskEntity>

    /** 队列页用：按状态分组的计数，一次查四个值，避免四个 COUNT 查询。 */
    @Query(
        """
        SELECT status, COUNT(*) AS total FROM ai_tasks
        GROUP BY status
        """,
    )
    suspend fun statusCounts(): List<AiTaskStatusCount>

    /** 上次进程被杀留下的 RUNNING 任务，重置回待执行。 */
    @Query(
        """
        UPDATE ai_tasks SET status = 0, runAfter = :now, updatedAt = :now
        WHERE status = 1 AND updatedAt < :before
        """,
    )
    suspend fun resetStaleRunning(before: Long, now: Long)

    @Query("DELETE FROM ai_tasks WHERE status IN (2, 3) AND updatedAt < :before")
    suspend fun purgeFinished(before: Long)

    @Query("DELETE FROM ai_tasks WHERE status = 0")
    suspend fun clearPending()

    @Query("DELETE FROM ai_tasks")
    suspend fun clearAll()

    @Query("DELETE FROM ai_tasks WHERE id = :id")
    suspend fun delete(id: Long)
}

/** `GROUP BY status` 的结果行。Room 要求有构造函数能承接列名。 */
data class AiTaskStatusCount(
    val status: Int,
    val total: Int,
)
