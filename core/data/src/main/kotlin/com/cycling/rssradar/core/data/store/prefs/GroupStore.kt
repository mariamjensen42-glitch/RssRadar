package com.cycling.rssradar.core.data.store.prefs

import android.content.SharedPreferences
import androidx.core.content.edit
import com.cycling.rssradar.core.data.db.DEFAULT_GROUP
import com.cycling.rssradar.core.model.GROUP_DESIGN
import com.cycling.rssradar.core.model.GROUP_DEV
import com.cycling.rssradar.core.model.GROUP_TECH
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 分组注册表：分组名清单（SharedPreferences）。
 *
 * 分组不是独立表，而是 feeds.groupName 字符串 + 这份注册表：
 * - 注册表保证「空分组」也存在（没有任何 feed 也能显示/管理）
 * - 创建 = 注册表加名字；删除 = 注册表删名字 + 该组 feed 移回默认组
 * - 重命名 = 注册表改名 + feeds.groupName 批量更新
 */
class GroupStore(private val prefs: SharedPreferences) {

    private val _groups = MutableStateFlow(readGroups())
    val state: StateFlow<List<String>> = _groups.asStateFlow()

    init {
        // 首次运行：写入默认分组，保证注册表非空、UI 总有分组可显示
        if (!prefs.contains(KEY_GROUPS)) {
            prefs.edit {
                putString(
                    KEY_GROUPS,
                    listOf(DEFAULT_GROUP, GROUP_TECH, GROUP_DEV, GROUP_DESIGN).joinToString(
                        GROUP_SEPARATOR
                    )
                )
            }
            _groups.value = readGroups()
        }
    }

    private fun readGroups(): List<String> =
        (prefs.getString(KEY_GROUPS, null) ?: "").split(GROUP_SEPARATOR).filter { it.isNotBlank() }

    fun getGroups(): List<String> = _groups.value

    fun addGroup(name: String): Boolean {
        val clean = name.trim()
        if (clean.isBlank() || _groups.value.contains(clean)) return false
        persist(_groups.value + clean)
        return true
    }

    fun renameGroup(old: String, new: String): Boolean {
        val clean = new.trim()
        if (clean.isBlank() || _groups.value.contains(clean)) return false
        persist(_groups.value.map { if (it == old) clean else it })
        return true
    }

    fun removeGroup(name: String) {
        persist(_groups.value.filterNot { it == name })
    }

    /** 每次落盘后同步推给订阅者，否则各 ViewModel 只能拿到建 VM 那刻的快照，新建分组看不见。 */
    private fun persist(next: List<String>) {
        val distinct = next.distinct()
        prefs.edit { putString(KEY_GROUPS, distinct.joinToString(GROUP_SEPARATOR)) }
        _groups.value = distinct
    }

    companion object {
        private const val KEY_GROUPS = "feed_groups"
        private const val GROUP_SEPARATOR = "\u001F"
    }
}
