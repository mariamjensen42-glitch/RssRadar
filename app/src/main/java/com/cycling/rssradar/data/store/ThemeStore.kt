package com.cycling.rssradar.data.store

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.cycling.rssradar.Context

/** 主题偏好：跟随系统 / 强制浅色 / 强制深色。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 主题偏好持久化 + 运行态共享。
 * 用 StateFlow 让设置页与主题宿主（MainActivity）共享同一份状态：
 * 设置页改 mode → flow 更新 → 宿主重组换主题。
 */
class ThemeStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(readPersisted())
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _mode.value = mode
    }

    private fun readPersisted(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM)
    }

    companion object {
        private const val PREFS_NAME = "rssradar_settings"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
