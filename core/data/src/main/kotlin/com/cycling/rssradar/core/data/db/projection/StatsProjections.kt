package com.cycling.rssradar.core.data.db.projection

/** 订阅 + 未读数（每条结果用 Flow 汇总）。 */
data class FeedUnreadCount(
    val feedId: Long,
    val cnt: Int,
)

/** 订阅源最近一篇文章的时间戳（订阅列表「按最近更新」排序用）。 */
data class FeedLatestTime(
    val feedId: Long,
    val latest: Long,
)

/** 近 7 天阅读窗口统计（#83 统计仪表盘）：打开篇数 + 估算阅读分钟合计。 */
data class ReadingWindowStat(
    val cnt: Int,
    /** SUM 忽略 readingMinutes 为 null 的行；无样本时为 null，UI 按 0 展示。 */
    val minutes: Long?,
)

/** Top 打开源（#83）：近 7 天打开篇数最多的订阅源。 */
data class FeedOpenStat(
    val feedTitle: String,
    val cnt: Int,
)

/** 按站点聚合：总数 / 失败数 / 不完整数。诊断页的分组统计直接吃这个。 */
data class FetchHostStat(
    val host: String,
    val total: Int,
    val failures: Int,
    val incomplete: Int,
)
