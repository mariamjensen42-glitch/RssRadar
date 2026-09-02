package com.cycling.rssradar.ui.article

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** 上标 / 下标（Compose 侧映射到 BaselineShift）。 */
internal enum class MathScript { NORMAL, SUPER, SUB }

/**
 * 一段带脚本位置的公式文本。
 * 只保留「排版得出来」的信息：文字、上下标、变量斜体——不假装自己是数学排版引擎。
 */
internal data class MathSpan(
    val text: String,
    val script: MathScript = MathScript.NORMAL,
    /** 数学惯例：单字母变量用斜体（<mi>），数字与运算符正体（<mn>/<mo>）。 */
    val italic: Boolean = false,
)

/**
 * MathML → 可读文本片段（纯 JVM：只依赖 jsoup）。
 *
 * 为什么能拿到 MathML：`RssParser.sanitizeHtml` 会剥掉所有属性（class 全丢，所以认不出
 * `katex-mathml`），但**保留元素结构**——KaTeX 与 MathJax(CHTML) 都会在页面里留一份
 * `<math>` 辅助标记，实测过 sanitize 后 `msup`/`mfrac`/`mi`/`mn`/`mo` 都还在。
 * 于是：认 `<math>` 标签，不认 class。
 *
 * 渲染策略（务实，不吹牛）：
 * - 上下标 → 真正的上/下标（Compose 的 BaselineShift），这是最值钱的部分；
 * - 分式 → `分子⁄分母`（U+2044 分数线字符），需要时补括号；
 * - 根号 → `√(…)`；
 * - 表格 → 换行 + 列间空格；
 * - 不认识的标签一律递归取内容——宁可线性化得难看，也不能把内容吞掉。
 * MathJax 的 SVG 输出模式拿不到（sanitize 直接删 `<svg>`），那种页面只能看降级文本。
 */
internal object MathMl {

    /** U+2044 FRACTION SLASH：字体里通常渲染成斜分数线，比普通 / 更像分数。 */
    private const val FRACTION_SLASH = "⁄"

    /** 极简结构不需要加括号（加了反而噪声）。 */
    private val BARE_TAGS = setOf("mn", "mi", "ms", "mtext", "msup", "msub", "msubsup", "msqrt")

    fun parse(mathHtml: String): List<MathSpan> {
        val math = runCatching { Jsoup.parseBodyFragment(mathHtml).selectFirst("math") }.getOrNull()
            ?: return emptyList()
        val out = mutableListOf<MathSpan>()
        collect(math, MathScript.NORMAL, italic = false, out)
        return out.filter { it.text.isNotEmpty() }
    }

