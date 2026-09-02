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
        // 800 非空白字符 / 6 ≈ 133 词；133 词 @ 200 词/分 = 0 分 → 兜底 +1 = 1
        val text = "word".repeat(200)
        assertEquals(1, estimateReadingMinutes(text))
    }

    @Test
    fun `短文本至少 1 分钟`() {
        assertEquals(1, estimateReadingMinutes("hi"))
    }
}
