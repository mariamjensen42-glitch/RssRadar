package com.cycling.rssradar.core.data

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * 纯 JVM 内存版 [SharedPreferences] fake：Store 解耦 Context 后，
 * 设置逻辑（含 AutoSync 的时间戳写入）可在无 Android 环境下测试。
 * 只实现 Store 实际用到的读写；未实现的接口方法返回空实现。
 */
class FakeSharedPreferences : SharedPreferences {

    val map = ConcurrentHashMap<String, Any>()

    override fun getAll(): Map<String, *> = map.toMap()
    override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String> =
        @Suppress("UNCHECKED_CAST") (map[key] as? Set<String>)?.toMutableSet() ?: defValues ?: mutableSetOf()
    override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removed += key }
        override fun clear() = apply { clearAll = true }

        override fun commit(): Boolean { applyEdits(); return true }
        override fun apply() = applyEdits()

        private fun applyEdits() {
            if (clearAll) map.clear()
            removed.forEach { map.remove(it) }
            pending.forEach { (k, v) -> if (v != null) map[k] = v }
        }
    }
}
