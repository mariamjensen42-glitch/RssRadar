package com.cycling.rssradar.core.data.store

import com.cycling.rssradar.core.data.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ThemeStore：主题模式 + Material You 动态取色（#27）的持久化。
 * 动态取色默认关——整套色板跟随壁纸会让产品视觉身份消失，必须显式开启。
 */
class ThemeStoreTest {

    @Test
    fun `dynamic color defaults to off`() {
        assertFalse(ThemeStore(FakeSharedPreferences()).dynamicColor.value)
    }

    @Test
    fun `dynamic color survives a new store instance`() {
        val prefs = FakeSharedPreferences()
        ThemeStore(prefs).setDynamicColor(true)
        assertTrue(ThemeStore(prefs).dynamicColor.value)
    }

    @Test
    fun `theme mode defaults to SYSTEM and round-trips`() {
        val prefs = FakeSharedPreferences()
        assertEquals(ThemeMode.SYSTEM, ThemeStore(prefs).mode.value)
        ThemeStore(prefs).setMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, ThemeStore(prefs).mode.value)
    }

    @Test
    fun `unparsable theme mode falls back to SYSTEM`() {
        val prefs = FakeSharedPreferences()
        prefs.map["theme_mode"] = "PURPLE"
        assertEquals(ThemeMode.SYSTEM, ThemeStore(prefs).mode.value)
    }
}
