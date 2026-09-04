package com.cycling.rssradar.core.data.ai

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * 正文 HTML → 翻译分段的分层真相源（渐进式翻译）。
 *
 * **两级单位，都住在这个模块里**：
 * - **chunk（翻译分块）**：一次 API 往返的原文块组，上限 [MAX_CHUNK_CHARS] 字符。
 * - **block（顶层块）**：chunk 内的段落/标题/列表项/引用/代码块，是双语对照的配对单位。
 *
 * 两级都在这里定，是因为它们是一对不变量：chunk 由 block 聚合而成，配对又必须按
 * block 边界走。此前 chunk 在 [AiRepository] 切、block 在 UI 侧二次切，两级单位
 * 各说各话，只能靠两处注释维持共识；现在 [chunk] 一次给出两级，下游只消费结果。
 *
 * 为什么不整篇一把梭：整篇翻译要等全部完成才能显示，长文等 1~2 分钟；
 * 按块切成分段后逐段调 API，翻完一段亮一段。
 *
 * 切分是确定性的：同一份 HTML 永远切出同样的段序，这是缓存对齐（缓存按段序存译文）
 * 和 UI 分段渲染的前提。空 HTML 返回空表——调用方据此报"没有可翻译的内容"。
 */

/** 一段原文与其对应译文的块级配对（双语一一对应的最小渲染单元）。 */
data class TranslationBlockPair(
    val originalHtml: String,
    /** null = 该块尚未翻出译文（渐进显示中）。 */
    val translatedHtml: String?,
)

/**
 * 一个翻译分段：chunk 与其内部的 block 边界一次给出。
 *
 * [blocks] 随分段一路带到渲染侧，渲染侧就不必再把原文切第二遍
 * （译文侧是模型新产出的内容，才需要重新切，见 [TranslationSegments.pair]）。
 */
data class TranslationChunk(
    /** 本次 API 往返送出去的原文（[TranslationSegments.MAX_CHUNK_CHARS] 字符上限）。 */
    val html: String,
    /** 其内的顶层块，双语对照的配对单位。 */
    val blocks: List<String>,
)

/**
 * [TranslationSegments.pair] 的输入：一个分段的原文块边界 + 模型翻出来的该段 HTML。
 * 原文块边界来自 [TranslationSegments.chunk]，译文是新的，需要现场切块。
 */
data class TranslationPairInput(
    val originalBlocks: List<String>,
    val translatedHtml: String?,
)

object TranslationSegments {

    /** 单块字符上限（合成一次 API 请求的上限，不是渲染配对单位）。 */
    const val MAX_CHUNK_CHARS = 1_800

    /**
     * 两级切分的唯一入口：chunk（API 往返单位）+ 其内的 block（双语配对单位）一次切好。
     *
     * 切法：按 body 顶层节点聚合，凑满 [MAX_CHUNK_CHARS] 就开新块；裸文本节点包一层
     * `<p>`（翻译 prompt 只认标签间文本）；超长的单个块（长代码块/大表格）不强行二次
     * 切分——单块就是它的 chunk，交给 prompt 长度保护兜底。
     */
    fun chunk(html: String): List<TranslationChunk> {
        val blocks = splitBlocks(html)
        if (blocks.isEmpty()) return emptyList()
        val chunks = mutableListOf<TranslationChunk>()
        val buffer = StringBuilder()
        val buffered = mutableListOf<String>()
        for (block in blocks) {
            if (buffer.isNotEmpty() && buffer.length + block.length > MAX_CHUNK_CHARS) {
                chunks.add(TranslationChunk(buffer.toString(), buffered.toList()))
                buffer.setLength(0)
                buffered.clear()
            }
            buffer.append(block)
            buffered.add(block)
        }
        if (buffer.isNotBlank()) chunks.add(TranslationChunk(buffer.toString(), buffered.toList()))
        return chunks
    }

    /** 只要 chunk 序列（不需要块边界）时用这个；等价于 [chunk] 取 [TranslationChunk.html]。 */
    fun split(html: String): List<String> = chunk(html).map { it.html }

    /**
     * 逐块切分：一个顶层块（段落/标题/列表/引用/代码块/表格…）产出一段 HTML。
     * 这是双语对照的配对单位——标题、列表项各自独立成对，不合并、不拆分。
     * 裸文本节点包一层 `<p>`；空白文本与注释丢弃。
     */
    fun splitBlocks(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parseBodyFragment(html)
        // 关掉 prettyPrint：否则 outerHtml 会在块级标签间注入换行/缩进，输出
        // 含无关空白（白耗翻译 token，也让块级配对/断言不稳定）
        doc.outputSettings().prettyPrint(false)
        val blocks = mutableListOf<String>()
        for (node in doc.body().childNodes()) {
            val piece = when (node) {
                is Element -> node.outerHtml()
                is TextNode -> if (node.text().isBlank()) null else "<p>${node.outerHtml()}</p>"
                else -> null // 注释等杂节点直接丢
            } ?: continue
            blocks.add(piece)
        }
        return blocks
    }

    /**
     * 把翻译分段摊平成逐块一一对应的配对表。
     *
     * 块序严格对齐：原文第 n 块 ↔ 译文第 n 块（prompt 要求模型保持块数与顺序不变）。
     * 原文块边界由 [TranslationChunk.blocks] 给定——切分是 [chunk] 的事，这里不再切第二遍；
     * 只有译文侧是模型新产出的内容，需要现场 [splitBlocks]。
     *
     * 模型没做到时（译文块数少了/多了）如实降级——少则缺的块没有译文（渐进中表现为
     * 待译），多则多出的译文块挂在末尾，绝不为了凑对齐而合并或重排原文块。
     */
    fun pair(inputs: List<TranslationPairInput>): List<TranslationBlockPair> {
        val pairs = mutableListOf<TranslationBlockPair>()
        for (input in inputs) {
            val translated = input.translatedHtml?.let { splitBlocks(it) }
            if (translated == null) {
                input.originalBlocks.forEach { pairs.add(TranslationBlockPair(it, null)) }
                continue
            }
            val count = maxOf(input.originalBlocks.size, translated.size)
            for (i in 0 until count) {
                pairs.add(
                    TranslationBlockPair(
                        originalHtml = input.originalBlocks.getOrNull(i) ?: "",
                        translatedHtml = translated.getOrNull(i),
                    ),
                )
            }
        }
        return pairs
    }
}
