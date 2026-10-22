package com.cycling.rssradar.core.model


/**
 * 「今日」的序号：本地时区下的自然日编号。
 *
 * 用整除而不是日期格式化库：批处理只需要「跨没跨天」这一个判定，
 * 拿 `SimpleDateFormat` 或 `java.time` 是为了算自然日而引入时区与 Locale 两套可变状态，
 * 纯算术则可注入、可断言。时区偏移由调用方传入，测试里塞 0 就是 UTC。
 */
object AiDayIndex {
    const val MS_PER_DAY = 24 * 60 * 60 * 1000L

    fun indexOf(millis: Long, zoneOffsetMillis: Int): Long {
        val shifted = millis + zoneOffsetMillis
        // 向下取整的整除：普通 `/` 对负数会向零取整，跨 1970 前的时刻会算错一天。
        return if (shifted >= 0) shifted / MS_PER_DAY else (shifted - MS_PER_DAY + 1) / MS_PER_DAY
    }
}

/**
 * AI 调用预算与用量统计。
 *
 * 存在的理由：16 项功能里 10 项要调大模型，**不限流的 AI 功能等于让用户开着水龙头睡觉**。
 * 三道闸都在这里：日调用上限（配额）、并发上限（不把连接池打满）、最小间隔（不触发服务端限流）。
 *
 * **只统计次数与字数，不换算金额**——DeepSeek 的单价随时调整，
 * 硬编码一个系数就是给用户一个看起来精确实则过期的数字，与本项目「数字必须真实」的原则冲突。
 * 要看钱，用户拿字数去官方账单对。
 */
data class AiBudgetState(
    /** 每日调用上限。0 = 不限（交给用户自己判断风险）。 */
    val dailyLimit: Int = DEFAULT_DAILY_LIMIT,
    /** 允许同时在飞的请求数。 */
    val concurrentLimit: Int = DEFAULT_CONCURRENT,
    /** 两次调用之间的最小间隔，防止短时突发被服务端限流。 */
    val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    /** 统计归属的自然日（[AiDayIndex]）。跨天后本组计数归零。 */
    val dayIndex: Long = 0,
    val usedToday: Int = 0,
    val failedToday: Int = 0,
    val inputCharsToday: Long = 0L,
    val outputCharsToday: Long = 0L,
    val totalCalls: Long = 0L,
    val totalFailed: Long = 0L,
    val totalInputChars: Long = 0L,
    val totalOutputChars: Long = 0L,
) {
    /** 今日剩余额度。不限时返回 Int.MAX_VALUE，让比较逻辑不必分支。 */
    val remainingToday: Int
        get() = if (dailyLimit <= 0) Int.MAX_VALUE else (dailyLimit - usedToday).coerceAtLeast(0)

    val hasBudget: Boolean get() = remainingToday > 0

    /** 今日失败率，用量页展示；无调用时为 0 而不是 NaN。 */
    val failureRateToday: Double
        get() = if (usedToday == 0) 0.0 else failedToday.toDouble() / usedToday

    companion object {
        /**
         * 默认日上限 200 次。取这个数：日常批处理（每天新文章约几十篇 × 已开启的功能数）
         * 加上手动问答与翻译，200 足够用；真到 200 说明开着的功能太多，用户该去看用量页了。
         */
        const val DEFAULT_DAILY_LIMIT = 200

        /** 默认并发 2：单线程串行太慢，超过 3 对一个手机 App 的后台任务没有意义，还更容易被限流。 */
        const val DEFAULT_CONCURRENT = 2

        /** 默认最小间隔 1.2 秒。 */
        const val DEFAULT_MIN_INTERVAL_MS = 1_200L

        const val MIN_CONCURRENT = 1
        const val MAX_CONCURRENT = 8
    }
}

/** 当前开启的 AI 功能集合。用 Set 而不是 33 个布尔字段的数据类——增删功能不用改这里。 */
data class AiFeatureSettings(
    val enabled: Set<AiFeature> = AiFeature.DEFAULT_ENABLED,
) {
    fun isEnabled(feature: AiFeature): Boolean = feature in enabled

    companion object
}
