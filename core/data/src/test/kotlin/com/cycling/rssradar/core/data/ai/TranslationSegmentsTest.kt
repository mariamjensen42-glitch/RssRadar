package com.cycling.rssradar.core.data.ai

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

    // ---- pair：原文块 ↔ 译文块逐一对应（原文块边界由 chunk 给定） ----

    /** 按 UI 侧的真实用法构造输入：原文块边界来自 chunk，译文是模型新产出的 HTML。 */
    private fun pairOf(original: String, translated: String?) =
        TranslationSegments.pair(
            listOf(
                TranslationPairInput(
                    originalBlocks = TranslationSegments.splitBlocks(original),
                    translatedHtml = translated,
                ),
            ),
        )

    @Test
    fun `pair - one to one by index preserves block order`() {
        val pairs = pairOf("<p>one</p><h2>title</h2><p>two</p>", "<p>一</p><h2>标题</h2><p>二</p>")
        assertEquals(3, pairs.size)
        assertEquals(listOf("<p>one</p>", "<h2>title</h2>", "<p>two</p>"), pairs.map { it.originalHtml })
        assertEquals(listOf("<p>一</p>", "<h2>标题</h2>", "<p>二</p>"), pairs.map { it.translatedHtml })
    }

    @Test
    fun `pair - untranslated chunk keeps originals with null translation`() {
        val pairs = pairOf("<p>a</p><p>b</p>", null)
        assertEquals(2, pairs.size)
        assertTrue(pairs.all { it.translatedHtml == null })
    }

    @Test
    fun `pair - model returns fewer blocks leaves the tail untranslated`() {
        val pairs = pairOf("<p>a</p><p>b</p><p>c</p>", "<p>甲</p>")
        assertEquals(3, pairs.size)
        assertEquals("<p>甲</p>", pairs[0].translatedHtml)
        assertEquals(null, pairs[1].translatedHtml)
        assertEquals(null, pairs[2].translatedHtml)
    }

    @Test
    fun `pair - model returns more blocks keeps extra translations at the tail`() {
        val pairs = pairOf("<p>a</p>", "<p>甲</p><p>乙</p>")
        assertEquals(2, pairs.size)
        assertEquals("<p>甲</p>", pairs[0].translatedHtml)
        assertEquals("<p>乙</p>", pairs[1].translatedHtml)
        assertEquals("", pairs[1].originalHtml) // 多出的译文没有对应原文，不凭空造原文
    }

    @Test
    fun `pair - multiple chunks keep global block order`() {
        val pairs = TranslationSegments.pair(
            listOf(
                TranslationPairInput(
                    originalBlocks = TranslationSegments.splitBlocks("<p>a1</p><p>a2</p>"),
                    translatedHtml = "<p>甲一</p><p>甲二</p>",
                ),
                TranslationPairInput(
                    originalBlocks = TranslationSegments.splitBlocks("<p>b1</p>"),
                    translatedHtml = null,
                ),
            ),
        )
        // 块序 = 原文块序：a1, a2, b1（译文按索引对位）
        assertEquals(listOf("<p>a1</p>", "<p>a2</p>", "<p>b1</p>"), pairs.map { it.originalHtml })
        assertEquals(listOf("<p>甲一</p>", "<p>甲二</p>", null), pairs.map { it.translatedHtml })
    }

    // ---- 两级单位的一致性：chunk 与 block 必须是同一套边界 ----

    @Test
    fun `chunk - blocks are exactly the concatenation of the chunk html`() {
        val html = "<p>one</p><h2>title</h2><p>two</p>"

        val chunks = TranslationSegments.chunk(html)

        assertEquals(1, chunks.size)
        // chunk.html 拼接起来 === 逐块切出来的块，边界是同一套
        assertEquals(TranslationSegments.splitBlocks(html), chunks.single().blocks)
        assertEquals(chunks.single().blocks.joinToString(""), chunks.single().html)
    }

    @Test
    fun `chunk - every block lands in exactly one chunk`() {
        // 撑到必然分成多个 chunk
        val longHtml = (1..60).joinToString("") { "<p>第 ${it} 段，内容paddingpaddingpaddingpadding</p>" }

        val chunks = TranslationSegments.chunk(longHtml)
        assertTrue("应切成多个 chunk", chunks.size > 1)

        val chunkedBlocks = chunks.flatMap { it.blocks }
        assertEquals(TranslationSegments.splitBlocks(longHtml), chunkedBlocks)
        // 不重不漏：块总数守恒
        assertEquals(TranslationSegments.splitBlocks(longHtml).size, chunkedBlocks.size)
        assertTrue(chunks.all { it.html == it.blocks.joinToString("") })
    }

    @Test
    fun `split - equals chunk htmls`() {
        val html = (1..60).joinToString("") { "<p>第 ${it} 段，内容paddingpaddingpaddingpadding</p>" }

        assertEquals(TranslationSegments.chunk(html).map { it.html }, TranslationSegments.split(html))
    }
}
