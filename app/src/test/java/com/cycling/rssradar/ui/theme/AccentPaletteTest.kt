package com.cycling.rssradar.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 强调色数学（#29）：HSL ↔ ARGB 与「白字还是黑字」的对比度判据。
 * 全是纯 Kotlin——不碰 compose 的色彩空间转换（那会把 ui-util 拽进测试 classpath）。
 */
class AccentPaletteTest {

    private fun channels(argb: Long): IntArray = intArrayOf(
        ((argb shr 16) and 0xFF).toInt(),
        ((argb shr 8) and 0xFF).toInt(),
        (argb and 0xFF).toInt(),
    )

    @Test
    fun `black accent gets white foreground`() {
        assertEquals(Color(0xFFFFFFFFL), onAccentFor(Color(0xFF000000L)))
    }

    @Test
    fun `white accent gets black foreground`() {
        assertEquals(Color(0xFF000000L), onAccentFor(Color(0xFFFFFFFFL)))
    }

    @Test
    fun `dark navy accent gets white foreground`() {
        assertEquals(Color(0xFFFFFFFFL), onAccentFor(Color(0xFF1E3A8AL)))
    }

    @Test
    fun `bright amber accent gets black foreground`() {
        assertEquals("浅色底上再画白字就糊了，必须换成黑字", Color(0xFF000000L), onAccentFor(Color(0xFFD9A100L)))
    }

    @Test
    fun `first preset is the default purple`() {
        assertEquals(DEFAULT_ACCENT_ARGB, ACCENT_PRESETS.first())
    }

    @Test
    fun `every preset round-trips through HSL within one step`() {
        ACCENT_PRESETS.forEach { argb ->
            val back = argbToHsl(argb).toArgb()
            val expected = channels(argb)
            val actual = channels(back)
            (0..2).forEach { i ->
                val diff = kotlin.math.abs(expected[i] - actual[i])
                assertTrue("预设色 ${argb.toString(16)} 第 $i 通道偏差 $diff", diff <= 1)
            }
        }
    }

    @Test
    fun `primary hues map to the right channels`() {
        assertEquals(0xFFFF0000L, HslColor(0f, 1f, 0.5f).toArgb())
        assertEquals(0xFF00FF00L, HslColor(120f, 1f, 0.5f).toArgb())
        assertEquals(0xFF0000FFL, HslColor(240f, 1f, 0.5f).toArgb())
    }

    @Test
    fun `out of range HSL is clamped instead of producing garbage`() {
        assertEquals(0xFF000000L, HslColor(400f, 2f, -1f).toArgb())
        assertEquals(0xFFFFFFFFL, HslColor(-30f, 0f, 2f).toArgb())
    }

    @Test
    fun `gray has zero saturation`() {
        val gray = argbToHsl(0xFF808080L)
        assertEquals(0f, gray.saturation, 0.0001f)
    }
}
