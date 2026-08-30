package com.cycling.rssradar.sync

import com.cycling.rssradar.data.store.ArchiveStore
import com.cycling.rssradar.data.store.KeepArchived
import com.cycling.rssradar.data.store.SyncStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * 自动同步唯一用例（issue #57/#58，ADR-0008）：CONTEXT.md「自动同步」「归档」的代码承载。
 * 周期任务（SyncWorker）与启动同步（SyncScheduler.onAppStart）共用，保证
 * lastAutoSyncAt 时间戳与清理行为在两条入口完全一致。
 *
 * **顺序即实现，调用方不得拆散**：必须先刷新（屏蔽源过滤）再归档清理。
 * 刷新会把 feed 里的旧文章重新 upsert 回来，与清理并发就是「删了又同步回来」
 * （issue #57 修订实测）。这条规则在此定案；各入口此前各自用注释复述的版本已废。
 *
 * 测试缝：刷新与归档以 suspend 函数注入（生产绑 [com.cycling.rssradar.data.FeedRepository]
 * 的对应方法），[clock] 可注入 fake，规则可纯 JVM 断言。
 */
class AutoSync(
    private val syncStore: SyncStore,
    private val archiveStore: ArchiveStore,
    private val refreshAutoSyncFeeds: suspend () -> Int,
    private val archiveExpired: suspend (KeepArchived) -> Int,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** 刷新参与自动同步的源 → 归档清理。 */
    suspend fun run() {
        syncStore.update { it.copy(lastAutoSyncAt = clock()) }
        refreshAutoSyncFeeds()
        archiveNow()
    }

    /** 仅归档清理，不刷新（启动时未到启动同步去抖阈值时走这条）。 */
    suspend fun archiveOnly() {
        // 归档不因取消而跳过一半：删一半留一半不可接受
        withContext(NonCancellable) {
            archiveExpired(archiveStore.state.value)
        }
    }

    private suspend fun archiveNow() {
        // 归档不因取消而跳过一半：删一半留一半不可接受
        withContext(NonCancellable) {
            archiveExpired(archiveStore.state.value)
        }
    }
}
