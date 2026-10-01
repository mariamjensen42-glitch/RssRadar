package com.cycling.rssradar.core.domain.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTextBuilderTest {

    @Test
    fun `纯中文切成相邻双字组`() {
        assertEquals("人工 工智 智能", SearchTextBuilder.segment("人工智能"))
    }

    @Test
    fun `单个汉字保留原样`() {
        assertEquals("人", SearchTextBuilder.segment("人"))
    }

    @Test
    fun `英文词转小写并保留`() {
        assertEquals("hello world", SearchTextBuilder.segment("Hello World"))
    }

    @Test
    fun `中英混排各自成词`() {
        assertEquals("ai 人工 工智 智能", SearchTextBuilder.segment("AI 人工智能"))
    }

    @Test
    fun `标点被丢弃不产生空词`() {
        assertEquals("你好 世界", SearchTextBuilder.segment("你好，世界！"))
    }

    @Test
    fun `英文与数字视为同一个词`() {
        assertEquals("rss2json2026", SearchTextBuilder.segment("RSS2JSON2026"))
    }

    @Test
    fun `空串返回空`() {
        assertEquals("", SearchTextBuilder.segment(""))
    }

    @Test
    fun `纯标点返回空`() {
        assertEquals("", SearchTextBuilder.segment("!!! ，。"))
    }

    @Test
    fun `截断落在词边界不切半个词`() {
        assertEquals("人工 工智", SearchTextBuilder.segment("人工智能", maxChars = 5))
    }

    @Test
    fun `短于上限时原样返回`() {
        assertEquals("人工 工智 智能", SearchTextBuilder.segment("人工智能", maxChars = 64))
    }

    @Test
    fun `假名与片假名按同一连续段切双字组`() {
        assertEquals("かな なカ カナ", SearchTextBuilder.segment("かなカナ"))
    }
}
