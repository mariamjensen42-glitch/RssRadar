package com.cycling.rssradar.core.data.store

import android.content.SharedPreferences
import com.cycling.rssradar.core.domain.notify.NotifyPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 新文章通知的完整偏好（#31 总开关 + 勿扰时段 + 关键词）。
 *
 * 总开关默认关：通知是打扰型能力，必须用户主动开。Android 13+ 还需运行时权限，
 * 由设置页在开启时请求；权限没给时发送侧静默跳过。
 * 判定链的规则全部收在 core/domain 的 [com.cycling.rssradar.core.domain.notify.NotifyDecision]，
 * 本类只负责存取——两处各写一半判定，日后必然对不上。
 */
class NotificationStore(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<NotifyPrefs> = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) = write(_state.value.copy(enabled = enabled))

    fun setDnd(startMinute: Int?, endMinute: Int?) =
        write(_state.value.copy(dndStartMinute = startMinute, dndEndMinute = endMinute))

    fun setKeywords(include: List<String>, exclude: List<String>) =
        write(_state.value.copy(includeKeywords = include, excludeKeywords = exclude))

    private fun read(): NotifyPrefs = NotifyPrefs(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        dndStartMinute = prefs.getInt(KEY_DND_START, NO_MINUTE).takeIf { it != NO_MINUTE },
        dndEndMinute = prefs.getInt(KEY_DND_END, NO_MINUTE).takeIf { it != NO_MINUTE },
        includeKeywords = split(prefs.getString(KEY_INCLUDE, null)),
        excludeKeywords = split(prefs.getString(KEY_EXCLUDE, null)),
    )

    private fun write(state: NotifyPrefs) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, state.enabled)
            .putInt(KEY_DND_START, state.dndStartMinute ?: NO_MINUTE)
            .putInt(KEY_DND_END, state.dndEndMinute ?: NO_MINUTE)
            .putString(KEY_INCLUDE, state.includeKeywords.joinToString(SEPARATOR))
            .putString(KEY_EXCLUDE, state.excludeKeywords.joinToString(SEPARATOR))
            .apply()
        _state.value = state
    }

    private fun split(raw: String?): List<String> =
        raw?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()

    companion object {
        private const val KEY_ENABLED = "notify_new_articles_enabled"
        private const val KEY_DND_START = "notify_dnd_start_minute"
        private const val KEY_DND_END = "notify_dnd_end_minute"
        private const val KEY_INCLUDE = "notify_keyword_include"
        private const val KEY_EXCLUDE = "notify_keyword_exclude"
        private const val NO_MINUTE = -1
        private const val SEPARATOR = "\u001F"
    }
}
