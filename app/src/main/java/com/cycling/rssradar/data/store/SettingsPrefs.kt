package com.cycling.rssradar.data.store

import android.content.Context
import android.content.SharedPreferences

/**
 * 全部设置 Store 共用的 SharedPreferences 文件（单一真相源）。
 *
 * Store 构造只吃 [SharedPreferences]，不吃 Context：Hilt 在装配点调 [of]，
 * JVM 测试直接塞内存实例（如 shadow / in-memory 实现），设置逻辑可离线验证。
 */
object SettingsPrefs {

    const val NAME = "rssradar_settings"

    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
