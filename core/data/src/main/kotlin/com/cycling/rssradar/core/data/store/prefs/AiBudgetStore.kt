package com.cycling.rssradar.core.data.store.prefs

import android.content.SharedPreferences
import com.cycling.rssradar.core.data.store.model.AiBudgetState
import com.cycling.rssradar.core.data.store.model.AiDayIndex
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
