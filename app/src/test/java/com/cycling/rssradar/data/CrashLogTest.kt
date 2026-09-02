package com.cycling.rssradar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 崩溃日志落盘逻辑（issue #61）：只测纯逻辑（写文件 / 截断 / 滚动保留 / 摘要解析），
 * 不碰 Android API——install/list/read 需要 Context，留给真机与 CI 冒烟。
 */
class CrashLogTest {

    private fun tempDir(): File =
        File.createTempFile("crashlog", "").apply { delete(); mkdirs() }

    @Test
    fun write_namesByTimestamp_andContentIsIntact() {
        val dir = tempDir()
        val name = CrashLog.write(dir, "# boom\nbody", 1_700_000_000_123L)

        assertTrue(name.startsWith("crash_"))
        assertTrue(name.endsWith(".log"))
        assertEquals("# boom\nbody", File(dir, name).readText())
    }

    @Test
    fun write_sameSecondDoesNotOverwrite() {
        val dir = tempDir()
        val first = CrashLog.write(dir, "first", 1_700_000_000_000L)
        val second = CrashLog.write(dir, "second", 1_700_000_000_500L)

        assertEquals(2, dir.listFiles()!!.size)
        assertEquals("first", File(dir, first).readText())
        assertEquals("second", File(dir, second).readText())
    }

    @Test
    fun format_keepsShortTraceAsIs() {
        val trace = "a\nb\nc"
        assertEquals(trace, CrashLog.format(trace, maxLines = 10))
    }

    @Test
    fun format_truncatesLongTraceWithHonestMarker() {
        val trace = (1..500).joinToString("\n") { "line $it" }
        val out = CrashLog.format(trace, maxLines = 10)

        assertTrue(out.contains("line 10"))
        assertTrue(out.contains("共 500 行"))
        assertTrue(out.contains("已截断"))
        assertTrue(!out.contains("line 11\n"))
    }

    @Test
    fun prune_keepsNewestFiveByName() {
        val dir = tempDir()
        val written = (1..8).map { i ->
            CrashLog.write(dir, "crash $i", 1_700_000_000_000L + i * 1_000L)
        }

        CrashLog.prune(dir, max = 5)

        val left = dir.listFiles()!!.map { it.name }.toSet()
        assertEquals(5, left.size)
        // 保留时间戳最大的 5 份（written 按时间递增）
        assertEquals(written.takeLast(5).toSet(), left)
    }

    @Test
    fun prune_ignoresUnrelatedFiles() {
        val dir = tempDir()
        File(dir, "not-a-crash.txt").writeText("keep me")
        CrashLog.write(dir, "boom", 1_700_000_000_000L)

        CrashLog.prune(dir, max = 5)

        assertTrue(File(dir, "not-a-crash.txt").exists())
    }

    @Test
    fun headOf_readsMarkedFirstLine() {
        val dir = tempDir()
        val name = CrashLog.write(dir, "# java.lang.IllegalStateException: boom\nmore", 1L)

        assertEquals(
            "java.lang.IllegalStateException: boom",
            CrashLog.headOf(File(dir, name)),
        )
    }

    @Test
    fun headOf_fallsBackWhenFileIsEmpty() {
        val dir = tempDir()
        val name = CrashLog.write(dir, "", 1L)

        assertEquals("崩溃", CrashLog.headOf(File(dir, name)))
    }
}
