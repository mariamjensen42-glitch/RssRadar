package com.cycling.rssradar.core.domain.stats

import com.cycling.rssradar.core.domain.ai.AiReadingStats

/** 一条「最常打开的订阅源」行。刻意用本模块自己的类型，不让 domain 认识 Room。 */
data class TopFeed(val feedTitle: String, val opens: Int)

/**
 * 统计仪表盘的**口径唯一落点**（#83）。
 *
 * 之前近 7 天窗口、epoch day 手写除法、集中度口径散在 ReadingStatsViewModel 的 init 里，
 * 只能真机看；AI 报告那一侧（[AiReadingStats]）却有纯函数与测试。现在仪表盘与 AI 报告
 * 共享同一套算法，仪表盘的装配本身也成了 JVM 可断言的纯函数：
 * DAO 负责取数（输入参数），这里负责口径（全部数字来自 DB 真实计算，一个都不许编）。
 */
object ReadingStatsDashboard {

    /** 滑动窗口宽度：近 7 天。 */
    const val WINDOW_DAYS = 7L
    const val DAY_MS = 24 * 60 * 60 * 1000L
    const val TOP_FEED_LIMIT = 5

    data class Inputs(
        val now: Long,
        val zoneOffsetMillis: Int,
        /** 近窗口打开统计（cnt, minutes），来自 readingWindowStat。 */
        val windowCnt: Int,
        val windowMinutes: Long?,
        /** 全部历史打开时间戳（口径 lastOpenedAt，滑动标已读不算）。 */
        val allOpened: List<Long>,
        /** 每源打开次数（全部有打开的源，不是 top——top 算集中度必然虚高）。 */
        val openedCountsByFeed: List<Int>,
        /** 每源打开次数 + 标题，来自 topOpenedFeeds。 */
        val topFeeds: List<TopFeed>,
    )

    data class Summary(
        val weekOpens: Int,
        /** 估算阅读分钟合计——UI 必须标注「估算」（CONTEXT.md），不得表述为真实停留。 */
        val weekMinutes: Long,
        val activeHours: List<Int>,
        val topFeeds: List<TopFeed>,
        /** 归一化 HHI，0~1。 */
        val concentration: Double,
        val streakDays: Int,
    )

    fun assemble(inputs: Inputs): Summary {
        val since = inputs.now - WINDOW_DAYS * DAY_MS
        // 活跃时段只要近 7 天的样本；streak 要全部历史（断一天就断）
        val hours = AiReadingStats.activeHours(inputs.allOpened.filter { it >= since }, inputs.zoneOffsetMillis)
        // epoch day 手写除法（floorDiv 在 check-kotlin 下解析不到）：时间戳恒正，普通除法等价
        val dayKeys = inputs.allOpened.map { (it + inputs.zoneOffsetMillis) / DAY_MS }.toSet()
        val todayDay = (inputs.now + inputs.zoneOffsetMillis) / DAY_MS
        return Summary(
            weekOpens = inputs.windowCnt,
            weekMinutes = inputs.windowMinutes ?: 0L,
            activeHours = hours,
            topFeeds = inputs.topFeeds,
            concentration = AiReadingStats.concentration(inputs.openedCountsByFeed),
            streakDays = AiReadingStats.streakDays(dayKeys, todayDay),
        )
    }
}
