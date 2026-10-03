package com.cycling.rssradar.core.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cycling.rssradar.core.data.di.AppEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * 检索语料补齐：把还没写 `searchText` / 未进 FTS 的文章补上。
 *
 * 为什么要有它：新文章的语料在刷新时同步写入（[com.cycling.rssradar.core.data.refresh.RefreshEngine]），
 * 但**升级用户**的历史文章、以及刷新中途失败留下的空洞没有别的地方会补。
 * 缺了它，用户升级后搜不到旧文章——而"搜不到"会被当成"搜索坏了"。
 */
class SearchIndexWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val indexer = EntryPointAccessors
            .fromApplication(applicationContext, AppEntryPoint::class.java)
            .searchIndexer()
        return try {
            // 一致就不动：这个判断同时兜住"已经补过"与"用户没动过库"两种情况
            if (!indexer.isConsistent()) {
                val indexed = indexer.ensureIndexed()
                Result.success(workDataOf(KEY_INDEXED to indexed))
            } else {
                Result.success(workDataOf(KEY_INDEXED to 0))
            }
        } catch (t: Throwable) {
            // 索引是可重入的（补齐语义），失败就再试；但别无限重试卡住队列
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_INDEXED = "indexed"

        /** 索引语料是"慢慢补"的事，失败三次已经足够说明有硬问题。 */
        const val MAX_ATTEMPTS = 3
    }
}

/**
 * 检索语料的调度入口。[ExistingWorkPolicy.KEEP]：已经在队列里就不重复入队——
 * 每次冷启动都入队一条会让队列越堆越长。
 */
object SearchIndexScheduler {

    private const val UNIQUE_NAME = "rssradar-search-index"

    fun ensureIndexed(context: Context) {
        val request = OneTimeWorkRequestBuilder<SearchIndexWorker>()
            .setConstraints(
                // 数万篇的建索引是重活，别在低电量时跑
                Constraints.Builder().setRequiresBatteryNotLow(true).build(),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
