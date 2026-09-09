package com.cycling.rssradar.core.data.store

import com.cycling.rssradar.core.data.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `custom accent defaults to null and round-trips`() {
        val prefs = FakeSharedPreferences()
        assertNull(ThemeStore(prefs).customAccent.value)
        ThemeStore(prefs).setCustomAccent(0xFF12AB34L)
        assertEquals(0xFF12AB34L, ThemeStore(prefs).customAccent.value)
    }

    @Test
    fun `clearing custom accent removes the key entirely`() {
        val prefs = FakeSharedPreferences()
        val store = ThemeStore(prefs)
        store.setCustomAccent(0xFF12AB34L)
        store.setCustomAccent(null)
        assertNull(store.customAccent.value)
        assertFalse("回到默认必须真的删键，否则下次读到的是旧值", prefs.map.containsKey("theme_custom_accent"))
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
