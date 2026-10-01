package com.cycling.rssradar.core.data.backup

import android.content.Context
import android.content.SharedPreferences
import com.cycling.rssradar.core.data.store.SettingsPrefs

/**
 * 设置的备份快照。只读 `SettingsPrefs.of`（rssradar_settings）——
 * **绝不碰 `ofSecret`**（rssradar_secrets 存的是 AI API Key），备份文件可能被随手分享出去。
 */
class SettingsSnapshot(private val context: Context) {

    fun read(): Map<String, String> =
        SettingsPrefs.of(context).all.mapValues { (_, value) -> encode(value) }

    fun apply(values: Map<String, String>) {
        val editor = SettingsPrefs.of(context).edit()
        values.forEach { (key, raw) -> put(editor, key, raw) }
        editor.apply()
    }

    private fun put(editor: SharedPreferences.Editor, key: String, raw: String) {
        val body = raw.substringAfter(':', "")
        when (raw.substringBefore(':', "")) {
            TYPE_BOOL -> editor.putBoolean(key, body.toBoolean())
            TYPE_INT -> body.toIntOrNull()?.let { editor.putInt(key, it) }
            TYPE_LONG -> body.toLongOrNull()?.let { editor.putLong(key, it) }
            TYPE_FLOAT -> body.toFloatOrNull()?.let { editor.putFloat(key, it) }
            else -> editor.putString(key, body)
        }
    }

    private fun encode(value: Any?): String = when (value) {
        null -> "$TYPE_STRING:"
        is Boolean -> "$TYPE_BOOL:$value"
        is Int -> "$TYPE_INT:$value"
        is Long -> "$TYPE_LONG:$value"
        is Float -> "$TYPE_FLOAT:$value"
        else -> "$TYPE_STRING:$value"
    }

    companion object {
        private const val TYPE_BOOL = "b"
        private const val TYPE_INT = "i"
        private const val TYPE_LONG = "l"
        private const val TYPE_FLOAT = "f"
        private const val TYPE_STRING = "s"
    }
}
