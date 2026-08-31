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

    // ---- splitBlocks：双语一一对应的配对单位 ----

    @Test
    fun `splitBlocks - every top level block becomes its own unit`() {
        val blocks = TranslationSegments.splitBlocks(
            "<h2>标题</h2><p>第一段</p><ul><li>项一</li><li>项二</li></ul><p>第二段</p>",
        )
        assertEquals(4, blocks.size) // h2 / p / ul(整块) / p
        assertEquals("<h2>标题</h2>", blocks[0])
        assertEquals("<p>第一段</p>", blocks[1])
        assertTrue(blocks[2].startsWith("<ul>"))
        assertEquals("<p>第二段</p>", blocks[3])
    }

    @Test
    fun `splitBlocks - empty html returns empty list`() {
        assertEquals(emptyList<String>(), TranslationSegments.splitBlocks(""))
        assertEquals(emptyList<String>(), TranslationSegments.splitBlocks("   "))
    }

    // ---- pairBlocks：原文块 ↔ 译文块逐一对应 ----

    @Test
    fun `pairBlocks - one to one by index preserves block order`() {
        val original = "<p>one</p><h2>title</h2><p>two</p>"
        val translated = "<p>一</p><h2>标题</h2><p>二</p>"
        val pairs = TranslationSegments.pairBlocks(listOf(original to translated))
        assertEquals(3, pairs.size)
        assertEquals(listOf("<p>one</p>", "<h2>title</h2>", "<p>two</p>"), pairs.map { it.originalHtml })
        assertEquals(listOf("<p>一</p>", "<h2>标题</h2>", "<p>二</p>"), pairs.map { it.translatedHtml })
    }

    @Test
    fun `pairBlocks - untranslated chunk keeps originals with null translation`() {
        val pairs = TranslationSegments.pairBlocks(listOf("<p>a</p><p>b</p>" to null))
        assertEquals(2, pairs.size)
        assertTrue(pairs.all { it.translatedHtml == null })
    }

    @Test
    fun `pairBlocks - model returns fewer blocks leaves the tail untranslated`() {
        val pairs = TranslationSegments.pairBlocks(listOf("<p>a</p><p>b</p><p>c</p>" to "<p>甲</p>"))
        assertEquals(3, pairs.size)
        assertEquals("<p>甲</p>", pairs[0].translatedHtml)
        assertEquals(null, pairs[1].translatedHtml)
        assertEquals(null, pairs[2].translatedHtml)
    }

    @Test
    fun `pairBlocks - model returns more blocks keeps extra translations at the tail`() {
        val pairs = TranslationSegments.pairBlocks(listOf("<p>a</p>" to "<p>甲</p><p>乙</p>"))
        assertEquals(2, pairs.size)
        assertEquals("<p>甲</p>", pairs[0].translatedHtml)
        assertEquals("<p>乙</p>", pairs[1].translatedHtml)
        assertEquals("", pairs[1].originalHtml) // 多出的译文没有对应原文，不凭空造原文
    }

    @Test
    fun `pairBlocks - multiple chunks keep global block order`() {
        val pairs = TranslationSegments.pairBlocks(
            listOf(
                "<p>a1</p><p>a2</p>" to "<p>甲一</p><p>甲二</p>",
                "<p>b1</p>" to null,
            ),
        )
        // 块序 = 原文块序：a1, a2, b1（译文按索引对位）
        assertEquals(listOf("<p>a1</p>", "<p>a2</p>", "<p>b1</p>"), pairs.map { it.originalHtml })
        assertEquals(listOf("<p>甲一</p>", "<p>甲二</p>", null), pairs.map { it.translatedHtml })
    }
}
