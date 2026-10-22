package com.cycling.rssradar.core.data.store.prefs

import android.content.SharedPreferences
import com.cycling.rssradar.core.model.FeedValueMode
import com.cycling.rssradar.core.model.ListDescMode
import com.cycling.rssradar.core.model.ListDisplayState
import com.cycling.rssradar.core.model.ListViewMode
import com.cycling.rssradar.core.model.enumValueOrNull
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
            .putString(KEY_VALUE_MODE, next.valueMode.name)
            .apply()
        _state.value = next
    }

    private fun readPersisted(): ListDisplayState = ListDisplayState(
        showFeedIcon = prefs.getBoolean(KEY_FEED_ICON, true),
        showFeedName = prefs.getBoolean(KEY_FEED_NAME, true),
        showDate = prefs.getBoolean(KEY_DATE, true),
        showThumbnail = prefs.getBoolean(KEY_THUMBNAIL, true),
        descMode = enumValueOrNull<ListDescMode>(prefs.getString(KEY_DESC_MODE, null))
            ?: ListDescMode.SHORT,
        stickyDateHeader = prefs.getBoolean(KEY_STICKY_DATE, true),
        dimRead = prefs.getBoolean(KEY_DIM_READ, false),
        markReadOnScroll = prefs.getBoolean(KEY_MARK_READ_ON_SCROLL, false),
        viewMode = enumValueOrNull<ListViewMode>(prefs.getString(KEY_VIEW_MODE, null))
            ?: ListViewMode.CARD,
        valueMode = readValueMode(prefs),
    )

    /**
     * 读档位。上一版存的是布尔 `list_sort_by_value`，读到 true 就升成「按信息价值」——
     * 不读它等于把用户刚设过的偏好悄悄抹掉。
     */
    private fun readValueMode(prefs: SharedPreferences): FeedValueMode {
        enumValueOrNull<FeedValueMode>(prefs.getString(KEY_VALUE_MODE, null))
            ?.let { return it }
        return if (prefs.getBoolean(KEY_SORT_BY_VALUE, false)) FeedValueMode.BY_VALUE else FeedValueMode.OFF
    }

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
        private const val KEY_VALUE_MODE = "list_value_mode"

        /** 上一版的布尔键：只在迁移时读一次。 */
        private const val KEY_SORT_BY_VALUE = "list_sort_by_value"
    }
}
