package com.cycling.rssradar.sync

import com.cycling.rssradar.FakeSharedPreferences
import com.cycling.rssradar.core.data.store.ArchiveStore
import com.cycling.rssradar.core.data.store.KeepArchived
import com.cycling.rssradar.core.data.store.SyncStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「刷新 → 清理」顺序不变量的 JVM 证明（深化前的三处注释级约定，现收口 [AutoSync]）。
 */
class AutoSyncTest {

    private class Recorder {
        val events = mutableListOf<String>()
        suspend fun refresh(): Int { events += "refresh"; return 1 }
        suspend fun archive(keep: KeepArchived): Int { events += "archive"; return 0 }
    }

    @Test
    fun `run 先刷新后清理，并更新 lastAutoSyncAt`() = runBlocking {
        val prefs = FakeSharedPreferences()
        val syncStore = SyncStore(prefs)
        val archiveStore = ArchiveStore(prefs)
        val recorder = Recorder()
        val autoSync = AutoSync(
            syncStore = syncStore,
            archiveStore = archiveStore,
            refreshAutoSyncFeeds = recorder::refresh,
            archiveExpired = recorder::archive,
            clock = { 1234L },
        )

        autoSync.run()

        assertEquals(listOf("refresh", "archive"), recorder.events)
        assertEquals(1234L, syncStore.state.value.lastAutoSyncAt)
    }

    @Test
    fun `archiveOnly 不刷新只清理`() = runBlocking {
        val syncStore = SyncStore(FakeSharedPreferences())
        val archiveStore = ArchiveStore(FakeSharedPreferences())
        val recorder = Recorder()
        val autoSync = AutoSync(
            syncStore = syncStore,
            archiveStore = archiveStore,
            refreshAutoSyncFeeds = recorder::refresh,
            archiveExpired = recorder::archive,
        )

        autoSync.archiveOnly()

        assertTrue(recorder.events.containsOnly("archive"))
    }

    // ---- runOnStart：启动去抖判定（原先住在 SyncScheduler，clock 不可注入） ----

    private fun startSync(
        prefs: FakeSharedPreferences = FakeSharedPreferences(),
        now: Long,
    ): Triple<Recorder, SyncStore, AutoSync> {
        val syncStore = SyncStore(prefs)
        val recorder = Recorder()
        val autoSync = AutoSync(
            syncStore = syncStore,
            archiveStore = ArchiveStore(prefs),
            refreshAutoSyncFeeds = recorder::refresh,
            archiveExpired = recorder::archive,
            clock = { now },
        )
        return Triple(recorder, syncStore, autoSync)
    }

    @Test
    fun `runOnStart 上次同步刚过就只归档不刷新`() = runBlocking {
        val prefs = FakeSharedPreferences()
        SyncStore(prefs).update { it.copy(lastAutoSyncAt = 10_000L) }
        val (recorder, _, autoSync) = startSync(prefs, now = 10_000L + 60_000L)

        autoSync.runOnStart()

        assertTrue(recorder.events.containsOnly("archive"))
    }

    @Test
    fun `runOnStart 超过去抖窗口就跑完整同步`() = runBlocking {
        val prefs = FakeSharedPreferences()
        SyncStore(prefs).update { it.copy(lastAutoSyncAt = 10_000L) }
        val (recorder, syncStore, autoSync) = startSync(
            prefs,
            now = 10_000L + AutoSync.START_SYNC_DEBOUNCE_MS,
        )

        autoSync.runOnStart()

        assertEquals(listOf("refresh", "archive"), recorder.events)
        assertEquals(10_000L + AutoSync.START_SYNC_DEBOUNCE_MS, syncStore.state.value.lastAutoSyncAt)
    }

    @Test
    fun `runOnStart 关闭启动同步后只归档`() = runBlocking {
        val prefs = FakeSharedPreferences()
        SyncStore(prefs).update { it.copy(syncOnStart = false, lastAutoSyncAt = 0L) }
        // 距上次同步远超去抖窗口，但开关关着
        val (recorder, _, autoSync) = startSync(prefs, now = AutoSync.START_SYNC_DEBOUNCE_MS * 10)

        autoSync.runOnStart()

        assertTrue(recorder.events.containsOnly("archive"))
    }

    @Test
    fun `runOnStart 从未同步过则视为到期`() = runBlocking {
        // lastAutoSyncAt 默认 0（epoch），时钟取一个远超去抖窗口的真实时刻
        val (recorder, _, autoSync) = startSync(now = 1_788_160_000_000L)

        autoSync.runOnStart()

        assertEquals(listOf("refresh", "archive"), recorder.events)
    }

    private fun <T> List<T>.containsOnly(element: T) = size == 1 && first() == element
}
