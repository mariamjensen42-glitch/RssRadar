package com.cycling.rssradar.data.store

/**
 * 「标记已读」的作用范围（ReadYou MarkAsReadConditions 的同名能力）。
 *
 * 时间基准与归档清理保持一致：文章可能没有发布时间（`publishedAt` 为 null，
 * 部分源不写 date），一律用 `COALESCE(publishedAt, fetchedAt)` 判定，
 * 免得无日期的文章永远标不掉。
 */
enum class MarkAsReadCondition(
    val label: String,
    /** 距今多少天；null = 不限时间（全部）。 */
    val days: Int?,
) {
    ONE_DAY("1 天前", 1),
    THREE_DAYS("3 天前", 3),
    SEVEN_DAYS("7 天前", 7),
    ALL("全部", null),
    ;

    /** 早于该时间戳的文章要被标记；ALL 返回 null（调用方走「全部已读」）。 */
    fun cutoffMillis(now: Long = System.currentTimeMillis()): Long? =
        days?.let { now - it * DAY_MILLIS }

    companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
