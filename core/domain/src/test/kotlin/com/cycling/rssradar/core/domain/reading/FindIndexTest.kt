package com.cycling.rssradar.core.domain.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FindIndexTest {

    private val flat = ReadingTextMap.flatten(
        listOf("今天发布了一款新手机", "价格是三千元", "配置很高"),
    )

    @Test
    fun `首次命中定位到正确块`() {
        val hits = FindIndex.find(flat, "新手机")
        assertEquals(1, hits.size)
        assertEquals(0, hits[0].blockIndex)
        assertEquals("今天发布了一款新手机".indexOf("新手机"), hits[0].localStart)
    }

    @Test
    fun `多块命中各自成条`() {
        val multi = ReadingTextMap.flatten(listOf("苹果很好吃", "苹果很贵"))
        val hits = FindIndex.find(multi, "苹果")
        assertEquals(2, hits.size)
        assertEquals(0, hits[0].blockIndex)
        assertEquals(1, hits[1].blockIndex)
    }

    @Test
    fun `默认忽略大小写`() {
        val latin = ReadingTextMap.flatten(listOf("Hello World"))
        assertEquals(1, FindIndex.find(latin, "hello").size)
    }

    @Test
    fun `区分大小写时找不到`() {
        val latin = ReadingTextMap.flatten(listOf("Hello World"))
        assertTrue(FindIndex.find(latin, "hello", caseSensitive = true).isEmpty())
    }

    @Test
    fun `整词匹配排除子串但保留独立词`() {
        val latin = ReadingTextMap.flatten(listOf("scatter the cat"))
        val wholeOnly = FindIndex.find(latin, "cat", wholeWord = true)
        assertEquals(1, wholeOnly.size)
        assertEquals("scatter the cat".lastIndexOf("cat"), wholeOnly[0].localStart)
        assertEquals(2, FindIndex.find(latin, "cat").size)
    }

    @Test
    fun `空查询返回空`() {
        assertTrue(FindIndex.find(flat, "   ").isEmpty())
    }

    @Test
    fun `无命中返回空`() {
        assertTrue(FindIndex.find(flat, "不存在的内容").isEmpty())
    }

    @Test
    fun `游标从第一处开始并向后环绕`() {
        val multi = ReadingTextMap.flatten(listOf("a x", "b x", "c x"))
        val cursor = FindIndex.Cursor(FindIndex.find(multi, "x"))
        assertEquals(3, cursor.size)
        assertEquals(0, cursor.next()?.blockIndex)
        assertEquals(1, cursor.next()?.blockIndex)
        assertEquals(2, cursor.next()?.blockIndex)
        assertEquals(0, cursor.next()?.blockIndex)
    }

    @Test
    fun `游标向前从末尾环绕`() {
        val multi = ReadingTextMap.flatten(listOf("a x", "b x"))
        val cursor = FindIndex.Cursor(FindIndex.find(multi, "x"))
        assertEquals(1, cursor.previous()?.blockIndex)
        assertEquals(0, cursor.previous()?.blockIndex)
        assertEquals(1, cursor.previous()?.blockIndex)
    }

    @Test
    fun `空命中集的游标返回 null`() {
        val cursor = FindIndex.Cursor(FindIndex.find(flat, "不存在"))
        assertNull(cursor.next())
        assertNull(cursor.previous())
        assertNull(cursor.current())
        assertEquals(0, cursor.size)
    }
}
