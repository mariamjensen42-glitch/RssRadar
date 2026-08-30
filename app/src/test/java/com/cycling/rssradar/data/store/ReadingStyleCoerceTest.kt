package com.cycling.rssradar.data.store

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingStyleCoerceTest {

    @Test
    fun `font size coerces into range`() {
        assertEquals(12, coerceFontSize(0))
        assertEquals(12, coerceFontSize(12))
        assertEquals(17, coerceFontSize(17))
        assertEquals(28, coerceFontSize(28))
        assertEquals(28, coerceFontSize(99))
    }

    @Test
    fun `line height coerces into range`() {
        assertEquals(0.8f, coerceLineHeight(-1f))
        assertEquals(0.8f, coerceLineHeight(0.8f))
        assertEquals(1.25f, coerceLineHeight(1.25f))
        assertEquals(2.5f, coerceLineHeight(9.9f))
    }

    @Test
    fun `padding coerces into range`() {
        assertEquals(0, coercePadding(-8))
        assertEquals(0, coercePadding(0))
        assertEquals(24, coercePadding(24))
        assertEquals(48, coercePadding(48))
        assertEquals(48, coercePadding(120))
    }

    @Test
    fun `defaults match spec`() {
        assertEquals(17, ReadingStyleState.DEFAULT_FONT_SIZE)
        assertEquals(1.0f, ReadingStyleState.DEFAULT_LINE_HEIGHT)
        assertEquals(24, ReadingStyleState.DEFAULT_PADDING)
        assertEquals(ReadingFontFamily.SYSTEM, ReadingStyleState().fontFamily)
    }
}
