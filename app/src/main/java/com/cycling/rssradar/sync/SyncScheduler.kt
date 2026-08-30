package com.cycling.rssradar.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.cycling.rssradar.data.store.SyncInterval
import com.cycling.rssradar.data.store.SyncStore
import com.cycling.rssradar.di.AppEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * 自动同步调度（issue #58，ADR-0008）：WorkManager 唯一周期任务
 * 「rssradar-auto-sync」，间隔/约束由 SyncStore 驱动，设置变更即重建。
 */
object SyncScheduler {

    private const val WORK_NAME = "rssradar-auto-sync"

    /**
     * 按当前 SyncStore 状态重建（或取消）周期任务。
     * 设置页改间隔/约束、应用启动时都调；UPDATE 策略原地替换不打断下一次执行计划。
     */
    fun reschedule(context: Context) {
        val state = EntryPointAccessors
            .fromApplication(context.applicationContext, AppEntryPoint::class.java)
            .syncStore()
            .state
            .value
        val workManager = WorkManager.getInstance(context)
        if (state.interval == SyncInterval.MANUALLY) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (state.onlyOnWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(state.onlyWhenCharging)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SyncWorker>(state.interval.minutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build(),
        )
    }

    /**
     * 应用启动入口：先按最新偏好重建周期任务（覆盖系统重启/任务被清的场景），
     * 再按开关 + 去抖决定是否立即同步一轮（fire-and-forget，不阻塞启动）。
     */
    fun onAppStart(context: Context, externalScope: CoroutineScope) {
        reschedule(context)
        externalScope.launch {
            val syncStore = EntryPointAccessors
                .fromApplication(context.applicationContext, AppEntryPoint::class.java)
                .syncStore()
            val state = syncStore.state.value
            if (!state.syncOnStart) return@launch
            val now = System.currentTimeMillis()
            if (now - state.lastAutoSyncAt < SyncStore.START_SYNC_DEBOUNCE_MS) return@launch
            runCatching { SyncRunner.runAutoSync(context) }
        }
    }
}
