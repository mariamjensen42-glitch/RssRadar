package com.cycling.rssradar.core.data.store.prefs

import android.content.SharedPreferences
import com.cycling.rssradar.core.model.ThemeMode
import com.cycling.rssradar.core.model.enumValueOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 主题偏好持久化 + 运行态共享。
 * 用 StateFlow 让设置页与主题宿主共享同一份状态：
 * 设置页改 mode → flow 更新 → 宿主重组换主题。
 */
class ThemeStore(private val prefs: SharedPreferences) {

    private val _mode = MutableStateFlow(readPersistedMode())
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    /**
     * Material You 动态取色（对照表 #27）：整套 M3 色板跟随系统壁纸。
     *
     * 默认**开**（2026-10-03）。关掉等于退回 M3 基线色板（紫调）。
     * 非 Android 12 设备上本开关无效（见 supportsDynamicColor），那时也走基线色板。
     */
    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    /**
     * 自定义强调色 ARGB（对照表 #29）；null = 用默认紫。
     * 与 [dynamicColor] 互斥：自定义优先，两个来源同时存在时「到底哪个生效」无法解释。
     */
    private val _customAccent = MutableStateFlow(readPersistedAccent())
    val customAccent: StateFlow<Long?> = _customAccent.asStateFlow()

    fun setMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _mode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _dynamicColor.value = enabled
    }

    /** 传 null 表示回到默认紫。 */
    fun setCustomAccent(argb: Long?) {
        val editor = prefs.edit()
        if (argb == null) editor.remove(KEY_CUSTOM_ACCENT) else editor.putLong(KEY_CUSTOM_ACCENT, argb)
        editor.apply()
        _customAccent.value = argb
    }

    private fun readPersistedMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return enumValueOrNull<ThemeMode>(name) ?: ThemeMode.SYSTEM
    }

    private fun readPersistedAccent(): Long? {
        if (!prefs.contains(KEY_CUSTOM_ACCENT)) return null
        return prefs.getLong(KEY_CUSTOM_ACCENT, 0L)
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "theme_dynamic_color"
        private const val KEY_CUSTOM_ACCENT = "theme_custom_accent"
    }
}
