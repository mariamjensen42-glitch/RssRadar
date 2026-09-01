package com.cycling.rssradar.data.store

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 新文章通知的总开关（#31）。默认关：通知是打扰型能力，必须用户主动开。
 * Android 13+ 还需运行时权限 POST_NOTIFICATIONS，由设置页在开启时请求；
 * 权限没给时发送侧静默跳过（见 NotificationHelper.postNewArticles）。
 *
 * Feed 级开关在 `feeds.notificationsEnabled`（DB v10），两道开关都开才发通知。
 */
class NotificationStore(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val state: StateFlow<Boolean> = _state.asStateFlow()

    fun set(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _state.value = enabled
    }

    companion object {
        private const val KEY_ENABLED = "notify_new_articles_enabled"
    }
}
