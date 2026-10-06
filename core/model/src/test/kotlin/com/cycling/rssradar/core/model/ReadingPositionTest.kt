package com.cycling.rssradar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingPositionTest {

    @Test
    fun `ratio 是滚动量与上限之比`() {
        assertEquals(0.5f, ReadingPosition.ratio(scrollPos = 500, maxScroll = 1000), 0.0001f)
        assertEquals(0f, ReadingPosition.ratio(scrollPos = 0, maxScroll = 1000), 0.0001f)
        assertEquals(1f, ReadingPosition.ratio(scrollPos = 1000, maxScroll = 1000), 0.0001f)
    }

    @Test
    fun `内容不足一屏时比例为 0`() {
        assertEquals(0f, ReadingPosition.ratio(scrollPos = 0, maxScroll = 0), 0.0001f)
        assertEquals(0f, ReadingPosition.ratio(scrollPos = 300, maxScroll = -1), 0.0001f)
    }

    @Test
    fun `比例被夹在 0 与 1 之间`() {
        assertEquals(1f, ReadingPosition.ratio(scrollPos = 5000, maxScroll = 1000), 0.0001f)
        assertEquals(0f, ReadingPosition.ratio(scrollPos = -80, maxScroll = 1000), 0.0001f)
    }

    @Test
    fun `pixels 是比例的反向换算`() {
        assertEquals(500, ReadingPosition.pixels(saved = 0.5f, maxScroll = 1000))
        assertEquals(0, ReadingPosition.pixels(saved = 0f, maxScroll = 1000))
        assertEquals(1000, ReadingPosition.pixels(saved = 1f, maxScroll = 1000))
    }

    @Test
    fun `上限未知时回到开头`() {
        assertEquals(0, ReadingPosition.pixels(saved = 0.7f, maxScroll = 0))
        assertEquals(0, ReadingPosition.pixels(saved = 0.7f, maxScroll = -1))
    }

    @Test
    fun `比例换算来回不丢一个像素以上`() {
        for (pos in listOf(0, 37, 512, 999, 1000)) {
            val round = ReadingPosition.pixels(ReadingPosition.ratio(pos, 1000), 1000)
            assertTrue("pos=$pos round=$round", kotlin.math.abs(round - pos) <= 1)
        }
    }

    @Test
    fun `开头与读完都不值得记`() {
        assertFalse(ReadingPosition.isWorthRemembering(0f))
        assertFalse(ReadingPosition.isWorthRemembering(0.01f))
        assertFalse(ReadingPosition.isWorthRemembering(0.98f))
        assertFalse(ReadingPosition.isWorthRemembering(1f))
    }

    @Test
    fun `中间位置值得记`() {
        assertTrue(ReadingPosition.isWorthRemembering(0.02f + 0.0001f))
        assertTrue(ReadingPosition.isWorthRemembering(0.5f))
        assertTrue(ReadingPosition.isWorthRemembering(0.98f - 0.0001f))
    }
}
