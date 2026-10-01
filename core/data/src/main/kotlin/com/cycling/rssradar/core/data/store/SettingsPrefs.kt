package com.cycling.rssradar.core.data.store

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

    /**
     * 敏感凭据专用文件，**明确排除在云备份之外**（见 res/xml/backup_rules.xml 与
     * data_extraction_rules.xml）。与普通设置分开存放的唯一理由就是这个：
     * 备份规则只能按文件粒度排除，而 DeepSeek API Key 不该被上传到用户的云端账号
     * （Google 云备份 + 旧机型 `adb backup` 都能把它整份导出）。
     */
    const val NAME_SECRETS = "rssradar_secrets"

    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun ofSecret(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME_SECRETS, Context.MODE_PRIVATE)
}
