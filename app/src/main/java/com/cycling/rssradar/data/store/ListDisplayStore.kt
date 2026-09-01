package com.cycling.rssradar.data.store

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 列表描述档位（issue #56）。NONE 隐藏摘要，SHORT 两行，LONG 四行。
 * label 供设置页直接展示，与 ReadingFontFamily 同款做法。
 */
enum class ListDescMode(val lines: Int, val label: String) {
    NONE(0, "关"),
    SHORT(2, "短"),
    LONG(4, "长"),
}

/**
 * 信息流列表显示项状态（issue #56）。纯数据类。
 * 默认值 = 功能引入前的固定渲染，升级无感知。
 */
data class ListDisplayState(
    val showFeedIcon: Boolean = true,
    val showFeedName: Boolean = true,
    val showDate: Boolean = true,
    val showThumbnail: Boolean = true,
    val descMode: ListDescMode = ListDescMode.SHORT,
    val stickyDateHeader: Boolean = false,
    val dimRead: Boolean = false,
    /**
     * 滚动时自动标记已读（#11）：卡片滚出视口顶部即标记为已读。
     * 默认关——这是会改变用户数据的行为，必须显式选择。
     */
    val markReadOnScroll: Boolean = false,
)

/**
 * 列表显示项偏好持久化 + 运行态共享（ReadingStyleStore 同款模式）。
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
        stickyDateHeader = prefs.getBoolean(KEY_STICKY_DATE, false),
        dimRead = prefs.getBoolean(KEY_DIM_READ, false),
        markReadOnScroll = prefs.getBoolean(KEY_MARK_READ_ON_SCROLL, false),
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
    }
}
