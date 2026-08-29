package com.cycling.rssradar.data.store

import android.content.Context
import com.cycling.rssradar.data.db.DEFAULT_GROUP
import com.cycling.rssradar.data.db.GROUP_TECH
import com.cycling.rssradar.data.db.GROUP_DEV
import com.cycling.rssradar.data.db.GROUP_DESIGN


/**
 * 分组注册表：分组名清单（SharedPreferences）。
 *
 * 分组不是独立表，而是 feeds.groupName 字符串 + 这份注册表：
 * - 注册表保证「空分组」也存在（没有任何 feed 也能显示/管理）
 * - 创建 = 注册表加名字；删除 = 注册表删名字 + 该组 feed 移回默认组
 * - 重命名 = 注册表改名 + feeds.groupName 批量更新
 */
class GroupStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // 首次运行：写入默认分组，保证注册表非空、UI 总有分组可显示
        if (!prefs.contains(KEY_GROUPS)) {
            prefs.edit()
                .putString(KEY_GROUPS, listOf(DEFAULT_GROUP, GROUP_TECH, GROUP_DEV, GROUP_DESIGN).joinToString(GROUP_SEPARATOR))
                .apply()
        }
    }

    fun getGroups(): List<String> =
        (prefs.getString(KEY_GROUPS, null) ?: "").split(GROUP_SEPARATOR).filter { it.isNotBlank() }

    fun addGroup(name: String): Boolean {
        val clean = name.trim()
        if (clean.isBlank() || getGroups().contains(clean)) return false
        val next = (getGroups() + clean).distinct()
        prefs.edit().putString(KEY_GROUPS, next.joinToString(GROUP_SEPARATOR)).apply()
        return true
    }

    fun renameGroup(old: String, new: String): Boolean {
        val clean = new.trim()
        if (clean.isBlank() || getGroups().contains(clean)) return false
        val next = getGroups().map { if (it == old) clean else it }
        prefs.edit().putString(KEY_GROUPS, next.joinToString(GROUP_SEPARATOR)).apply()
        return true
    }

    fun removeGroup(name: String) {
        val next = getGroups().filterNot { it == name }
        prefs.edit().putString(KEY_GROUPS, next.joinToString(GROUP_SEPARATOR)).apply()
    }

    companion object {
        private const val PREFS_NAME = "rssradar_settings"
        private const val KEY_GROUPS = "feed_groups"
        private const val GROUP_SEPARATOR = "\u001F"
    }
}
