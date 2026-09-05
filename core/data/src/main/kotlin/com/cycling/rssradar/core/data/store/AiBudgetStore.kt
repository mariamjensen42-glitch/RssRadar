package com.cycling.rssradar.core.data.store

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.TimeZone


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
 * 存在的理由：35 项功能里 32 项要调大模型，**不限流的 AI 功能等于让用户开着水龙头睡觉**。
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


class AiBudgetStore(
    private val prefs: SharedPreferences,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zoneOffsetAt: (Long) -> Int = { TimeZone.getDefault().getOffset(it) },
) {

    private val _state = MutableStateFlow(readPersisted())
    val state: StateFlow<AiBudgetState> = _state.asStateFlow()

    /** 取状态前先做一次跨天归零——否则跨过零点后仍在昨天的计数上累加。 */
    fun current(): AiBudgetState {
        rollDayIfNeeded()
        return _state.value
    }

    fun hasBudget(): Boolean = current().hasBudget

    /**
     * 记一次调用。
     * @param success false 表示调用失败（网络/API 错误）——失败的调用同样占额度，
     *                否则一个反复失败的任务会把当天的额度烧穿。
     */
    fun record(inputChars: Int, outputChars: Int, success: Boolean) {
        rollDayIfNeeded()
        val now = _state.value
        val next = now.copy(
            usedToday = now.usedToday + 1,
            failedToday = now.failedToday + if (success) 0 else 1,
            inputCharsToday = now.inputCharsToday + inputChars,
            outputCharsToday = now.outputCharsToday + outputChars,
            totalCalls = now.totalCalls + 1,
            totalFailed = now.totalFailed + if (success) 0 else 1,
            totalInputChars = now.totalInputChars + inputChars,
            totalOutputChars = now.totalOutputChars + outputChars,
        )
        persist(next)
    }

    fun setDailyLimit(limit: Int) {
        val next = current().copy(dailyLimit = limit.coerceAtLeast(0))
        persist(next)
    }

    fun setConcurrentLimit(limit: Int) {
        val next = current().copy(
            concurrentLimit = limit.coerceIn(AiBudgetState.MIN_CONCURRENT, AiBudgetState.MAX_CONCURRENT),
        )
        persist(next)
    }

    fun setMinIntervalMs(millis: Long) {
        val next = current().copy(minIntervalMs = millis.coerceIn(0L, 30_000L))
        persist(next)
    }

    /** 重置今日计数（不含累计）。用量页的「今天重来」——调高上限后不用等明天。 */
    fun resetToday() {
        val next = current().copy(
            usedToday = 0,
            failedToday = 0,
            inputCharsToday = 0L,
            outputCharsToday = 0L,
        )
        persist(next)
    }

    /** 清空全部统计（含累计）。 */
    fun resetAll() {
        prefs.edit().remove(KEY_DAY_INDEX)
            .remove(KEY_USED_TODAY).remove(KEY_FAILED_TODAY)
            .remove(KEY_INPUT_TODAY).remove(KEY_OUTPUT_TODAY)
            .remove(KEY_TOTAL_CALLS).remove(KEY_TOTAL_FAILED)
            .remove(KEY_TOTAL_INPUT).remove(KEY_TOTAL_OUTPUT)
            .apply()
        _state.value = readPersisted()
    }

    private fun rollDayIfNeeded() {
        val now = clock()
        val today = AiDayIndex.indexOf(now, zoneOffsetAt(now))
        if (today != _state.value.dayIndex) {
            _state.value = _state.value.copy(
                dayIndex = today,
                usedToday = 0,
                failedToday = 0,
                inputCharsToday = 0L,
                outputCharsToday = 0L,
            )
        }
    }

    private fun persist(state: AiBudgetState) {
        prefs.edit()
            .putInt(KEY_DAILY_LIMIT, state.dailyLimit)
            .putInt(KEY_CONCURRENT, state.concurrentLimit)
            .putLong(KEY_MIN_INTERVAL, state.minIntervalMs)
            .putLong(KEY_DAY_INDEX, state.dayIndex)
            .putInt(KEY_USED_TODAY, state.usedToday)
            .putInt(KEY_FAILED_TODAY, state.failedToday)
            .putLong(KEY_INPUT_TODAY, state.inputCharsToday)
            .putLong(KEY_OUTPUT_TODAY, state.outputCharsToday)
            .putLong(KEY_TOTAL_CALLS, state.totalCalls)
            .putLong(KEY_TOTAL_FAILED, state.totalFailed)
            .putLong(KEY_TOTAL_INPUT, state.totalInputChars)
            .putLong(KEY_TOTAL_OUTPUT, state.totalOutputChars)
            .apply()
        _state.value = state
    }

    private fun readPersisted(): AiBudgetState {
        val now = clock()
        val today = AiDayIndex.indexOf(now, zoneOffsetAt(now))
        val storedDay = prefs.getLong(KEY_DAY_INDEX, today)
        // 存的不是今天 → 今日计数一律作废，累计保留。
        val sameDay = storedDay == today
        return AiBudgetState(
            dailyLimit = prefs.getInt(KEY_DAILY_LIMIT, AiBudgetState.DEFAULT_DAILY_LIMIT),
            concurrentLimit = prefs.getInt(KEY_CONCURRENT, AiBudgetState.DEFAULT_CONCURRENT)
                .coerceIn(AiBudgetState.MIN_CONCURRENT, AiBudgetState.MAX_CONCURRENT),
            minIntervalMs = prefs.getLong(KEY_MIN_INTERVAL, AiBudgetState.DEFAULT_MIN_INTERVAL_MS),
            dayIndex = today,
            usedToday = if (sameDay) prefs.getInt(KEY_USED_TODAY, 0) else 0,
            failedToday = if (sameDay) prefs.getInt(KEY_FAILED_TODAY, 0) else 0,
            inputCharsToday = if (sameDay) prefs.getLong(KEY_INPUT_TODAY, 0L) else 0L,
            outputCharsToday = if (sameDay) prefs.getLong(KEY_OUTPUT_TODAY, 0L) else 0L,
            totalCalls = prefs.getLong(KEY_TOTAL_CALLS, 0L),
            totalFailed = prefs.getLong(KEY_TOTAL_FAILED, 0L),
            totalInputChars = prefs.getLong(KEY_TOTAL_INPUT, 0L),
            totalOutputChars = prefs.getLong(KEY_TOTAL_OUTPUT, 0L),
        )
    }

    companion object {
        private const val KEY_DAILY_LIMIT = "ai_budget_daily_limit"
        private const val KEY_CONCURRENT = "ai_budget_concurrent"
        private const val KEY_MIN_INTERVAL = "ai_budget_min_interval"
        private const val KEY_DAY_INDEX = "ai_budget_day_index"
        private const val KEY_USED_TODAY = "ai_budget_used_today"
        private const val KEY_FAILED_TODAY = "ai_budget_failed_today"
        private const val KEY_INPUT_TODAY = "ai_budget_input_today"
        private const val KEY_OUTPUT_TODAY = "ai_budget_output_today"
        private const val KEY_TOTAL_CALLS = "ai_budget_total_calls"
        private const val KEY_TOTAL_FAILED = "ai_budget_total_failed"
        private const val KEY_TOTAL_INPUT = "ai_budget_total_input"
        private const val KEY_TOTAL_OUTPUT = "ai_budget_total_output"
    }
}
