package com.cycling.rssradar.core.data.store.prefs

import android.content.SharedPreferences
import com.cycling.rssradar.core.data.store.model.ListDescMode
import com.cycling.rssradar.core.data.store.model.ListDisplayState
import com.cycling.rssradar.core.data.store.model.ListViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 列表显示项偏好持久化 + 运行态共享（与 [ReadingPrefsStore] 同款模式）。
 * 设置页改开关 → StateFlow 更新 → 主题宿主注入的 CompositionLocal 跟着重组，即改即见。
 */
class ListDisplayStore(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(readPersisted())
    val state: StateFlow<ListDisplayState> = _state.asStateFlow()

    fun update(transform: (ListDisplayState) -> ListDisplayState) {
        val next = transform(_state.value)
        prefs.edit()
            .putBoolean(KEY_FEED_ICON, next.showFeedIcon)
            .putBoolean(KEY_FEED_NAME, next.showFeedName)
            .putBoolean(KEY_DATE, next.showDate)
            .putBoolean(KEY_THUMBNAIL, next.showThumbnail)
            .putString(KEY_DESC_MODE, next.descMode.name)
            .putBoolean(KEY_STICKY_DATE, next.stickyDateHeader)
            .putBoolean(KEY_DIM_READ, next.dimRead)
            .putBoolean(KEY_MARK_READ_ON_SCROLL, next.markReadOnScroll)
            .putString(KEY_VIEW_MODE, next.viewMode.name)
            .apply()
        _state.value = next
    }

    private fun readPersisted(): ListDisplayState = ListDisplayState(
        showFeedIcon = prefs.getBoolean(KEY_FEED_ICON, true),
        showFeedName = prefs.getBoolean(KEY_FEED_NAME, true),
        showDate = prefs.getBoolean(KEY_DATE, true),
        showThumbnail = prefs.getBoolean(KEY_THUMBNAIL, true),
        descMode = prefs.getString(KEY_DESC_MODE, null)
            ?.let { name -> runCatching { ListDescMode.valueOf(name) }.getOrNull() }
            ?: ListDescMode.SHORT,
        stickyDateHeader = prefs.getBoolean(KEY_STICKY_DATE, true),
        dimRead = prefs.getBoolean(KEY_DIM_READ, false),
        markReadOnScroll = prefs.getBoolean(KEY_MARK_READ_ON_SCROLL, false),
        viewMode = prefs.getString(KEY_VIEW_MODE, null)
            ?.let { name -> runCatching { ListViewMode.valueOf(name) }.getOrNull() }
            ?: ListViewMode.CARD,
    )

    companion object {
        private const val KEY_FEED_ICON = "list_show_feed_icon"
        private const val KEY_FEED_NAME = "list_show_feed_name"
        private const val KEY_DATE = "list_show_date"
        private const val KEY_THUMBNAIL = "list_show_thumbnail"
        private const val KEY_DESC_MODE = "list_desc_mode"
        private const val KEY_STICKY_DATE = "list_sticky_date_header"
        private const val KEY_DIM_READ = "list_dim_read"
        private const val KEY_MARK_READ_ON_SCROLL = "list_mark_read_on_scroll"
        private const val KEY_VIEW_MODE = "list_view_mode"
    }
}
