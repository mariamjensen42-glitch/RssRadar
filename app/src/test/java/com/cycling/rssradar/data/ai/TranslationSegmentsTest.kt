package com.cycling.rssradar.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TranslationSegments] 纯函数单测：分段确定性、块长约束、裸文本包装、空输入。
 * 对标 AiTextTest 风格，纯 JVM（jsoup 可用）。
 */
class TranslationSegmentsTest {

    @Test
    fun `split - empty and blank html return empty list`() {
        assertEquals(emptyList<String>(), TranslationSegments.split(""))
        assertEquals(emptyList<String>(), TranslationSegments.split("   \n  "))
    }

    @Test
    fun `split - deterministic for same input`() {
        val html = "<p>第一段</p><p>Second paragraph</p><h2>标题</h2><p>第三段内容更长一些。</p>"
        assertEquals(TranslationSegments.split(html), TranslationSegments.split(html))
    }

    @Test
    fun `split - preserves order and keeps block html intact`() {
        val chunks = TranslationSegments.split("<p>alpha</p><h2>beta</h2><p>gamma</p>")
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].contains("<p>alpha</p>"))
        assertTrue(chunks[0].contains("<h2>beta</h2>"))
        assertTrue(chunks[0].contains("<p>gamma</p>"))
    }

    @Test
    fun `split - chunks never exceed max unless single block is larger`() {
        val small = (1..50).joinToString("") { "<p>段落 $it：${"内容".repeat(30)}</p>" }
        val chunks = TranslationSegments.split(small)
        assertTrue(chunks.size > 1)
        // 单块都不超限时，任何块都不得超上限（确定性切分的硬约束）
        chunks.forEach { chunk ->
            assertTrue("chunk 超限：${chunk.length}", chunk.length <= TranslationSegments.MAX_CHUNK_CHARS)
        }
    }

    @Test
    fun `split - oversized single block becomes its own chunk`() {
        val huge = "<p>${"很长的一段".repeat(1000)}</p>" // ~6000 字符，远超单块上限
        val chunks = TranslationSegments.split("<p>开头</p>$huge<p>结尾</p>")
        // 开头/结尾各自成块，huge 独占一块且不被切散
        assertTrue(chunks.any { it.startsWith("<p>") && it.length > TranslationSegments.MAX_CHUNK_CHARS })
    }

    @Test
    fun `split - bare text nodes are wrapped in p tags`() {
        val chunks = TranslationSegments.split("裸文本一段<p>块级段落</p>另一段裸文本")
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].contains("<p>裸文本一段</p>"))
        assertTrue(chunks[0].contains("<p>另一段裸文本</p>"))
        assertTrue(chunks[0].contains("<p>块级段落</p>"))
    }

    @Test
    fun `split - whitespace-only text nodes are dropped`() {
        val chunks = TranslationSegments.split("<p>a</p>\n\n   <p>b</p>")
        assertEquals(1, chunks.size)
        assertTrue(!chunks[0].contains("<p>   </p>"))
    }

    @Test
    fun `split - comments and nested structure survive as top-level units`() {
        val html = "<!-- 注释 --><blockquote><p>引用里的段落</p></blockquote><p>正文</p>"
        val chunks = TranslationSegments.split(html)
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].contains("<blockquote>"))
        // 注释不进分段（模型只该看到正文标签）
        assertTrue(!chunks[0].contains("注释"))
    }
}
