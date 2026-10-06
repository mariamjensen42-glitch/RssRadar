package com.cycling.rssradar.core.data

import com.cycling.rssradar.core.data.store.prefs.ReadingPositionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingPositionStoreTest {

    private fun store() = ReadingPositionStore(FakeSharedPreferences())

    @Test
    fun `没记录时返回 null`() {
        assertNull(store().get(7L))
    }

    @Test
    fun `存进去能读回来`() {
        val s = store()
        s.save(7L, 0.42f)
        assertEquals(0.42f, s.get(7L)!!, 0.0001f)
    }

    @Test
    fun `还在开头的位置不记`() {
        val s = store()
        s.save(7L, 0f)
        assertNull(s.get(7L))
        s.save(7L, 0.01f)
        assertNull(s.get(7L))
    }

    @Test
    fun `读完的位置不记`() {
        val s = store()
        s.save(7L, 1f)
        assertNull(s.get(7L))
        s.save(7L, 0.99f)
        assertNull(s.get(7L))
    }

    @Test
    fun `滚回开头会把旧记录作废`() {
        val s = store()
        s.save(7L, 0.5f)
        assertEquals(0.5f, s.get(7L)!!, 0.0001f)
        s.save(7L, 0.001f)
        assertNull(s.get(7L))
    }

    @Test
    fun `读完会把旧记录作废`() {
        val s = store()
        s.save(7L, 0.5f)
        s.save(7L, 1f)
        assertNull(s.get(7L))
    }

    @Test
    fun `clear 只删这一篇`() {
        val s = store()
        s.save(1L, 0.3f)
        s.save(2L, 0.6f)
        s.clear(1L)
        assertNull(s.get(1L))
        assertEquals(0.6f, s.get(2L)!!, 0.0001f)
    }

    @Test
    fun `同一篇反复保存不会重复占用名额`() {
        val s = store()
        repeat(5) { s.save(7L, 0.5f) }
        s.save(8L, 0.4f)
        // 7 仍在窗口内：若顺序表因重复写入被撑满，最先写入的早该被挤出去了
        assertEquals(0.5f, s.get(7L)!!, 0.0001f)
        assertEquals(0.4f, s.get(8L)!!, 0.0001f)
    }

    @Test
    fun `超过上限时淘汰最久未写的那些`() {
        val s = store()
        for (id in 1L..200L) s.save(id, 0.5f)
        assertEquals(0.5f, s.get(1L)!!, 0.0001f)
        // 第 201 篇挤进来，最旧的第 1 篇连同它的比例键一起被删
        s.save(201L, 0.5f)
        assertNull(s.get(1L))
        assertEquals(0.5f, s.get(2L)!!, 0.0001f)
        assertEquals(0.5f, s.get(201L)!!, 0.0001f)
    }

    @Test
    fun `被淘汰后再打开只是回到开头，不会读到别人的位置`() {
        val s = store()
        for (id in 1L..201L) s.save(id, 0.5f)
        assertNull(s.get(1L))
        s.save(1L, 0.7f)
        assertEquals(0.7f, s.get(1L)!!, 0.0001f)
    }
}
