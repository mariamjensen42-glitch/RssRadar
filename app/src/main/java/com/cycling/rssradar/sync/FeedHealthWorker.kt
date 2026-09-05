package com.cycling.rssradar.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cycling.rssradar.di.AppEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.TimeUnit

/**
 * 失效源每日复探（#82）。
 *
 * 与 [AiDailyWorker] 同一套路：不走 hilt-work，EntryPoint 取依赖。
 * 只探测有失败记录的源（consecutiveFailures > 0，FeedDao 层过滤）——
 * 健康源靠日常刷新自然维持计数，全量每日探测是纯浪费。
 * 复探走与常规刷新同一条 [com.cycling.rssradar.core.data.FeedRepository.refreshUnhealthyFeeds]：
 * 成功即清零恢复，再失败计数继续累加，达到阈值即「失效」（判定在 core/domain FeedHealth）。
 */
class FeedHealthWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val repository = EntryPointAccessors
            .fromApplication(applicationContext, AppEntryPoint::class.java)
            .feedRepository()
        repository.refreshUnhealthyFeeds()
        Result.success()
    } catch (_: Exception) {
        // 网络抖动交给 WorkManager 退避；探测失败本身已被埋点记录，这里无需再记
        Result.retry()
    }

    companion object {
        const val WORK_NAME = "rssradar-feed-health"
    }
}

/** 失效源复探的调度：每日一次，有网即可（探测量 = 伤员数，计量网络下也无妨）。 */
object FeedHealthScheduler {

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            FeedHealthWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<FeedHealthWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build(),
        )
    }
}
