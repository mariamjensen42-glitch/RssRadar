package com.cycling.rssradar.sync

import com.cycling.rssradar.FakeSharedPreferences
import com.cycling.rssradar.data.store.ArchiveStore
import com.cycling.rssradar.data.store.KeepArchived
import com.cycling.rssradar.data.store.SyncStore
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

    private fun <T> List<T>.containsOnly(element: T) = size == 1 && first() == element
}
