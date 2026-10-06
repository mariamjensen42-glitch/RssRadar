package com.cycling.rssradar.core.data.store.prefs

import android.content.SharedPreferences

/**
 * DeepSeek API Key 存储（issue #44，ADR-0005）。
 * SharedPreferences，对标 [com.cycling.rssradar.core.data.rsshub.RssHubInstanceStore]：
 * 用户自备 Key，成本与额度由用户掌控，无内置 Key 分支。
 */
class AiStore(private val prefs: SharedPreferences) {

    /** DeepSeek API Key。null/空 = 未配置，AI 功能引导去「我的」页设置。 */
    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_API_KEY, value?.trim()?.ifBlank { null }).apply()
        }

    fun hasKey(): Boolean = apiKey != null

    companion object {
        private const val KEY_API_KEY = "deepseek_api_key"

        /**
         * 一次性迁移：Key 原先与普通设置共用 `rssradar_settings`，而那份文件会随
         * Google 云备份上传。搬到独立的 secrets 文件后，旧文件里的值必须立即抹掉——
         * 只在旧位置留着就等于没搬。
         */
        fun migrateFromLegacy(legacy: SharedPreferences, target: SharedPreferences) {
            val old = legacy.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() } ?: return
            if (target.getString(KEY_API_KEY, null).isNullOrBlank()) {
                target.edit().putString(KEY_API_KEY, old).apply()
            }
            legacy.edit().remove(KEY_API_KEY).apply()
        }
    }
}
