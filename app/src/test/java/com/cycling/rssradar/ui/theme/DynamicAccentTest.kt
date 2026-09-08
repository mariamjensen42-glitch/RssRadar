package com.cycling.rssradar.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动态取色强调色派生（#27）：系统只给 primary / onPrimary，
 * accentPressed 与 link 由本项目派生，比例即判据，所以单独立测试。
 */
class DynamicAccentTest {

    private val accent = Color(0xFF7B7CFF)
    private val onAccent = Color(0xFFFFFFFF)

    @Test
    fun `accent and onAccent pass through unchanged`() {
        val dark = deriveAccentColors(accent, onAccent, darkTheme = true)
        assertEquals(accent, dark.accent)
        assertEquals(onAccent, dark.onAccent)
    }

    @Test
    fun `pressed state is darker than accent in both themes`() {
        assertTrue(deriveAccentColors(accent, onAccent, darkTheme = true).accentPressed.red < accent.red)
        assertTrue(deriveAccentColors(accent, onAccent, darkTheme = false).accentPressed.red < accent.red)
    }

    @Test
    fun `link is lighter than accent on dark background`() {
        val link = deriveAccentColors(accent, onAccent, darkTheme = true).link
        assertTrue("深色下 link 必须提亮，否则在纯黑背景上读不出是链接", link.red > accent.red)
    }

    @Test
    fun `link is darker than accent on light background`() {
        val link = deriveAccentColors(accent, onAccent, darkTheme = false).link
        assertTrue("浅色下 link 必须压暗，否则白底对比度掉下去", link.red < accent.red)
    }

    @Test
    fun `derived colors stay inside channel range`() {
        listOf(true, false).forEach { dark ->
            val c = deriveAccentColors(Color(0xFFFFFFFF), Color(0xFF000000), dark)
            listOf(c.accent, c.accentPressed, c.onAccent, c.link).forEach { color ->
                assertTrue(color.red in 0f..1f)
                assertTrue(color.green in 0f..1f)
                assertTrue(color.blue in 0f..1f)
            }
        }
    }
}
