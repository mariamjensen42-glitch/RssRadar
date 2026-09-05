package com.cycling.rssradar.core.data.ai

import com.cycling.rssradar.core.data.store.AiBudgetStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


/** 一次 AI 调用的结果：拿到值，或因为今日额度用尽而根本没发起。 */
sealed interface AiCallResult<out T> {
    data class Ok<T>(val value: T) : AiCallResult<T>

    /**
     * 今日额度用尽。**不是错误**：调用方应把任务留在队列里等明天，
     * 而不是记一次失败——失败会触发重试，重试又撞额度，白转一圈。
     */
    data object OutOfBudget : AiCallResult<Nothing>
}


/**
 * AI 调用的两道闸：日预算 + 最小发起间隔。
 *
 * **为什么只管这两件事、并发度交给任务队列**：并发度是「一次领几个任务分头跑」的问题，
 * 属于队列的调度策略；而预算与发起节奏对**所有**调用路径（手动问答、阅读页翻译、
 * 后台批处理）都生效，必须收在一处，否则手动点两下就能绕过预算。
 *
 * 最小间隔只序列化「请求发起时刻」，不锁住整个请求——
 * 否则一个 30 秒的长请求会把后续调用全堵住，最小间隔就从「防突发」变成了「串行执行」。
 */
class AiRateLimiter(
    private val budgetStore: AiBudgetStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val startGate = Mutex()

    /**
     * 上次发起请求的时刻。null = 从未发起过。
     *
     * **必须用 null 当哨兵，不能用 `Long.MIN_VALUE`**：后者会让 `clock() - lastStartAt`
     * 溢出成负数（结果超过 Long.MAX），进而算出约 9.2×10¹⁸ 毫秒的等待——
     * 第一次调用就被 `delay` 永久挂起，而且此后每次都撞同一个超长等待。
     * 症状就是「点了按钮一直转圈，什么都不会发生」。
     */
    @Volatile
    private var lastStartAt: Long? = null

    /** 今日是否还有额度（顺带做一次跨天归零）。 */
    fun hasBudget(): Boolean = budgetStore.hasBudget()

    /**
     * 在闸内执行一次调用。
     *
     * @param block 真正发起请求的代码，抛出的异常原样向上传播（调用方自行分类重试）。
     * @return 成功拿到 [AiCallResult.Ok]；额度用尽返回 [AiCallResult.OutOfBudget]（**未执行** block）。
     */
    suspend fun <T> withPermit(block: suspend () -> T): AiCallResult<T> {
        if (!budgetStore.hasBudget()) return AiCallResult.OutOfBudget

        startGate.withLock {
            val interval = budgetStore.current().minIntervalMs
            val elapsed = lastStartAt?.let { clock() - it }
            // 三种情况都立即放行：从未发起过（null）、时钟被回拨（elapsed < 0）、已超过间隔。
            // 只有「在间隔之内」才真的等待，且等待量恒为 (interval - elapsed) ∈ (0, interval]。
            if (elapsed != null && elapsed >= 0 && elapsed < interval) {
                delay(interval - elapsed)
            }
            lastStartAt = clock()
        }

        return AiCallResult.Ok(block())
    }

    /** 记录一次调用结果，用于用量统计。失败同样占额度（反复失败不该免费）。 */
    fun record(inputChars: Int, outputChars: Int, success: Boolean) =
        budgetStore.record(inputChars, outputChars, success)

    /** 测试用：把发起时刻恢复到"从未调用过"，避免用例之间互相干扰。 */
    internal fun resetClock() {
        lastStartAt = null
    }
}
