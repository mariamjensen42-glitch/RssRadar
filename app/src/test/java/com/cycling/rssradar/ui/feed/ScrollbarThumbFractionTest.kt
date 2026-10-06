package com.cycling.rssradar.ui.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 滚动条 thumb 位置的守门测试。
 *
 * 这个指示条连续三次栽在「分母里混进了会变的量」上（已加载条数、可见条目数），
 * 每次都表现为「滚动时位置跳动」。这里锁住的是**结果只由两个入参决定**这条结构约束：
 * 函数签名里根本没有「已加载量」「可见条目数」，它们就再也进不来。
 */
class ScrollbarThumbFractionTest {

    @Test
    fun `位置随首可见下标单调不减`() {
        val total = 5000
        var previous = -1f
        listOf(0, 1, 50, 200, 1000, 4999).forEach { index ->
            val current = scrollbarThumbFraction(index, total)
            assertTrue("index=$index 应不小于上一个位置", current >= previous)
            previous = current
        }
    }

    @Test
    fun `位置只看首可见下标——与加载了多少条无关也是一条推论`() {
        // 同一个 index 反复取值必须完全相同：分母若混进任何会变的量，这里就会飘
        val once = scrollbarThumbFraction(firstVisibleIndex = 120, totalCount = 5000)
        repeat(5) {
            assertEquals(once, scrollbarThumbFraction(firstVisibleIndex = 120, totalCount = 5000), 0f)
        }
    }

    @Test
    fun `总数非法时退回 0 而不是除零`() {
        assertEquals(0f, scrollbarThumbFraction(firstVisibleIndex = 10, totalCount = 0), 0f)
        assertEquals(0f, scrollbarThumbFraction(firstVisibleIndex = 10, totalCount = -1), 0f)
    }

    @Test
    fun `越界下标被夹在 0 到 1 之间`() {
        assertEquals(0f, scrollbarThumbFraction(firstVisibleIndex = -5, totalCount = 100), 0f)
        assertEquals(1f, scrollbarThumbFraction(firstVisibleIndex = 500, totalCount = 100), 0f)
    }
}
