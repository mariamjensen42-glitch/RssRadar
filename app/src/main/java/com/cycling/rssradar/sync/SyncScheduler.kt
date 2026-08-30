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
     * 再在**单个协程内顺序执行**：应跑启动同步则「刷新 → 清理」（与周期任务同链路），
     * 否则只做归档清理。fire-and-forget，不阻塞启动。
     * 禁止把刷新和清理拆成并发协程——刷新会把 feed 里的旧文章重新 upsert 回来，
     * 与清理竞态就是「删了又同步回来」（issue #57 修订实测）。
     */
    fun onAppStart(context: Context, externalScope: CoroutineScope) {
        reschedule(context)
        externalScope.launch {
            val entryPoint = EntryPointAccessors
                .fromApplication(context.applicationContext, AppEntryPoint::class.java)
            val state = entryPoint.syncStore().state.value
            val now = System.currentTimeMillis()
            val shouldSync = state.syncOnStart &&
                now - state.lastAutoSyncAt >= SyncStore.START_SYNC_DEBOUNCE_MS
            if (shouldSync) {
                runCatching { SyncRunner.runAutoSync(context) }
            } else {
                runCatching {
                    entryPoint.feedRepository()
                        .archiveExpired(entryPoint.archiveStore().state.value)
                }
            }
        }
    }
}
