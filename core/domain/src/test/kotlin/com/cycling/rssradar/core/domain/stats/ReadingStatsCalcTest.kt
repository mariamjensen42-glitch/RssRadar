package com.cycling.rssradar.core.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


/**
 * 阅读习惯统计的纯算法测试。
 *
 * 阅读统计页上的每个数字都由这里算出，所以它们必须真实：
 * 活跃时段来自真实打开时刻、集中度来自真实来源计数。
 * 算法错了，页面上写得再漂亮也是一份假报告。
 */
class ReadingStatsCalcTest {

    private val hour = 3_600_000L
    private val day = 24 * hour

    @Test
    fun `连续天数从今天往回数`() {
        val today = 100L
        val keys = setOf(100L, 99L, 98L, 95L)
        // 98→99→100 连续 3 天；95 断档不计
        assertEquals(3, ReadingStatsCalc.streakDays(keys, today))
    }

    @Test
    fun `今天还没打开不断连击`() {
        val today = 100L
        val keys = setOf(99L, 98L, 97L)
        assertEquals(3, ReadingStatsCalc.streakDays(keys, today))
    }

    @Test
    fun `昨天也没打开则连击归零`() {
        val today = 100L
        val keys = setOf(98L, 97L)
        assertEquals(0, ReadingStatsCalc.streakDays(keys, today))
    }

    @Test
    fun `空样本连击为零`() {
        assertEquals(0, ReadingStatsCalc.streakDays(emptySet(), 100L))
    }

    @Test
    fun `活跃时段只取高于全天平均的小时`() {
        // UTC 第 100 天的 21 点出现 3 次，22 点 1 次；平均 = 4/24 ≈ 0.17
        val base = 100L * 24 * hour
        val times = listOf(
            base + 21 * hour,
            base + 21 * hour,
            base + 21 * hour,
            base + 22 * hour,
        )
        assertEquals(listOf(21, 22), ReadingStatsCalc.activeHours(times, 0))
    }

    @Test
    fun `时区偏移把 UTC 小时换算成本地小时`() {
        // UTC 16 点 + 东八区 = 本地 0 点（次日）
        val base = 100L * 24 * hour
        val times = listOf(base + 16 * hour)
        assertEquals(listOf(0), ReadingStatsCalc.activeHours(times, 8 * hour.toInt()))
    }

    @Test
    fun `空样本返回空结果而不是报错`() {
        assertTrue(ReadingStatsCalc.activeHours(emptyList(), 0).isEmpty())
    }

    @Test
    fun `集中度在单一来源时为 1`() {
        assertEquals(1.0, ReadingStatsCalc.concentration(listOf(10)), 0.001)
        assertEquals(1.0, ReadingStatsCalc.concentration(listOf(10, 0, 0)), 0.001)
    }

    @Test
    fun `集中度在完全均匀分布时接近 0`() {
        assertEquals(0.0, ReadingStatsCalc.concentration(listOf(5, 5, 5, 5)), 0.001)
    }

    @Test
    fun `集中度随分布倾斜而升高`() {
        val balanced = ReadingStatsCalc.concentration(listOf(4, 4, 4, 4))
        val skewed = ReadingStatsCalc.concentration(listOf(10, 2, 2, 2))
        assertTrue(skewed > balanced)
    }

    @Test
    fun `无打开记录时集中度为 0 而不是除零`() {
        assertEquals(0.0, ReadingStatsCalc.concentration(emptyList()), 0.001)
        assertEquals(0.0, ReadingStatsCalc.concentration(listOf(0, 0)), 0.001)
    }
}