    private fun collect(node: Node, script: MathScript, italic: Boolean, out: MutableList<MathSpan>) {
        when (node) {
            is TextNode -> {
                // 数学 token 里的空白无意义（显式空白是 <mspace>）：压缩并裁边
                val text = collapseWs(node.text())
                if (text.isNotEmpty()) out += MathSpan(text, script, italic)
            }
            is Element -> when (val tag = node.tagName().lowercase()) {
                // 变量斜体（单字母），数字与运算符正体
                "mi" -> children(node, script, italic = true, out)
                "mn", "mo", "mtext", "ms" -> children(node, script, italic = false, out)

                "mrow", "mstyle", "mpadded", "merror", "menclose", "maction", "mmultiscripts",
                "mfenced",
                -> children(node, script, italic, out)

                "mphantom" -> Unit // 用于对齐的占位，不可见
                "mspace" -> out += MathSpan(" ", script, italic)

                "msup" -> scripted(node, script, italic, out, second = MathScript.SUPER)
                "msub" -> scripted(node, script, italic, out, second = MathScript.SUB)
                "msubsup" -> {
                    val kids = node.children()
                    kids.getOrNull(0)?.let { collect(it, script, italic, out) }
                    kids.getOrNull(1)?.let { collect(it, MathScript.SUB, italic, out) }
                    kids.getOrNull(2)?.let { collect(it, MathScript.SUPER, italic, out) }
                }

                // munder 的第二个子元素在下方（SUB），mover 在上方（SUPER），
                // munderover = base + 下 + 上
                "munder" -> {
                    val kids = node.children()
                    kids.getOrNull(0)?.let { collect(it, script, italic, out) }
                    kids.getOrNull(1)?.let { collect(it, MathScript.SUB, italic, out) }
                }
                "mover" -> {
                    val kids = node.children()
                    kids.getOrNull(0)?.let { collect(it, script, italic, out) }
                    kids.getOrNull(1)?.let { collect(it, MathScript.SUPER, italic, out) }
                }
                "munderover" -> {
                    val kids = node.children()
                    kids.getOrNull(0)?.let { collect(it, script, italic, out) }
                    kids.getOrNull(1)?.let { collect(it, MathScript.SUB, italic, out) }
                    kids.getOrNull(2)?.let { collect(it, MathScript.SUPER, italic, out) }
                }

                "mfrac" -> {
                    val kids = node.children()
                    parens(kids.getOrNull(0), script, italic, out)
                    out += MathSpan(FRACTION_SLASH, script, false)
                    parens(kids.getOrNull(1), script, italic, out)
                }

                "msqrt" -> {
                    out += MathSpan("√(", script, false)
                    children(node, script, italic, out)
                    out += MathSpan(")", script, false)
                }

                // <mroot> 的子元素顺序是 radicand 在前、index 在后
                "mroot" -> {
                    val kids = node.children()
                    kids.getOrNull(1)?.let { collect(it, MathScript.SUPER, italic, out) }
                    out += MathSpan("√(", script, false)
                    kids.getOrNull(0)?.let { collect(it, script, italic, out) }
                    out += MathSpan(")", script, false)
                }

                "mtable" -> {
                    val rows = node.select("mtr").ifEmpty { node.children() }
                    rows.forEachIndexed { index, row ->
                        if (index > 0) out += MathSpan("\n", script, false)
                        val cells = row.select("mtd").ifEmpty { row.children() }
                        cells.forEachIndexed { cellIndex, cell ->
                            if (cellIndex > 0) out += MathSpan("  ", script, false)
                            collect(cell, script, italic, out)
                        }
                    }
                }

                // <semantics> 里第一个子元素是 MathML 本体，后面的 annotation（TeX 源码等）不要
                "semantics" -> node.children()
                    .firstOrNull { !it.tagName().equals("annotation", ignoreCase = true) }
                    ?.let { collect(it, script, italic, out) }

                "annotation", "annotation-xml" -> Unit

                else -> children(node, script, italic, out)
            }
        }
    }

    private fun children(el: Element, script: MathScript, italic: Boolean, out: MutableList<MathSpan>) {
        for (child in el.childNodes()) collect(child, script, italic, out)
    }

    /** 压缩空白并裁掉首尾（数学 token 内容里不该有空格，显式空白是 mspace 的职责）。 */
    private fun collapseWs(text: String): String =
        if (text.isBlank()) "" else text.trim().replace(WHITESPACE, " ")

    private val WHITESPACE = Regex("\\s+")

    /** msup / msub：第一个子元素按原样，第二个升为上标或下标。 */
    private fun scripted(
        el: Element,
        script: MathScript,
        italic: Boolean,
        out: MutableList<MathSpan>,
        second: MathScript,
    ) {
        val kids = el.children()
        kids.getOrNull(0)?.let { collect(it, script, italic, out) }
        kids.getOrNull(1)?.let { collect(it, second, italic, out) }
    }

    /** 分子/分母：结构简单就直接写，结构复杂补括号避免歧义。 */
    private fun parens(el: Element?, script: MathScript, italic: Boolean, out: MutableList<MathSpan>) {
        if (el == null) return
        val need = el.tagName().lowercase() !in BARE_TAGS
        if (need) out += MathSpan("(", script, false)
        collect(el, script, italic, out)
        if (need) out += MathSpan(")", script, false)
    }
}
