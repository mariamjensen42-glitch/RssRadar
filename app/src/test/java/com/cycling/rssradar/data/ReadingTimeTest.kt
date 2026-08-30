package com.cycling.rssradar.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 阅读时长估算：来自真实正文字数，不虚构（产品核心原则）。 */
class ReadingTimeTest {

    @Test
    fun `中文按 300 字每分钟`() {
        val text = "字".repeat(600)
        assertEquals(3, estimateReadingMinutes(text))
    }

    @Test
    fun `英文按 200 词每分钟（6 字符约 1 词）`() {
        // 200 × 4 字符 = 800 非空白字符 → 800/6 = 133 分钟 → 134
        val text = "word".repeat(200)
        assertEquals(134, estimateReadingMinutes(text))
    }

    @Test
    fun `短文本至少 1 分钟`() {
        assertEquals(1, estimateReadingMinutes("hi"))
    }
}
