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
     * 再 fire-and-forget 跑自动同步用例。
     *
     * 跑哪条（完整同步还是只归档）以及启动去抖的判定都在 [AutoSync.runOnStart] 里 ——
     * 那里有可注入的 clock，且 lastAutoSyncAt 也是它写的。本类只做 WorkManager 装配，
     * 不参与同步策略。
     */
    fun onAppStart(context: Context, externalScope: CoroutineScope) {
        reschedule(context)
        externalScope.launch {
            val autoSync = EntryPointAccessors
                .fromApplication(context.applicationContext, AppEntryPoint::class.java)
                .autoSync()
            runCatching { autoSync.runOnStart() }
        }
    }
}
