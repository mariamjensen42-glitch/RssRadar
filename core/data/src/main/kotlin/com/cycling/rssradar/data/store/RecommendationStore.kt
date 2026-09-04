package com.cycling.rssradar.core.data.store

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 推荐流开关（ADR-0013）：关闭后信息流不显示「推荐」tab。
 *
 * 默认**开**：冷启动退化排序（按源轮转的最近未读）本身就有用，
 * 「没数据就隐藏」是功能没人发现的经典陷阱。开关有真实读取方——
 * FeedListScreen 据此决定是否渲染推荐 tab。
 */
class RecommendationStore(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val state: StateFlow<Boolean> = _state.asStateFlow()

    fun set(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _state.value = enabled
    }

    companion object {
        private const val KEY_ENABLED = "recommendation_enabled"
    }
}
