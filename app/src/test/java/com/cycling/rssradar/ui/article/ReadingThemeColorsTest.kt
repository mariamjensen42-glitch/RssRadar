package com.cycling.rssradar.ui.article

import com.cycling.rssradar.core.data.store.ReadingTheme
import com.cycling.rssradar.core.ui.theme.RadarColors
import com.cycling.rssradar.core.ui.theme.isLightBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阅读主题配色（ReadYou 差距表 #16）。返回整份 [RadarColors] 由阅读页整体注入
 * CompositionLocal，所以「哪些槽位该被换掉」就是这里的全部判据。
 */
class ReadingThemeColorsTest {

    private val app = RadarColors.Dark

    @Test
    fun `FOLLOW hands back the app palette untouched`() {
        assertSame("跟随应用就该是同一份色板，别复制", app, ReadingTheme.FOLLOW.pageColors(app))
    }

    @Test
    fun `presets repaint the background`() {
        ReadingTheme.entries.filter { it != ReadingTheme.FOLLOW }.forEach { theme ->
            assertNotEquals(
                "「${theme.label}」必须真的换掉底色",
                app.bgRoot,
                theme.pageColors(app).bgRoot,
            )
            assertNotEquals(app.textPrimary, theme.pageColors(app).textPrimary)
        }
    }

    @Test
    fun `presets keep the accent so custom accent still applies`() {
        ReadingTheme.entries.forEach { theme ->
            val page = theme.pageColors(app)
            assertEquals("「${theme.label}」不该动强调色", app.accent, page.accent)
            assertEquals(app.onAccent, page.onAccent)
        }
    }

    @Test
    fun `paper and gray are light backgrounds`() {
        assertTrue(ReadingTheme.PAPER.pageColors(app).isLightBackground())
        assertTrue(ReadingTheme.GRAY.pageColors(app).isLightBackground())
    }

    @Test
    fun `night is a dark background even when the app is in light mode`() {
        assertFalse(ReadingTheme.NIGHT.pageColors(RadarColors.Light).isLightBackground())
    }

    @Test
    fun `each preset is visually distinct`() {
        val bgs = ReadingTheme.entries.map { it.pageColors(app).bgRoot }
        assertEquals("四档底色不能撞车", bgs.size, bgs.toSet().size)
    }
}
