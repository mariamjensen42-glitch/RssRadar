package com.cycling.rssradar.core.data.ai

import com.cycling.rssradar.core.data.FakeSharedPreferences
import com.cycling.rssradar.core.data.store.AiBudgetStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


/**
 * 限流器的行为测试。
 *
 * 这些用例全部用 `withTimeout` 包住而不是裸等：限流器的失效模式是**挂起**而不是报错，
 * 裸等会让测试卡到超时机制兜底，症状被掩盖。用 `withTimeout` 才能把「卡住」变成「红」。
 */
class AiRateLimiterTest {

    /** 固定时钟：一个远大于 0、也远小于 Long.MAX 的普通值。 */
    private val fixedNow = 1_700_000_000_000L

    private fun budget(overrides: AiBudgetStore.() -> Unit = {}): AiBudgetStore {
        val store = AiBudgetStore(
            FakeSharedPreferences(),
            clock = { fixedNow },
            zoneOffsetAt = { 0 },
        )
        store.overrides()
        return store
    }

    private fun limiter(budget: AiBudgetStore): AiRateLimiter =
        AiRateLimiter(budget, clock = { fixedNow })

    @Test
    fun `首次调用立即执行，不被限流挡住`() {
        runBlocking {
            var entered = false
            withTimeout(2_000) {
                limiter(budget()).withPermit { entered = true }
            }
            assertTrue("首次调用应立刻进入，而不是被限流挂住", entered)
        }
    }

    @Test
    fun `连续调用在固定时钟下不会累积出超长等待`() {
        runBlocking {
            val budget = budget { setMinIntervalMs(50) }
            val limiter = limiter(budget)
            withTimeout(2_000) { limiter.withPermit { } }
            // 时钟固定不动，第二次的间隔判定应退化为「已过间隔」，而不是累积出超长等待
            withTimeout(2_000) { limiter.withPermit { } }
        }
    }

    @Test
    fun `额度用尽时不执行也不记账`() {
        runBlocking {
            val budget = budget { setDailyLimit(1) }
            budget.record(10, 10, success = true)
            val limiter = limiter(budget)

            val outcome = withTimeout(2_000) { limiter.withPermit { "ran" } }

            assertEquals(AiCallResult.OutOfBudget, outcome)
            // 没发起的调用不该占用额度，否则「已用」会超过「上限」
            assertEquals(1, budget.current().usedToday)
        }
    }

    @Test
    fun `有额度时把结果透传出来`() {
        runBlocking {
            val outcome = withTimeout(2_000) { limiter(budget()).withPermit { "ok" } }
            assertTrue(outcome is AiCallResult.Ok)
            assertEquals("ok", (outcome as AiCallResult.Ok).value)
        }
    }

    @Test
    fun `限流间隔为零时完全不等待`() {
        runBlocking {
            val limiter = limiter(budget { setMinIntervalMs(0) })
            repeat(5) {
                withTimeout(500) { limiter.withPermit { } }
            }
        }
    }
}
