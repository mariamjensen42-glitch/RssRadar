package com.cycling.rssradar.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.NetworkType
import com.cycling.rssradar.core.data.ai.AiBatchProcessor
import com.cycling.rssradar.core.data.ai.AiFeature
import com.cycling.rssradar.di.AppEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.TimeUnit


/**
 * 每日 AI 批处理。
 *
 * 与 [com.cycling.rssradar.sync.SyncWorker] 同一套路：不用 hilt-work，
 * 走 `EntryPointAccessors` + [AppEntryPoint] 取依赖，省一个注解处理器。
 *
 * 一个容易被忽略的判定：**额度用尽返回 success 而不是 retry**。
 * 额度是用户自己设的上限，用完了是预期内的正常状态；
 * 若返回 retry，WorkManager 会按退避在几小时内反复拉起，白白耗电。
 */
class AiDailyWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val processor = EntryPointAccessors
            .fromApplication(applicationContext, AppEntryPoint::class.java)
            .aiBatchProcessor()
        processor.runDaily()
        Result.success()
    } catch (_: Exception) {
        // 网络抖动交给 WorkManager 退避重试，这里不吞错误详情——详情在 ai_tasks.lastError 里。
        Result.retry()
    }

    companion object {
        const val WORK_NAME = "rssradar-ai-daily"
    }
}


/**
 * AI 批处理的调度。
 *
 * **没有任何已开启的批处理功能时直接取消任务**，而不是留一个每天白跑一次的周期任务——
 * 每跑一次都要唤醒进程、查库、建队列，只为得出"什么都不用做"，纯属浪费电量。
 * 开关在设置页一变就重新调度（[reschedule]），任务生命周期跟着开关走。
 */
object AiTaskScheduler {

    /**
     * 按当前开启的功能重新调度每日任务。
     *
     * @param enabled 当前开启的功能集合；为空或不含任何批处理功能则取消周期任务。
     */
    fun reschedule(context: Context, enabled: Set<AiFeature>) {
        val workManager = WorkManager.getInstance(context)
        val hasBatch = AiFeature.BATCH_FEATURES.any { it in enabled }
        if (!hasBatch) {
            workManager.cancelUniqueWork(AiDailyWorker.WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            // 批处理只在非计量网络下跑：几十次请求在流量下既贵又不稳。
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()

        workManager.enqueueUniquePeriodicWork(
            AiDailyWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AiDailyWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build(),
        )
    }

    /** 立即跑一批（设置页「立即执行」按钮）。已有在跑的则跳过，不叠加。 */
    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${AiDailyWorker.WORK_NAME}-once",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AiDailyWorker>().build(),
        )
    }

    fun cancelAll(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(AiDailyWorker.WORK_NAME)
        workManager.cancelUniqueWork("${AiDailyWorker.WORK_NAME}-once")
    }
}
