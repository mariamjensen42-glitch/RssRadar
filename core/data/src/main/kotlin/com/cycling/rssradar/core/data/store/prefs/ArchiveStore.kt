package com.cycling.rssradar.core.data.store.prefs

import android.content.SharedPreferences
import com.cycling.rssradar.core.data.store.model.KeepArchived
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 归档策略持久化 + 运行态共享（ListDisplayStore 同款模式，issue #57）。
 * 默认 ALWAYS：存量数据大，升级即删不可接受——清理必须 opt-in。
 */
class ArchiveStore(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(readPersisted())
    val state: StateFlow<KeepArchived> = _state.asStateFlow()

    fun set(keep: KeepArchived) {
        prefs.edit().putString(KEY_KEEP_ARCHIVED, keep.name).apply()
        _state.value = keep
    }

    private fun readPersisted(): KeepArchived =
        KeepArchived.fromNameOrNull(prefs.getString(KEY_KEEP_ARCHIVED, null)) ?: KeepArchived.ALWAYS

    companion object {
        private const val KEY_KEEP_ARCHIVED = "archive_keep_archived"
    }
}
