package com.cycling.rssradar.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.LocaleList
import com.cycling.rssradar.core.data.store.AppLanguage
import com.cycling.rssradar.core.data.store.SettingsPrefs
import java.util.Locale

/**
 * 界面语言的应用层（ADR-0017）。
 *
 * 单一真相源是 [SettingsPrefs] 里的 `app_language`（LanguageStore 持久化）。
 * - API 33+：把 locale 推给系统 [LocaleManager]（per-app locale），系统自动重建
 *   全部 Activity，stringResource / 日期格式 / WebView Accept-Language 全部跟随。
 * - API 31/32：无系统级 per-app locale，[wrapContext] 在 Activity attach 时用
 *   createConfigurationContext 覆盖，改语言后手动 [recreate] 当前 Activity。
 *
 * 资源解析规则：中文文案在 values/（默认），英文在 values-en/。
 * 选 ENGLISH 而资源缺英文时回退中文（资源结构不完整时的安全网）。
 */
object AppLocales {

    /** 从持久化读当前偏好（attachBaseContext 阶段 Hilt 还没起来，直接读 prefs）。 */
    fun persisted(prefs: SharedPreferences): AppLanguage {
        val name = prefs.getString("app_language", null) ?: return AppLanguage.SYSTEM
        return runCatching { AppLanguage.valueOf(name) }.getOrDefault(AppLanguage.SYSTEM)
    }

    /** 覆盖 locale 用：SYSTEM 走系统默认，其余强制对应 locale。 */
    fun localeOf(language: AppLanguage): Locale = when (language) {
        AppLanguage.SYSTEM -> Locale.getDefault()
        AppLanguage.CHINESE -> Locale.SIMPLIFIED_CHINESE
        AppLanguage.ENGLISH -> Locale.ENGLISH
    }

    /**
     * 应用语言变更。必须在持久化（LanguageStore.setLanguage）之后调用。
     * - API 33+：推给 LocaleManager，系统负责重建，不需要手动 recreate。
     * - 更低版本：调用方对返回 true 的场景自行 recreate 当前 Activity。
     */
    fun apply(context: Context, language: AppLanguage): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val manager = context.getSystemService(LocaleManager::class.java) ?: return false
        val locales = when (language) {
            AppLanguage.SYSTEM -> LocaleList.getEmptyLocaleList()
            AppLanguage.CHINESE -> LocaleList(Locale.SIMPLIFIED_CHINESE)
            AppLanguage.ENGLISH -> LocaleList(Locale.ENGLISH)
        }
        manager.applicationLocales = locales
        return false
    }

    /** Activity attachBaseContext 覆盖（仅 API 31/32 生效；33+ 系统已接管）。 */
    fun wrapContext(context: Context, prefs: SharedPreferences): Context {
        val language = persisted(prefs)
        if (language == AppLanguage.SYSTEM) return context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
        val locale = localeOf(language)
        val config = context.resources.configuration
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }
}
