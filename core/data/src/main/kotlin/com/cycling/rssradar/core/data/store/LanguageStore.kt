package com.cycling.rssradar.core.data.store

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 界面语言偏好：跟随系统 / 中文 / English。 */
enum class AppLanguage { SYSTEM, CHINESE, ENGLISH }

/**
 * 界面语言持久化 + 运行态共享（ADR-0017）。
 * 与 [ThemeStore] 同构：设置页改值 → flow 更新 → 应用层把 locale 推给系统并重建界面。
 * 注意：这只管「选了哪种语言」，真正的 locale 应用在 app 模块 i18n/AppLocales——
 * core 层不碰 Activity / LocaleManager。
 */
class LanguageStore(private val prefs: SharedPreferences) {

    private val _language = MutableStateFlow(readPersisted())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, language.name).apply()
        _language.value = language
    }

    private fun readPersisted(): AppLanguage {
        val name = prefs.getString(KEY_APP_LANGUAGE, null) ?: return AppLanguage.SYSTEM
        return runCatching { AppLanguage.valueOf(name) }.getOrDefault(AppLanguage.SYSTEM)
    }

    companion object {
        private const val KEY_APP_LANGUAGE = "app_language"
    }
}
