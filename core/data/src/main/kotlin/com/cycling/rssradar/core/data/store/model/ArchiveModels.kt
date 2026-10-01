package com.cycling.rssradar.core.data.store.model

/**
 * 归档保留档位（issue #57）。days = 保留天数，0 表示永久保留。
 * 纯 JVM 枚举，可被单测；label 供设置页直接展示。
 */
enum class KeepArchived(val days: Long, val label: String) {
    ALWAYS(0, "永久"),
    ONE_DAY(1, "1 天"),
    TWO_DAYS(2, "2 天"),
    THREE_DAYS(3, "3 天"),
    ONE_WEEK(7, "1 周"),
    TWO_WEEKS(14, "2 周"),
    ONE_MONTH(30, "1 个月"),
    ;

    /**
     * 归档截止时间戳：早于它的文章到期。ALWAYS 返回 null = 不清理。
     * 保留期基准与 DAO 的 COALESCE(publishedAt, fetchedAt) 一致。
     */
    fun cutoffMillis(nowMs: Long): Long? =
        if (days <= 0) null else nowMs - days * MILLIS_PER_DAY

    companion object {
        const val MILLIS_PER_DAY = 86_400_000L

        /** 持久化名反查：未知值回落 ALWAYS（宁可不删，不可误删）。 */
        fun fromNameOrNull(name: String?): KeepArchived? =
            name?.let { n -> entries.firstOrNull { it.name == n } }
    }
}
