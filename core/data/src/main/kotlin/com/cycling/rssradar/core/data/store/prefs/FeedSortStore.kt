package com.cycling.rssradar.core.data.store.prefs

import android.content.SharedPreferences
import com.cycling.rssradar.core.model.FeedSortMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 订阅列表排序偏好持久化 + 运行态共享（与 [ListDisplayStore] 同款模式）。
 * 默认按名称（历史行为），升级无感知。
 */
class FeedSortStore(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(readPersisted())
    val state: StateFlow<FeedSortMode> = _state.asStateFlow()

    fun set(mode: FeedSortMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _state.value = mode
    }

    private fun readPersisted(): FeedSortMode =
        prefs.getString(KEY_MODE, null)
            ?.let { name -> runCatching { FeedSortMode.valueOf(name) }.getOrNull() }
            ?: FeedSortMode.BY_NAME

    companion object {
        private const val KEY_MODE = "feed_sort_mode"
    }
}
