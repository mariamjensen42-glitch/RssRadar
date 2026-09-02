package com.cycling.rssradar.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cycling.rssradar.di.AppEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * 自动同步周期任务（issue #58，ADR-0008）。
 * 经 EntryPoint 取 [AutoSync]，避免引入 hilt-work（HiltWorker）的额外注解处理器。
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        EntryPointAccessors
            .fromApplication(applicationContext, AppEntryPoint::class.java)
            .autoSync()
            .run()
        Result.success()
    } catch (_: Exception) {
        // 网络抖动等失败交给 WorkManager 退避重试，不打断周期
        Result.retry()
    }
}
