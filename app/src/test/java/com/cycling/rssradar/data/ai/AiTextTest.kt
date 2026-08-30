package com.cycling.rssradar.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AiText] 纯函数单测（issue #44）：语言预检阈值边界、截断与标注、DeepSeek 响应解析。
 * 对标 RssParserTest 风格，纯 JVM。
 */
class AiTextTest {

    // ---- chineseCharRatio / isMostlyChinese ----

    @Test
    fun `chineseRatio - pure english is zero`() {
        assertEquals(0.0, AiText.chineseCharRatio("Hello world, this is RSS."), 1e-9)
        assertFalse(AiText.isMostlyChinese("Hello world, this is RSS."))
    }

    @Test
    fun `chineseRatio - pure chinese is one`() {
        assertEquals(1.0, AiText.chineseCharRatio("这是一篇中文文章"), 1e-9)
        assertTrue(AiText.isMostlyChinese("这是一篇中文文章"))
    }

    @Test
    fun `chineseRatio - no letters returns zero not NaN`() {
        assertEquals(0.0, AiText.chineseCharRatio("12345 !@#"), 1e-9)
        assertEquals(0.0, AiText.chineseCharRatio(""), 1e-9)
        assertFalse(AiText.isMostlyChinese("12345 !@#"))
    }

    @Test
    fun `chineseRatio - exactly at threshold is not mostly chinese`() {
        // 3 中文 + 7 字母 = 0.3，恰好等于阈值（判定条件是 > 0.3）→ 不算中文文章
        val text = "中中文" + "abcdefg"
        assertEquals(0.3, AiText.chineseCharRatio(text), 1e-9)
        assertFalse(AiText.isMostlyChinese(text))
    }

    // ---- truncateForPrompt / truncationNote ----

    @Test
    fun `truncate - short text passes through unmarked`() {
        val (text, truncated) = AiText.truncateForPrompt("短文")
        assertEquals("短文", text)
        assertFalse(truncated)
    }

    @Test
    fun `truncate - long text cut to max and marked`() {
        val long = "a".repeat(AiText.MAX_INPUT_CHARS + 100)
        val (text, truncated) = AiText.truncateForPrompt(long)
        assertEquals(AiText.MAX_INPUT_CHARS, text.length)
        assertTrue(truncated)
        assertEquals("（原文较长，本摘要基于前 ${AiText.MAX_INPUT_CHARS} 字）", AiText.truncationNote(text.length))
    }

    // ---- parseChatCompletion ----

    @Test
    fun `parse - valid response extracts content`() {
        val json = """{"id":"x","choices":[{"index":0,"message":{"role":"assistant","content":"你好，这是摘要。"},"finish_reason":"stop"}],"usage":{"total_tokens":10}}"""
        assertEquals("你好，这是摘要。", AiText.parseChatCompletion(json))
    }

    @Test
    fun `parse - blank content returns null`() {
        val json = """{"choices":[{"message":{"content":"   "}}]}"""
        assertNull(AiText.parseChatCompletion(json))
    }

    @Test
    fun `parse - malformed json and wrong shape return null`() {
        assertNull(AiText.parseChatCompletion("not json at all"))
        assertNull(AiText.parseChatCompletion("""{"choices":[]}"""))
        assertNull(AiText.parseChatCompletion("""{"error":{"message":"rate limited"}}"""))
    }
}
