package com.cycling.rssradar.core.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/** 统计仪表盘口径的装配测试：窗口、时段、streak、集中度各断言一条主路径。 */
class ReadingStatsDashboardTest {

    private val dayMs = ReadingStatsDashboard.DAY_MS
    private val now = 1_800_000_000_000L
    private val offset = 0

    @Test
    fun `window only counts opens within the last 7 days`() {
        val inputs = ReadingStatsDashboard.Inputs(
            now = now,
            zoneOffsetMillis = offset,
            windowCnt = 12,
            windowMinutes = 45L,
            allOpened = listOf(now, now - dayMs, now - 8 * dayMs), // 8 天前的不进时段
            openedCountsByFeed = listOf(3, 3),
            topFeeds = listOf(TopFeed("A", 3), TopFeed("B", 3)),
        )

        val summary = ReadingStatsDashboard.assemble(inputs)

        assertEquals(12, summary.weekOpens)
        assertEquals(45L, summary.weekMinutes)
        assertEquals(2, summary.streakDays) // 今天 + 昨天
    }

    @Test
    fun `streak survives today not opened yet`() {
        val inputs = ReadingStatsDashboard.Inputs(
            now = now,
            zoneOffsetMillis = offset,
            windowCnt = 2,
            windowMinutes = null,
            allOpened = listOf(now - dayMs, now - 2 * dayMs), // 今天还没打开
            openedCountsByFeed = listOf(1, 1),
            topFeeds = emptyList(),
        )

        val summary = ReadingStatsDashboard.assemble(inputs)

        assertEquals(2, summary.streakDays)
        assertEquals(0L, summary.weekMinutes) // minutes 为 null 如实归零
    }

    @Test
    fun `concentration delegates to normalized hhi`() {
        val inputs = ReadingStatsDashboard.Inputs(
            now = now,
            zoneOffsetMillis = offset,
            windowCnt = 0,
            windowMinutes = null,
            allOpened = emptyList(),
            openedCountsByFeed = listOf(9, 1),
            topFeeds = emptyList(),
        )

        val summary = ReadingStatsDashboard.assemble(inputs)

        // 9:1 明显偏斜，归一化 HHI 应显著大于 0
        assert(summary.concentration > 0.5) { "expected skewed, got ${summary.concentration}" }
    }
}
