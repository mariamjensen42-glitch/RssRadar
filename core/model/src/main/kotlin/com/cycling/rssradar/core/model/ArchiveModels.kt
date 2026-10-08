package com.cycling.rssradar.core.model

/**
 * 归档保留档位（issue #57）。days = 保留天数，0 表示永久保留。
 * 纯 JVM 枚举，可被单测。
 */
enum class KeepArchived(val days: Long) {
    ALWAYS(0),
    ONE_DAY(1),
    TWO_DAYS(2),
    THREE_DAYS(3),
    ONE_WEEK(7),
    TWO_WEEKS(14),
    ONE_MONTH(30),
    ;

    /**
     * 归档截止时间戳：早于它的文章到期。ALWAYS 返回 null = 不清理。
     * 保留期基准与 DAO 的 COALESCE(publishedAt, fetchedAt) 一致。
     */
    fun cutoffMillis(nowMs: Long): Long? =
        if (days <= 0) null else nowMs - days * MILLIS_PER_DAY

    companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
