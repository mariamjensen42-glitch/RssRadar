package com.cycling.rssradar.data.store

import android.content.Context

/**
 * DeepSeek API Key 存储（issue #44，ADR-0005）。
 * SharedPreferences，对标 [com.cycling.rssradar.data.rsshub.RssHubInstanceStore]：
 * 用户自备 Key，成本与额度由用户掌控，无内置 Key 分支。
 */
class AiStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** DeepSeek API Key。null/空 = 未配置，AI 功能引导去「我的」页设置。 */
    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_API_KEY, value?.trim()?.ifBlank { null }).apply()
        }

    fun hasKey(): Boolean = apiKey != null

    companion object {
        private const val PREFS_NAME = "rssradar_settings"
        private const val KEY_API_KEY = "deepseek_api_key"
    }
}
