package com.cycling.rssradar.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cycling.rssradar.di.AppEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * 自动同步任务（issue #58，ADR-0008）：刷新参与自动同步的源 + 归档清理。
 * 经 EntryPoint 取依赖，避免引入 hilt-work（HiltWorker）的额外注解处理器。
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        SyncRunner.runAutoSync(applicationContext)
        Result.success()
    } catch (_: Exception) {
        // 网络抖动等失败交给 WorkManager 退避重试，不打断周期
        Result.retry()
    }
}

/**
 * 自动同步执行体：刷新（屏蔽源过滤）→ 归档清理（issue #57）。
 * 周期任务与启动时同步共用，保证 lastAutoSyncAt 与清理行为一致。
 */
object SyncRunner {

    suspend fun runAutoSync(context: Context) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppEntryPoint::class.java,
        )
        val syncStore = entryPoint.syncStore()
        val archiveStore = entryPoint.archiveStore()
        val repository = entryPoint.feedRepository()

        syncStore.update { it.copy(lastAutoSyncAt = System.currentTimeMillis()) }
        repository.refreshAutoSyncFeeds()
        // 归档不因取消而跳过一半：放在 NonCancellable，删一半留一半不可接受
        withContext(NonCancellable) {
            repository.archiveExpired(archiveStore.state.value)
        }
    }
}
