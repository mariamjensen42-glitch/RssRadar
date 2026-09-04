package com.cycling.rssradar.sync

import com.cycling.rssradar.core.data.store.ArchiveStore
import com.cycling.rssradar.core.data.store.KeepArchived
import com.cycling.rssradar.core.data.store.SyncStore
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
 * 测试缝：刷新与归档以 suspend 函数注入（生产绑 [com.cycling.rssradar.core.data.FeedRepository]
 * 的对应方法），[clock] 可注入 fake，规则可纯 JVM 断言。
 */
class AutoSync(
    private val syncStore: SyncStore,
    private val archiveStore: ArchiveStore,
    private val refreshAutoSyncFeeds: suspend () -> Int,
    private val archiveExpired: suspend (KeepArchived) -> Int,
    /**
     * 新文章通知（#31）：入参是本轮同步的起点时间戳，实现方据此取"新进库"的文章。
     * 默认空实现——通知是可选附加行为，不注入就完全不碰（老调用方与测试不受影响）。
     */
    private val notifyNewArticles: suspend (sinceMillis: Long) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** 刷新参与自动同步的源 → 归档清理 → 通知。 */
    suspend fun run() {
        // 起点时间戳必须在刷新之前取：新文章的 fetchedAt 落在它之后才算新
        val startedAt = clock()
        syncStore.update { it.copy(lastAutoSyncAt = startedAt) }
        refreshAutoSyncFeeds()
        archiveNow()
        // 归档之后再统计：刚被清理掉的旧文章不该出现在通知里
        notifyNewArticles(startedAt)
    }

    /**
     * 应用启动入口：判定「这次启动要不要跑一次完整同步」，然后只跑该跑的那条。
     *
     * 去抖判定原先住在 SyncScheduler 的 WorkManager plumbing 里，直接调
     * `System.currentTimeMillis()`，而本类手里就有一个可注入的 [clock] ——
     * 判定偏偏住在唯一测不到的那侧。挪进来之后，去抖阈值与写 lastAutoSyncAt 的代码
     * 同处一模块，测试注入 fake clock 就能跨过这条缝。
     */
    suspend fun runOnStart() {
        val state = syncStore.state.value
        val due = state.syncOnStart &&
            clock() - state.lastAutoSyncAt >= START_SYNC_DEBOUNCE_MS
        if (due) run() else archiveOnly()
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

    companion object {
        /**
         * 启动同步去抖窗口：距上次自动同步不足这个时长就不再刷新，只做归档清理。
         * 常数随判定住在本模块（原先在 SyncStore，判定在 SyncScheduler，隔文件对账）。
         */
        const val START_SYNC_DEBOUNCE_MS = 30 * 60_000L
    }
}
