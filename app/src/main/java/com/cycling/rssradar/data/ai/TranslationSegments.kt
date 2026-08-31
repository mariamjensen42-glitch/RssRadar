package com.cycling.rssradar.data.ai

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * 正文 HTML → 翻译分段的纯函数层（渐进式翻译的分段真相源）。
 *
 * 为什么不整篇一把梭：整篇翻译要等全部完成才能显示，长文等 1~2 分钟；
 * 按块切成分段后逐段调 API，翻完一段亮一段。块大小取 1800 字符是折中——
 * 太碎则请求次数多（每段一次 API 往返），太大则渐进粒度退化回"等半天"。
 *
 * 切法：按 body 顶层节点聚合，凑满 [MAX_CHUNK_CHARS] 就开新块；裸文本节点包一层
 * `<p>`（翻译 prompt 只认标签间文本）；超长的单个块（长代码块/大表格）不强行二次
 * 切分——单块就是它的 chunk，交给 prompt 长度保护兜底。
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

object TranslationSegments {

    /** 单块字符上限（合成一次 API 请求的上限，不是渲染配对单位）。 */
    const val MAX_CHUNK_CHARS = 1_800

    fun split(html: String): List<String> {
        val blocks = splitBlocks(html)
        val chunks = mutableListOf<String>()
        val buffer = StringBuilder()
        for (block in blocks) {
            if (buffer.isNotEmpty() && buffer.length + block.length > MAX_CHUNK_CHARS) {
                chunks.add(buffer.toString())
                buffer.setLength(0)
            }
            buffer.append(block)
        }
        if (buffer.isNotBlank()) chunks.add(buffer.toString())
        return chunks
    }

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
     * 把翻译分段（每次 API 往返的原文块组 + 其译文）摊平成逐块一一对应的配对表。
     *
     * 块序严格对齐：原文第 n 块 ↔ 译文第 n 块（prompt 要求模型保持块数与顺序不变）。
     * 模型没做到时（译文块数少了/多了）如实降级——少则缺的块没有译文（渐进中表现为
     * 待译），多则多出的译文块挂在末尾，绝不为了凑对齐而合并或重排原文块。
     */
    fun pairBlocks(chunks: List<Pair<String, String?>>): List<TranslationBlockPair> {
        val pairs = mutableListOf<TranslationBlockPair>()
        for ((originalChunk, translatedChunk) in chunks) {
            val originals = splitBlocks(originalChunk)
            val translated = translatedChunk?.let { splitBlocks(it) }
            if (translated == null) {
                originals.forEach { pairs.add(TranslationBlockPair(it, null)) }
                continue
            }
            val count = maxOf(originals.size, translated.size)
            for (i in 0 until count) {
                pairs.add(
                    TranslationBlockPair(
                        originalHtml = originals.getOrNull(i) ?: "",
                        translatedHtml = translated.getOrNull(i),
                    ),
                )
            }
        }
        return pairs
    }
}
