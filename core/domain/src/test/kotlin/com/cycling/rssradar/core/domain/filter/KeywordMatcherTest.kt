package com.cycling.rssradar.core.domain.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordMatcherTest {

    @Test
    fun `默认忽略大小写`() {
        assertTrue(KeywordMatcher.matches("Hello World", "hello"))
    }

    @Test
    fun `区分大小写时不匹配`() {
        assertFalse(KeywordMatcher.matches("Hello World", "hello", caseSensitive = true))
    }

    @Test
    fun `关键词前后空白被裁掉`() {
        assertTrue(KeywordMatcher.matches("hello", "  hello  "))
    }

    @Test
    fun `空关键词不匹配`() {
        assertFalse(KeywordMatcher.matches("hello", "   "))
    }

    @Test
    fun `空文本不匹配`() {
        assertFalse(KeywordMatcher.matches("", "hello"))
    }

    @Test
    fun `整词匹配要求字母数字边界`() {
        assertFalse(KeywordMatcher.matches("scatter", "cat", wholeWord = true))
        assertTrue(KeywordMatcher.matches("a cat here", "cat", wholeWord = true))
    }

    @Test
    fun `整词匹配在词首词尾也算命中`() {
        assertTrue(KeywordMatcher.matches("cat", "cat", wholeWord = true))
        assertTrue(KeywordMatcher.matches("cat dog", "cat", wholeWord = true))
    }

    @Test
    fun `整词匹配会继续找下一个候选`() {
        assertTrue(KeywordMatcher.matches("scatter cat", "cat", wholeWord = true))
    }

    @Test
    fun `任一词命中即算命中`() {
        assertTrue(KeywordMatcher.matchesAny("今天有优惠", listOf("广告", "优惠")))
        assertFalse(KeywordMatcher.matchesAny("普通内容", listOf("广告", "优惠")))
    }

    @Test
    fun `空关键词列表不命中`() {
        assertFalse(KeywordMatcher.matchesAny("任意内容", emptyList()))
    }

    @Test
    fun `解析关键词支持中英文逗号与换行`() {
        assertEquals(
            listOf("广告", "推广", "spam"),
            KeywordMatcher.parseKeywords("广告，推广, spam\n"),
        )
    }

    @Test
    fun `解析关键词去重并丢空项`() {
        assertEquals(listOf("a", "b"), KeywordMatcher.parseKeywords("a, ,b,a"))
    }
}
