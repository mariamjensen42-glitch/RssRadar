package com.cycling.rssradar.core.data.store

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


/** 主题偏好：跟随系统 / 强制浅色 / 强制深色。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 主题偏好持久化 + 运行态共享。
 * 用 StateFlow 让设置页与主题宿主共享同一份状态：
 * 设置页改 mode → flow 更新 → 宿主重组换主题。
 */
class ThemeStore(private val prefs: SharedPreferences) {

    private val _mode = MutableStateFlow(readPersistedMode())
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    /**
     * Material You 动态取色（对照表 #27）：**只换强调色**，表面阶梯仍用自有色板。
     * 默认关：整套换成 Monet 会让「RssRadar 长什么样」这件事消失，且老用户升级
     * 视觉突变。要跟随壁纸的人显式开一次即可。
     */
    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    fun setMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _mode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _dynamicColor.value = enabled
    }

    private fun readPersistedMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM)
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "theme_dynamic_color"
    }
}
