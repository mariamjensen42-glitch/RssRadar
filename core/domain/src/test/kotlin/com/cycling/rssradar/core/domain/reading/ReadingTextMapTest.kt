package com.cycling.rssradar.core.domain.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingTextMapTest {

    @Test
    fun `摊平后各块区间正确`() {
        val flat = ReadingTextMap.flatten(listOf("abc", "de"))
        assertEquals("abc\nde", flat.text)
        assertEquals(2, flat.spans.size)
        assertEquals(ReadingTextMap.Span(0, 0, 3), flat.spans[0])
        assertEquals(ReadingTextMap.Span(1, 4, 6), flat.spans[1])
    }

    @Test
    fun `块之间插入分隔符避免粘词`() {
        val flat = ReadingTextMap.flatten(listOf("hello", "world"))
        assertTrue(flat.text.contains("\n"))
        assertEquals("hello\nworld", flat.text)
    }

    @Test
    fun `空块列表得到空文本`() {
        val flat = ReadingTextMap.flatten(emptyList())
        assertEquals("", flat.text)
        assertTrue(flat.spans.isEmpty())
    }

    @Test
    fun `偏移可反查所属块`() {
        val flat = ReadingTextMap.flatten(listOf("abc", "de"))
        assertEquals(0, flat.spanOf(1)?.blockIndex)
        assertEquals(1, flat.spanOf(5)?.blockIndex)
    }

    @Test
    fun `分隔符不属于任何块`() {
        val flat = ReadingTextMap.flatten(listOf("abc", "de"))
        assertNull(flat.spanOf(3))
    }

    @Test
    fun `单块区间映射回局部坐标`() {
        val flat = ReadingTextMap.flatten(listOf("abcdef"))
        val ranges = ReadingTextMap.blockRangesFor(flat, 1, 4)
        assertEquals(1, ranges.size)
        assertEquals(0, ranges[0].first)
        assertEquals(1 until 4, ranges[0].second)
    }

    @Test
    fun `跨块区间被切成多段`() {
        val flat = ReadingTextMap.flatten(listOf("abc", "def"))
        val ranges = ReadingTextMap.blockRangesFor(flat, 1, 6)
        assertEquals(2, ranges.size)
        assertEquals(0, ranges[0].first)
        assertEquals(1 until 3, ranges[0].second)
        assertEquals(1, ranges[1].first)
        assertEquals(0 until 2, ranges[1].second)
    }

    @Test
    fun `落在分隔符上的部分被丢弃`() {
        val flat = ReadingTextMap.flatten(listOf("abc", "def"))
        val ranges = ReadingTextMap.blockRangesFor(flat, 3, 4)
        assertTrue(ranges.isEmpty())
    }

    @Test
    fun `空区间返回空`() {
        val flat = ReadingTextMap.flatten(listOf("abc"))
        assertTrue(ReadingTextMap.blockRangesFor(flat, 2, 2).isEmpty())
    }
}
