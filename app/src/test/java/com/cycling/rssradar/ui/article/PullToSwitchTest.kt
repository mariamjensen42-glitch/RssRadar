package com.cycling.rssradar.ui.article

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 越界切篇判据（ReadYou 差距表 #23）。
 *
 * 符号约定来自 M3 `material3/pulltorefresh/PullToRefresh.kt` 源码（`> 0` 手指下滑），
 * 这里按同一约定断言——改符号前先去看那份源码，别凭手感翻。
 */
class PullToSwitchTest {

    // ---- 累积 ----

    @Test
    fun `pulling down at the top accumulates positive`() {
        val after = accumulatePull(current = 0f, delta = 40f, atEdge = true)
        assertEquals(40f, after)
    }

    @Test
    fun `pulling up at the bottom accumulates negative`() {
        val after = accumulatePull(current = 0f, delta = -40f, atEdge = true)
        assertEquals(-40f, after)
    }

    @Test
    fun `leaving the edge resets the accumulation`() {
        val pulled = accumulatePull(current = 0f, delta = 100f, atEdge = true)
        assertEquals(0f, accumulatePull(current = pulled, delta = 100f, atEdge = false))
    }

    @Test
    fun `reversing direction resets instead of cancelling out`() {
        val down = accumulatePull(current = 0f, delta = 100f, atEdge = true)
        assertEquals(
            "来回蹭不该能凑出一次切篇",
            0f,
            accumulatePull(current = down, delta = -20f, atEdge = true),
        )
    }

    @Test
    fun `accumulation is clamped at the trigger distance`() {
        assertEquals(
            PULL_TO_SWITCH_DISTANCE,
            accumulatePull(current = PULL_TO_SWITCH_DISTANCE, delta = 999f, atEdge = true),
        )
        assertEquals(
            -PULL_TO_SWITCH_DISTANCE,
            accumulatePull(current = -PULL_TO_SWITCH_DISTANCE, delta = -999f, atEdge = true),
        )
    }

    // ---- 判据 ----

    @Test
    fun `pulling past the threshold at the top goes to the previous article`() {
        assertEquals(
            PullTarget.PREVIOUS,
            pullTarget(
                accumulated = PULL_TO_SWITCH_DISTANCE,
                atTop = true,
                atBottom = false,
                hasPrev = true,
                hasNext = true,
            ),
        )
    }

    @Test
    fun `pulling past the threshold at the bottom goes to the next article`() {
        assertEquals(
            PullTarget.NEXT,
            pullTarget(
                accumulated = -PULL_TO_SWITCH_DISTANCE,
                atTop = false,
                atBottom = true,
                hasPrev = true,
                hasNext = true,
            ),
        )
    }

    @Test
    fun `nothing happens below the threshold`() {
        assertEquals(
            PullTarget.NONE,
            pullTarget(
                accumulated = PULL_TO_SWITCH_DISTANCE - 1,
                atTop = true,
                atBottom = false,
                hasPrev = true,
                hasNext = true,
            ),
        )
    }

    @Test
    fun `no neighbour means no gesture`() {
        assertEquals(
            "没有上一篇时不该给一个拉到底什么也没发生的手势",
            PullTarget.NONE,
            pullTarget(
                accumulated = PULL_TO_SWITCH_DISTANCE,
                atTop = true,
                atBottom = false,
                hasPrev = false,
                hasNext = true,
            ),
        )
        assertEquals(
            PullTarget.NONE,
            pullTarget(
                accumulated = -PULL_TO_SWITCH_DISTANCE,
                atTop = false,
                atBottom = true,
                hasPrev = true,
                hasNext = false,
            ),
        )
    }

    @Test
    fun `pulling up at the top is not a switch`() {
        assertEquals(
            PullTarget.NONE,
            pullTarget(
                accumulated = -PULL_TO_SWITCH_DISTANCE,
                atTop = true,
                atBottom = false,
                hasPrev = true,
                hasNext = true,
            ),
        )
    }
}
