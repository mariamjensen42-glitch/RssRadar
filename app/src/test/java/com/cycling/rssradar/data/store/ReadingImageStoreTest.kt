package com.cycling.rssradar.data.store

import com.cycling.rssradar.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 阅读页图片偏好持久化（issue #60）：默认值、落盘与越界夹取。 */
class ReadingImageStoreTest {

    private fun store(prefs: FakeSharedPreferences = FakeSharedPreferences()) =
        ReadingImageStore(prefs) to prefs

    @Test
    fun `defaults keep the pre feature look`() {
        val (s, _) = store()

        assertEquals(ReadingImageState.DEFAULT_CORNER_RADIUS, s.state.value.cornerRadius)
        assertTrue(s.state.value.maximizeOnTap)
    }

    @Test
    fun `corner radius update is persisted`() {
        val (s, prefs) = store()

        s.update { it.copy(cornerRadius = 16) }

        assertEquals(16, s.state.value.cornerRadius)
        assertEquals(16, prefs.getInt("reading_image_corner_radius", -1))
    }

    @Test
    fun `corner radius is clamped into range`() {
        val (s, _) = store()

        s.update { it.copy(cornerRadius = 999) }
        assertEquals(ReadingImageState.CORNER_RADIUS_MAX, s.state.value.cornerRadius)

        s.update { it.copy(cornerRadius = -5) }
        assertEquals(ReadingImageState.CORNER_RADIUS_MIN, s.state.value.cornerRadius)
    }

    @Test
    fun `maximize switch survives a store restart`() {
        val prefs = FakeSharedPreferences()
        ReadingImageStore(prefs).update { it.copy(maximizeOnTap = false) }

        assertFalse(ReadingImageStore(prefs).state.value.maximizeOnTap)
    }

    @Test
    fun `out of range persisted value is clamped on read`() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putInt("reading_image_corner_radius", 80).apply()

        assertEquals(ReadingImageState.CORNER_RADIUS_MAX, ReadingImageStore(prefs).state.value.cornerRadius)
    }
}
