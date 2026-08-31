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
object TranslationSegments {

    /** 单块字符上限。 */
    const val MAX_CHUNK_CHARS = 1_800

    fun split(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parseBodyFragment(html)
        // 关掉 prettyPrint：否则 outerHtml 会在块级标签间注入换行/缩进，分段输出
        // 含无关空白（白耗翻译 token，也让纯文本包装的断言/对齐不稳定）
        doc.outputSettings().prettyPrint(false)
        val body = doc.body()
        val chunks = mutableListOf<String>()
        val buffer = StringBuilder()
        for (node in body.childNodes()) {
            val piece = when (node) {
                is Element -> node.outerHtml()
                is TextNode -> if (node.text().isBlank()) null else "<p>${node.outerHtml()}</p>"
                else -> null // 注释等杂节点直接丢
            } ?: continue
            if (buffer.isNotEmpty() && buffer.length + piece.length > MAX_CHUNK_CHARS) {
                chunks.add(buffer.toString())
                buffer.setLength(0)
            }
            buffer.append(piece)
        }
        if (buffer.isNotBlank()) chunks.add(buffer.toString())
        return chunks
    }
}
