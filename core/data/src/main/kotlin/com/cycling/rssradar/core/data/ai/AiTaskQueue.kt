package com.cycling.rssradar.core.data.ai

import com.cycling.rssradar.core.data.db.AiTaskDao
import com.cycling.rssradar.core.data.db.AiTaskEntity


/** 队列当前状态快照，队列页与设置页摘要用。 */
data class AiQueueSnapshot(
    val pending: Int = 0,
    val running: Int = 0,
    val done: Int = 0,
    val failed: Int = 0,
) {
    val total: Int get() = pending + running + done + failed
}


/**
 * AI 任务队列。
 *
 * 为什么不直接用 WorkManager 的工作链：任务之间**没有依赖关系**，
 * 且需要在"今日额度"这个共享约束下动态增减——排队、去重、退避、失败留痕全都要能查能改，
 * 一张表比一串 WorkRequest 好管得多。Worker 只负责定时来这里领活。
 *
 * 三条纪律：
 * 1. 入队用 REPLACE + dedupeKey，重复入队＝重新排队，不会堆出两条相同任务。
 * 2. 领取后立刻标记 RUNNING，进程被杀后由 [maintenance] 重置回待执行——
 *    不重置的话这些任务会永远卡在 RUNNING，既跑不了也清不掉。
 * 3. 失败**同样占额度**（由 [AiRateLimiter] 记账），所以退避必须够长，
 *    否则一个反复失败的任务能在几分钟内烧穿一天额度。
 */
class AiTaskQueue(private val dao: AiTaskDao) {

    /** 批量入队，返回实际写入条数。 */
    suspend fun enqueue(specs: List<AiTaskPlanner.Spec>, now: Long = System.currentTimeMillis()): Int {
        if (specs.isEmpty()) return 0
        val tasks = specs.map { spec ->
            AiTaskEntity(
                kind = spec.feature.dbValue,
                targetId = spec.targetId,
                payload = "",
                status = AiTaskEntity.STATUS_PENDING,
                attempts = 0,
                priority = spec.priority,
                createdAt = now,
                runAfter = now,
                updatedAt = now,
                dedupeKey = AiTaskEntity.dedupeKey(spec.feature.dbValue, spec.targetId),
            )
        }
        dao.enqueueAll(tasks)
        return tasks.size
    }

    /** 单条入队（手动重试、订阅源级任务）。 */
    suspend fun enqueueOne(
        feature: AiFeature,
        targetId: Long,
        priority: Int = 0,
        payload: String = "",
        now: Long = System.currentTimeMillis(),
    ) {
        dao.enqueue(
            AiTaskEntity(
                kind = feature.dbValue,
                targetId = targetId,
                payload = payload,
                status = AiTaskEntity.STATUS_PENDING,
                attempts = 0,
                priority = priority,
                createdAt = now,
                runAfter = now,
                updatedAt = now,
                dedupeKey = AiTaskEntity.dedupeKey(feature.dbValue, targetId),
            ),
        )
    }

    /** 领取一批可执行任务。limit 由并发上限决定。 */
    suspend fun claim(now: Long = System.currentTimeMillis(), limit: Int): List<AiTaskEntity> =
        dao.claimable(now, limit)

    suspend fun markRunning(task: AiTaskEntity, attempts: Int, now: Long = System.currentTimeMillis()) =
        dao.markRunning(task.id, attempts, now)

    suspend fun succeed(task: AiTaskEntity, attempts: Int, now: Long = System.currentTimeMillis()) =
        dao.finish(task.id, AiTaskEntity.STATUS_DONE, attempts, now, null)

    /**
     * 记一次失败。
     * @return true = 已退避重新排队；false = 达到重试上限，转为终态失败。
     */
    suspend fun fail(
        task: AiTaskEntity,
        attempts: Int,
        error: String,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        return if (AiTaskPlanner.shouldRetry(attempts)) {
            dao.requeue(task.id, now + AiTaskPlanner.backoffMs(attempts), now)
            true
        } else {
            // 终态也记 attempts，队列页要显示"试了 3 次"。
            dao.finish(task.id, AiTaskEntity.STATUS_FAILED, attempts, now, error.take(200))
            false
        }
    }

    /**
     * 推迟某个任务（额度用尽时用）。
     *
     * 与 [fail] 的区别：**不增加尝试次数**。额度用尽不是这个任务的错，
     * 把它计入 attempts 会让"今天没跑完的任务"在明后天被误判为反复失败而转终态。
     */
    suspend fun postpone(id: Long, runAfter: Long, now: Long = System.currentTimeMillis()) =
        dao.requeue(id, runAfter, now)

    /** 把终态失败的任务全部改回待执行（用户在队列页点「重试全部失败」）。 */
    suspend fun retryFailed(now: Long = System.currentTimeMillis()): Int {
        val failed = dao.ofStatus(AiTaskEntity.STATUS_FAILED, limit = 500)
        failed.forEach { dao.requeue(it.id, now, now) }
        return failed.size
    }

    /**
     * 队列维护：重置僵死的 RUNNING、清理过期终态任务。
     * 每次批处理领活前调一次，成本是两条 UPDATE/DELETE，换来的是不会越积越多的死任务。
     */
    suspend fun maintenance(now: Long = System.currentTimeMillis()) {
        dao.resetStaleRunning(now - AiTaskEntity.STALE_RUNNING_MS, now)
        dao.purgeFinished(now - AiTaskEntity.FINISHED_RETENTION_MS)
    }

    suspend fun snapshot(): AiQueueSnapshot {
        val counts = dao.statusCounts().associate { it.status to it.total }
        return AiQueueSnapshot(
            pending = counts[AiTaskEntity.STATUS_PENDING] ?: 0,
            running = counts[AiTaskEntity.STATUS_RUNNING] ?: 0,
            done = counts[AiTaskEntity.STATUS_DONE] ?: 0,
            failed = counts[AiTaskEntity.STATUS_FAILED] ?: 0,
        )
    }

    /** 队列页展示用：最近的任务（含终态，能看到失败原因）。 */
    suspend fun recent(limit: Int = 50): List<AiTaskEntity> = dao.recent(limit)

    suspend fun clearPending() = dao.clearPending()

    suspend fun clearAll() = dao.clearAll()

    suspend fun delete(id: Long) = dao.delete(id)

    /** 该功能的所有待执行任务（用户在设置页关掉某项后调用）。 */
    suspend fun dropPendingOf(feature: AiFeature) {
        val pending = dao.ofStatus(AiTaskEntity.STATUS_PENDING, limit = 1000)
        pending.filter { it.kind == feature.dbValue }.forEach { dao.delete(it.id) }
    }
}
