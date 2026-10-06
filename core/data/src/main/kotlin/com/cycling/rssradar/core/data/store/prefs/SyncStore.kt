package com.cycling.rssradar.core.data.store.prefs

import android.content.SharedPreferences
import com.cycling.rssradar.core.model.SyncInterval
import com.cycling.rssradar.core.model.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 自动同步偏好持久化 + 运行态共享（ArchiveStore 同款模式，issue #58）。
 * 设置页改间隔/约束 → SyncScheduler.reschedule 重建周期任务。
 */
class SyncStore(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(readPersisted())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    fun update(transform: (SyncState) -> SyncState) {
        val next = transform(_state.value)
        prefs.edit()
            .putString(KEY_INTERVAL, next.interval.name)
            .putBoolean(KEY_ONLY_WIFI, next.onlyOnWifi)
            .putBoolean(KEY_ONLY_CHARGING, next.onlyWhenCharging)
            .putBoolean(KEY_SYNC_ON_START, next.syncOnStart)
            .putLong(KEY_LAST_AUTO_SYNC, next.lastAutoSyncAt)
            .apply()
        _state.value = next
    }

    private fun readPersisted(): SyncState = SyncState(
        interval = SyncInterval.fromNameOrNull(prefs.getString(KEY_INTERVAL, null))
            ?: SyncInterval.MANUALLY,
        onlyOnWifi = prefs.getBoolean(KEY_ONLY_WIFI, true),
        onlyWhenCharging = prefs.getBoolean(KEY_ONLY_CHARGING, false),
        syncOnStart = prefs.getBoolean(KEY_SYNC_ON_START, true),
        lastAutoSyncAt = prefs.getLong(KEY_LAST_AUTO_SYNC, 0L),
    )

    companion object {
        private const val KEY_INTERVAL = "sync_interval"
        private const val KEY_ONLY_WIFI = "sync_only_wifi"
        private const val KEY_ONLY_CHARGING = "sync_only_charging"
        private const val KEY_SYNC_ON_START = "sync_on_start"
        private const val KEY_LAST_AUTO_SYNC = "sync_last_auto_sync_at"
    }
}
