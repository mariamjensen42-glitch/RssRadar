package com.cycling.rssradar.ui.article

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * 正文 HTML → Compose 中间树的**纯 JVM 解析**（ADR-0009 原生渲染器的解析半边）。
 *
 * 与渲染分离的理由和 [ReadingContentHtml] / [ReadingImages] 一样：解析是可测的纯函数，
 * 不碰 Android、不碰 Compose，单测直接覆盖；渲染那边只剩「树 → Composable」的直译。
 *
 * 输入是 [com.cycling.rssradar.data.parser.RssParser.sanitizeHtml] 的产物（相对路径与
 * 危险属性已剥掉），但**不依赖**这一点：凡是解析出来的 URL 都要再过 [absoluteUrl]，
 * 协议相对（`//host/x`）补 https，`mailto:`/`javascript:` 之类一律降级成纯文本。
 *
 * 三条硬约束（畸形/恶意 feed 的防线，缺一条就是线上崩溃源）：
 * 1. **深度上限** [MAX_DEPTH]：递归解析被打爆的嵌套标签（`div` 套一万层）打成
 *    StackOverflowError —— 这是 Error 不是 Exception，兜不住就该在源头截断。
 * 2. **节点总量上限** [MAX_BLOCKS]：超长正文只渲染前 N 个块，避免一次组合上千个 Text
 *    把主线程钉死在组合阶段（渲染器的组合开销与节点数线性相关）。
 * 3. **整段兜底**：[parse] 里所有 Throwable（含解析器自身抛的）都被吞成空列表——
 *    解析失败是「这篇读不了」，不该是「App 崩了」。调用方因此必须能在空树时回退 WebView。
 */
internal object ReadingNodes {

    /** 递归深度上限（块级与行内共用）。超过就停止下钻，已收集的内容照常返回。 */
    const val MAX_DEPTH = 24

    /** 单篇文章渲染的块级节点上限。超出的正文不渲染（宁缺毋卡）。 */
    const val MAX_BLOCKS = 800

    /** sanitize 生成的媒体占位卡类名。 */
    const val MEDIA_CARD_CLASS = "media-card"

    /** 块级标签：决定一个元素是「拆成子块」还是「当一段行内文本」。 */
    private val BLOCK_TAGS = setOf(
        "p", "div", "ul", "ol", "table", "pre", "blockquote",
        "figure", "figcaption", "hr", "img", "section", "article",
        "h1", "h2", "h3", "h4", "h5", "h6", "iframe", "video", "audio",
        "dl", "dt", "dd", "details", "summary", "caption",
    )

    /** 行内收集时直接跳过的标签：图片走块级、列表走 [NodeList]、脚本样式不可能出现。 */
    private val SKIP_INLINE = setOf("ul", "ol", "table", "dl", "img", "script", "style")

    private val WS = Regex("\\s+")

    fun parse(html: String): List<ReadingNode> {
        if (html.isBlank()) return emptyList()
        return runCatching {
            val body = Jsoup.parseBodyFragment(html).body()
            parseBlocks(body, depth = 0, budget = Budget(), link = null)
        }.getOrDefault(emptyList())
    }

    /**
     * 双语对照用：从原文侧剥掉"译文里一模一样"的块——图片（含公式图）、代码块、分隔线。
     * 双语模式原文列与译文列并排，这类块翻不翻都一样，重复渲染两份纯属噪音
     * （用户反馈：同一张图出现两次）。剥空了的容器（只剩图的 group/引用）一并丢掉，
     * 调用方据此把该块退化为"只显示一份"。纯函数，JVM 可测。
     */
    fun stripVisualDuplicates(nodes: List<ReadingNode>): List<ReadingNode> {
        val out = ArrayList<ReadingNode>(nodes.size)
        for (node in nodes) {
            when (node) {
                is NodeImage, is NodeCode, is NodeRule -> Unit // 译文侧照原样出现，不再重复
                is NodeGroup -> stripVisualDuplicates(node.nodes)
                    .takeIf { it.isNotEmpty() }
                    ?.let { out.add(NodeGroup(it)) }
                is NodeQuote -> stripVisualDuplicates(node.blocks)
                    .takeIf { it.isNotEmpty() }
                    ?.let { out.add(NodeQuote(it)) }
                else -> out.add(node)
            }
        }
        return out
    }

    // ———————————————————————————————————————————————
    // 块级
    // ———————————————————————————————————————————————

    private fun parseBlocks(parent: Node, depth: Int, budget: Budget, link: String?): List<ReadingNode> {
        if (depth > MAX_DEPTH) return emptyList()
        val out = ArrayList<ReadingNode>()
        for (child in parent.childNodes()) {
            when (child) {
                is TextNode -> {
                    val text = collapse(child.getWholeText()).trim()
                    if (text.isBlank() || !budget.take()) continue
                    out += NodeParagraph(
                        listOf(InlineText(text)),
                        (child.parent() as? Element)?.let(::paragraphAlign),
                    )
                }
                is Element -> {
                    val node = parseElement(child, depth, budget, link) ?: continue
                    if (!budget.take()) break
                    out += node
                }
            }
        }
        return out
    }

    private fun parseElement(el: Element, depth: Int, budget: Budget, link: String?): ReadingNode? {
        val tag = el.tagName().lowercase()
        return when {
            tag.length == 2 && tag[0] == 'h' && tag[1] in '1'..'6' ->
                NodeHeading(tag[1] - '0', inlineRuns(el, depth)).takeIf { it.runs.hasText() }

            tag == "ul" || tag == "ol" ->
                parseList(el, ordered = tag == "ol", depth, budget)

            tag == "blockquote" ->
                parseBlocks(el, depth + 1, budget, link).let { NodeQuote(it) }.takeIf { it.blocks.isNotEmpty() }

            // wholeText 而非 text：jsoup 的 text() 会归一化空白，代码块会塌成一行。
            tag == "pre" ->
                el.wholeText().trim('\n', '\r').takeIf { it.isNotBlank() }?.let { NodeCode(it) }

            tag == "hr" -> NodeRule

            tag == "table" -> parseTable(el, depth, budget)

            // dl/dt/dd 定义列表：术语 + 描述成对渲染
            tag == "dl" -> parseDefList(el, depth, budget)

            // figure：单 img + figcaption 组合成「带说明的图」；其余结构照常拆块
            tag == "figure" -> parseFigure(el, depth, budget, link)

            // 游离的 figcaption（不在 figure 里）：当说明文字
            tag == "figcaption" -> NodeCaption(inlineRuns(el, depth)).takeIf { it.runs.hasText() }

            // details 折叠卡：summary 做标题，内容默认收起
            tag == "details" -> parseDetails(el, depth, budget, link)

            tag == "img" -> imageNode(el, link)

            // <math> 作为块级容器的直接子元素：独立公式块
            tag == "math" -> NodeMath(MathMl.parse(el.outerHtml()))

            // 未净化的输入（如 readability 抓取的全文）可能还留着嵌入标签：
            // 不静默丢弃，降级成媒体占位卡，可点可外跳。
            tag in EMBEDDED_TAGS -> embeddedCard(el)

            tag == "a" -> when {
                el.hasClass(MEDIA_CARD_CLASS) -> mediaCard(el)
                else -> {
                    val href = absoluteUrl(el.attr("href")) ?: link
                    parseBlocks(el, depth + 1, budget, href).toSingleOrGroup()
                }
            }

            else -> if (hasBlockDescendant(el, 0)) {
                parseBlocks(el, depth + 1, budget, link).toSingleOrGroup()
            } else {
                val runs = inlineRuns(el, depth)
                val loneMath = runs.singleOrNull() as? InlineMath
                when {
                    // 整段只有一个公式（WordPress/KaTeX 的独立公式块都长这样）：升级成块级居中
                    loneMath != null -> NodeMath(loneMath.spans)
                    runs.hasText() -> NodeParagraph(runs, paragraphAlign(el))
                    else -> null
                }
            }
        }
    }

    /** 单块直接返回，多块包成 [NodeGroup]——避免给每个 div 都套一层无意义的 Column。 */
    private fun List<ReadingNode>.toSingleOrGroup(): ReadingNode? = when {
        isEmpty() -> null
        size == 1 -> single()
        else -> NodeGroup(this)
    }

    private fun parseList(el: Element, ordered: Boolean, depth: Int, budget: Budget): NodeList? {
        if (depth >= MAX_DEPTH) return null
        val items = ArrayList<ListItem>()
        for (li in el.children()) {
            if (!li.tagName().equals("li", ignoreCase = true)) continue
            // 嵌套列表不混进行内片段（行内收集会跳过 ul/ol），单独挂在 ListItem 上按层级缩进渲染。
            val nested = li.children().firstOrNull { it.isList }
                ?.let { parseList(it, ordered = it.tagName().equals("ol", ignoreCase = true), depth + 1, budget) }
            val runs = inlineRuns(li, depth)
            if (!runs.hasText() && nested == null) continue
            if (!budget.take()) break
            items += ListItem(runs, nested)
        }
        return items.takeIf { it.isNotEmpty() }?.let { NodeList(ordered, it) }
    }

    private fun parseTable(el: Element, depth: Int, budget: Budget): NodeTable? {
        if (depth >= MAX_DEPTH) return null
        val caption = el.children().firstOrNull { it.tagName().equals("caption", true) }
            ?.let { collapse(it.text()).trim().takeIf { t -> t.isNotEmpty() } }
        val rows = ArrayList<NodeRow>()
        // 只取本表格的行：thead/tbody/tfoot 的子 tr 或直接子 tr。
        // 用 el.select("tr") 会把嵌套表格的行一起捞出来（旧实现的 bug）。
        for (section in el.children()) {
            val sectionTag = section.tagName().lowercase()
            val trs = when (sectionTag) {
                "tr" -> listOf(section)
                "thead", "tbody", "tfoot" -> section.children().filter { it.tagName().equals("tr", ignoreCase = true) }
                else -> emptyList()
            }
            val inHead = sectionTag == "thead"
            for (tr in trs) {
                val cells = tr.children().filter { it.tagName().lowercase() in CELL_TAGS }
                if (cells.isEmpty()) continue
                if (!budget.take()) break
                rows += NodeRow(
                    isHeader = inHead || cells.any { it.tagName().equals("th", ignoreCase = true) },
                    cells = cells.map { inlineRuns(it, depth) },
                )
            }
        }
        return rows.takeIf { it.isNotEmpty() }?.let { NodeTable(caption, it) }
    }

    /** dl/dt/dd：dt 是术语（粗体），dd 是描述（缩进）。连续 dt 各自成行。 */
    private fun parseDefList(el: Element, depth: Int, budget: Budget): NodeDefList? {
        if (depth >= MAX_DEPTH) return null
        val items = ArrayList<DefItem>()
        var term: List<InlineRun>? = null
        fun flushTerm() {
            val t = term
            if (t != null && t.hasText()) {
                if (budget.take()) items += DefItem(t, emptyList())
            }
            term = null
        }
        for (child in el.children()) {
            when (child.tagName().lowercase()) {
                "dt" -> {
                    flushTerm()
                    term = inlineRuns(child, depth)
                }
                "dd" -> {
                    val desc = inlineRuns(child, depth)
                    if (term?.hasText() == true || desc.hasText()) {
                        if (!budget.take()) break
                        items += DefItem(term ?: emptyList(), desc)
                    }
                    term = null
                }
            }
        }
        flushTerm()
        return items.takeIf { it.isNotEmpty() }?.let { NodeDefList(it) }
    }

    /**
     * figure：有 img 就出「带说明的图」（figcaption 文本挂到 [NodeImage.caption]，
     * 不再重复输出说明行）；其余子块照常递归，figcaption 单独命中当说明文字。
     */
    private fun parseFigure(el: Element, depth: Int, budget: Budget, link: String?): ReadingNode? {
        if (depth >= MAX_DEPTH) return null
        val img = el.children().firstOrNull { it.tagName().equals("img", true) }
        val caption = el.children().firstOrNull { it.tagName().equals("figcaption", true) }
        val capText = caption?.let { collapse(it.text()).trim().takeIf { t -> t.isNotEmpty() } }
        val out = ArrayList<ReadingNode>()
        if (img != null) {
            imageNode(img, link)?.let { node ->
                out += if (capText != null) node.copy(caption = capText) else node
            }
        } else if (caption != null) {
            out += NodeCaption(inlineRuns(caption, depth))
        }
        for (child in el.children()) {
            val t = child.tagName().lowercase()
            if (t == "img" || t == "figcaption") continue
            val node = parseElement(child, depth + 1, budget, link) ?: continue
            if (!budget.take()) break
            out += node
        }
        return out.toSingleOrGroup()
    }

    /** details：summary 提标题（缺失时渲染端给默认文案），其余子块收进折叠体。 */
    private fun parseDetails(el: Element, depth: Int, budget: Budget, link: String?): NodeDetails? {
        if (depth >= MAX_DEPTH) return null
        var summary: List<InlineRun>? = null
        val blocks = ArrayList<ReadingNode>()
        for (child in el.children()) {
            if (child.tagName().equals("summary", true) && summary == null) {
                summary = inlineRuns(child, depth)
                continue
            }
            val node = parseElement(child, depth + 1, budget, link) ?: continue
            if (!budget.take()) break
            blocks += node
        }
        return if (summary != null || blocks.isNotEmpty()) NodeDetails(summary, blocks) else null
    }

    /** 从元素 style 读 text-align（sanitize 只白名单化过，这里再校验取值）。 */
    private fun paragraphAlign(el: Element): ParagraphAlign? = when (
        el.attr("style")
            .split(';')
            .firstOrNull { it.trim().startsWith("text-align", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.lowercase()
    ) {
        "left" -> ParagraphAlign.LEFT
        "center" -> ParagraphAlign.CENTER
        "right" -> ParagraphAlign.RIGHT
        "justify" -> ParagraphAlign.JUSTIFY
        else -> null
    }

    private fun imageNode(el: Element, link: String?): NodeImage? {
        val src = absoluteUrl(el.attr("src")) ?: return null // 空 src / 相对路径：不渲染占位图
        val alt = el.attr("alt").trim().takeIf { it.isNotEmpty() }
        return NodeImage(src, alt, link, isFormula = looksLikeFormula(src, alt))
    }

    /**
     * 公式图识别：codecogs / chart.apis / mathjax 这类 CDN 吐的都是**黑字透明底**的图，
     * 深色主题下等于隐形——命中后渲染端垫一层中性底色，公式才看得见。
     * class="latex" 帮不上忙（sanitize 会剥掉所有 class），只能看 src 与 alt 里的特征。
     */
    private fun looksLikeFormula(src: String, alt: String?): Boolean =
        FORMULA_URL.containsMatchIn(src) || (alt != null && FORMULA_TEXT.containsMatchIn(alt))

    private val FORMULA_URL = Regex(
        "(codecogs|chart\\.apis|mathjax|latex|/math|/tex|equation|svg\\.latex|\\.svg\\?)",
        RegexOption.IGNORE_CASE,
    )
    // alt 里的 LaTeX 源码特征：$、反斜杠命令、^
    private val FORMULA_TEXT = Regex("(\\$|\\\\|\\(|\\[|\\^)")

    /** sanitize 产出的占位卡：`<a class="media-card" href><span>▶</span>标签 · 域名</a>`。 */
    private fun mediaCard(el: Element): NodeMediaCard? {
        val url = absoluteUrl(el.attr("href")) ?: return null
        val label = el.text().trimStart('▶', ' ', '·').trim().ifEmpty { "嵌入内容" }
        return NodeMediaCard(url, label)
    }

    /** 未净化输入里残留的 iframe/video/embed：拿得到 http 地址就补一张卡，否则丢掉。 */
    private fun embeddedCard(el: Element): NodeMediaCard? {
        val raw = el.attr("src").ifBlank { el.attr("data-src") }
            .ifBlank { el.selectFirst("source[src]")?.attr("src").orEmpty() }
        val url = absoluteUrl(raw) ?: return null
        val label = when (el.tagName().lowercase()) {
            "video" -> "视频"
            "audio" -> "音频"
            else -> "嵌入内容"
        }
        return NodeMediaCard(url, "$label · ${hostOf(url)}")
    }

    // ———————————————————————————————————————————————
    // 行内
    // ———————————————————————————————————————————————

    fun inlineRuns(el: Element, depth: Int = 0): List<InlineRun> {
        val out = ArrayList<InlineRun>()
        collectRuns(el, RunStyle(), depth, out)
        return trimRuns(out)
    }

    /** 行内收集时携带的样式状态：嵌套标签叠加（`<b><u>` → bold+underline）。 */
    private data class RunStyle(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val strike: Boolean = false,
        val underline: Boolean = false,
        val mark: Boolean = false,
        val color: Int? = null,
        val script: MathScript = MathScript.NORMAL,
        val small: Boolean = false,
    )

    /** 从元素的 style 属性读出增量样式（sanitize 已声明级白名单；未净化输入靠这里二次把关）。 */
    private fun styleOf(el: Element, base: RunStyle): RunStyle {
        val style = el.attr("style").trim()
        if (style.isEmpty()) return base
        var s = base
        var saw = false
        for (decl in style.split(';')) {
            val i = decl.indexOf(':')
            if (i <= 0) continue
            val prop = decl.substring(0, i).trim().lowercase()
            val value = decl.substring(i + 1).trim()
            when (prop) {
                "color" -> safeColor(value)?.let { s = s.copyColor(it); saw = true }
                "background-color" -> if (value.isNotBlank()) { s = s.copyMark(); saw = true }
                "font-weight" -> {
                    val n = value.toIntOrNull()
                    if (value.equals("bold", true) || (n != null && n >= 600)) { s = s.copyBold(); saw = true }
                }
                "font-style" -> if (value.equals("italic", true) || value.equals("oblique", true)) { s = s.copyItalic(); saw = true }
                "text-decoration", "text-decoration-line" -> when {
                    value.contains("underline") -> { s = s.copyUnderline(); saw = true }
                    value.contains("line-through") -> { s = s.copyStrike(); saw = true }
                }
            }
        }
        return if (saw) s else base
    }

    /** 颜色值安全解析：hex / rgb() / rgba() / 少量命名色，其余一律 null（不猜）。 */
    private fun safeColor(value: String): Int? {
        val v = value.trim().lowercase()
        return when {
            v.matches(Regex("#[0-9a-f]{3,8}")) -> hexToArgb(v.removePrefix("#"))
            v.matches(Regex("rgba?\\(\\s*[0-9.]+\\s*,\\s*[0-9.]+\\s*,\\s*[0-9.]+\\s*(,\\s*[0-9.]+\\s*)?\\)")) ->
                rgbToArgb(v)
            else -> NAMED_COLOR_ARGB[v]
        }
    }

    private fun hexToArgb(hex: String): Int = when (hex.length) {
        3, 4 -> {
            // #rgb/#rgba → 每位翻倍
            val expanded = hex.map { "$it$it" }.joinToString("")
            (expanded + "ff".take(8 - expanded.length)).toLong(16).toInt()
        }
        6 -> ((hex + "ff").toLong(16)).toInt()
        8 -> hex.toLong(16).toInt()
        else -> 0 // 正则已保证 3/4/6/8，防御分支
    }

    private fun rgbToArgb(v: String): Int {
        val parts = v.substringAfter('(').substringBefore(')').split(',').map { it.trim().toFloat() }
        val r = parts.getOrNull(0)?.coerceIn(0f, 255f)?.toInt() ?: return 0
        val g = parts.getOrNull(1)?.coerceIn(0f, 255f)?.toInt() ?: return 0
        val b = parts.getOrNull(2)?.coerceIn(0f, 255f)?.toInt() ?: return 0
        val a = (parts.getOrNull(3)?.coerceIn(0f, 1f) ?: 1f)
        return ((a * 255).toInt() shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** CSS 命名色子集：覆盖正文常见用色即可，不求全。 */
    private val NAMED_COLOR_ARGB: Map<String, Int> = mapOf(
        "black" to 0xFF000000L.toInt(), "white" to 0xFFFFFFFFL.toInt(), "red" to 0xFFFF0000L.toInt(),
        "green" to 0xFF008000L.toInt(), "blue" to 0xFF0000FFL.toInt(), "yellow" to 0xFFFFFF00L.toInt(),
        "orange" to 0xFFFFA500L.toInt(), "purple" to 0xFF800080L.toInt(), "gray" to 0xFF808080L.toInt(),
        "grey" to 0xFF808080L.toInt(), "silver" to 0xFFC0C0C0L.toInt(), "navy" to 0xFF000080L.toInt(),
        "teal" to 0xFF008080L.toInt(), "gold" to 0xFFFFD700L.toInt(), "pink" to 0xFFFFC0CBL.toInt(),
        "brown" to 0xFFA52A2AL.toInt(),
    )

    // RunStyle 的 copy 们（命名参数，防位置错排）：color 是 Int?，需显式覆盖
    private fun RunStyle.copyColor(c: Int) = copy(color = c)
    private fun RunStyle.copyMark() = copy(mark = true)
    private fun RunStyle.copyBold() = copy(bold = true)
    private fun RunStyle.copyItalic() = copy(italic = true)
    private fun RunStyle.copyUnderline() = copy(underline = true)
    private fun RunStyle.copyStrike() = copy(strike = true)
    private fun RunStyle.copyCode() = copy(code = true)
    private fun RunStyle.copySmall() = copy(small = true)
    private fun RunStyle.copyScript(s: MathScript) = copy(script = s)

    private fun collectRuns(
        node: Node,
        style: RunStyle,
        depth: Int,
        out: MutableList<InlineRun>,
    ) {
        if (depth > MAX_DEPTH) return
        for (child in node.childNodes()) {
            when (child) {
                is TextNode -> {
                    // 只压缩不裁剪：片段之间的空格是内容（"a <b>b</b>" 的拼接靠它），
                    // 边界空白留给 [trimRuns] 在整段层面收口。逐段 trim 会把词粘在一起。
                    val text = collapse(child.getWholeText())
                    if (text.isNotEmpty()) {
                        out += InlineText(
                            text = text,
                            bold = style.bold,
                            italic = style.italic,
                            code = style.code,
                            strike = style.strike,
                            underline = style.underline,
                            mark = style.mark,
                            color = style.color,
                            script = style.script,
                            small = style.small,
                        )
                    }
                }
                is Element -> {
                    val st = styleOf(child, style)
                    when (val tag = child.tagName().lowercase()) {
                        "br" -> out += InlineText(text = "\n", bold = st.bold, italic = st.italic, code = st.code, strike = st.strike, underline = st.underline, mark = st.mark, color = st.color, script = st.script, small = st.small)
                        "strong", "b" -> collectRuns(child, st.copyBold(), depth + 1, out)
                        "em", "i", "q", "cite", "dfn", "var" -> collectRuns(child, st.copyItalic(), depth + 1, out)
                        "code", "kbd", "samp", "tt" -> collectRuns(child, st.copyCode(), depth + 1, out)
                        "del", "s", "strike" -> collectRuns(child, st.copyStrike(), depth + 1, out)
                        // 下划线：u/ins + style text-decoration
                        "u", "ins" -> collectRuns(child, st.copyUnderline(), depth + 1, out)
                        // 高亮：<mark> 或 style background-color（渲染端垫高亮底色）
                        "mark" -> collectRuns(child, st.copyMark(), depth + 1, out)
                        // 缩小字号（脚注、备注）
                        "small" -> collectRuns(child, st.copySmall(), depth + 1, out)
                        // 上下标：脚注、化学式、指数都靠它（Compose 侧映射到 BaselineShift）
                        "sup" -> collectRuns(child, st.copyScript(MathScript.SUPER), depth + 1, out)
                        "sub" -> collectRuns(child, st.copyScript(MathScript.SUB), depth + 1, out)
                        "math" -> out += InlineMath(MathMl.parse(child.outerHtml()))
                        "a" -> {
                            val url = absoluteUrl(child.attr("href"))
                            if (url != null) {
                                out += InlineLink(collapse(child.text()).trim().ifEmpty { url }, url)
                            } else {
                                // 非 http 的 href（mailto:/#anchor/javascript:）不当链接，退化成纯文本
                                collectRuns(child, st, depth + 1, out)
                            }
                        }
                        in SKIP_INLINE -> Unit
                        else -> collectRuns(child, st, depth + 1, out)
                    }
                }
            }
        }
    }

    /** 裁掉整段首尾的空白片段与首尾片段的多余空白（保留片段之间的分隔空格）。 */
    private fun trimRuns(runs: List<InlineRun>): List<InlineRun> {
        val out = runs.toMutableList()
        while (out.isNotEmpty() && out.first().text.isBlank()) out.removeAt(0)
        while (out.isNotEmpty() && out.last().text.isBlank()) out.removeAt(out.lastIndex)
        if (out.isEmpty()) return emptyList()
        out[0] = out[0].withText(out[0].text.trimStart())
        val last = out.lastIndex
        out[last] = out[last].withText(out[last].text.trimEnd())
        return out.filter { it.text.isNotEmpty() }
    }

    // ———————————————————————————————————————————————
    // 工具
    // ———————————————————————————————————————————————

    /** 只放行 http(s)；协议相对补 https；其余（空串/相对/mailto/javascript:）一律 null。 */
    internal fun absoluteUrl(raw: String?): String? {
        val v = raw?.trim().orEmpty()
        return when {
            v.startsWith("http://", ignoreCase = true) || v.startsWith("https://", ignoreCase = true) -> v
            v.startsWith("//") -> "https:$v"
            else -> null
        }
    }

    private fun collapse(text: String): String = if (text.isEmpty()) text else WS.replace(text, " ")

    /**
     * 该元素是否含块级后代（决定「拆块」还是「当一段」）。
     * 只下探 3 层：够覆盖 `<div><a><img></a></div>` 这类间接包裹，又不至于和
     * `el.select(...)` 一样在每个元素上做全子树扫描（深文档会退化成 O(n²)）。
     */
    private fun hasBlockDescendant(el: Element, depth: Int): Boolean {
        if (depth > 3) return false
        for (child in el.children()) {
            if (child.tagName().lowercase() in BLOCK_TAGS) return true
            if (hasBlockDescendant(child, depth + 1)) return true
        }
        return false
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull().orEmpty().ifEmpty { "外部内容" }

    private fun List<InlineRun>.hasText(): Boolean = any { it.text.isNotBlank() }

    private val Element.isList: Boolean
        get() = tagName().equals("ul", ignoreCase = true) || tagName().equals("ol", ignoreCase = true)

    private val CELL_TAGS = setOf("td", "th")
    private val EMBEDDED_TAGS = setOf("iframe", "video", "audio", "embed", "object")

    /** 节点预算：单个块计数，超 [MAX_BLOCKS] 后 take() 恒 false，解析自然收口。 */
    private class Budget(var blocks: Int = 0) {
        fun take(): Boolean = if (blocks >= MAX_BLOCKS) false else { blocks++ ; true }
    }
}

// ———————————————————————————————————————————————
// 中间模型：纯数据树，渲染层不碰 jsoup 类型。
// ———————————————————————————————————————————————

internal sealed interface ReadingNode

/** 段落对齐（style text-align，白名单声明）。 */
internal enum class ParagraphAlign { LEFT, CENTER, RIGHT, JUSTIFY }

internal data class NodeParagraph(
    val runs: List<InlineRun>,
    val align: ParagraphAlign? = null,
) : ReadingNode
internal data class NodeHeading(val level: Int, val runs: List<InlineRun>) : ReadingNode
internal data class NodeList(val ordered: Boolean, val items: List<ListItem>) : ReadingNode
internal data class ListItem(val runs: List<InlineRun>, val nested: NodeList?)
/** 引用块：内部可以是多个块（多段引用不再被压成一段）。 */
internal data class NodeQuote(val blocks: List<ReadingNode>) : ReadingNode
internal data class NodeCode(val code: String) : ReadingNode
internal data class NodeImage(
    val src: String,
    val alt: String?,
    val href: String?,
    /** 公式图（LaTeX CDN 渲染出来的那种），渲染端要垫底色否则深色主题下看不见
     * 。 */
    val isFormula: Boolean = false,
    /** figure/figcaption 的说明文字（纯文本），渲染在图片下方。 */
    val caption: String? = null,
) : ReadingNode
/** 块级公式：整段只有一个 <math> 时升级成居中的独立块。 */
internal data class NodeMath(val spans: List<MathSpan>) : ReadingNode
internal data class NodeMediaCard(val url: String, val label: String) : ReadingNode
internal data class NodeTable(
    /** caption 文本，渲染在表格上方。 */
    val caption: String? = null,
    val rows: List<NodeRow>,
) : ReadingNode
internal data class NodeRow(val isHeader: Boolean, val cells: List<List<InlineRun>>)

/** 定义列表（dl/dt/dd）：术语行 + 描述行成对。 */
internal data class NodeDefList(val items: List<DefItem>) : ReadingNode
internal data class DefItem(val termRuns: List<InlineRun>, val descRuns: List<InlineRun>)

/** 说明文字（figcaption）：弱色小字。 */
internal data class NodeCaption(val runs: List<InlineRun>) : ReadingNode

/** details 折叠卡：summary 缺省时渲染端给默认文案。 */
internal data class NodeDetails(val summaryRuns: List<InlineRun>?, val blocks: List<ReadingNode>) : ReadingNode

internal data object NodeRule : ReadingNode
internal data class NodeGroup(val nodes: List<ReadingNode>) : ReadingNode

/** 行内片段。text 是显示文本，渲染层据此裁剪与判空。 */
internal sealed interface InlineRun {
    val text: String
}

internal data class InlineText(
    override val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    /** 下划线（u/ins + style text-decoration:underline）。 */
    val underline: Boolean = false,
    /** 高亮底色（mark + style background-color），渲染端垫主题高亮色。 */
    val mark: Boolean = false,
    /** style color 解析出的 ARGB；null = 跟随主题正文色。 */
    val color: Int? = null,
    /** 上标/下标（<sup>/<sub>），渲染时映射成 BaselineShift。 */
    val script: MathScript = MathScript.NORMAL,
    /** 缩小字号（<small>，脚注/备注）。 */
    val small: Boolean = false,
) : InlineRun

/** 行内公式：MathML 解析出来的片段（见 [MathMl]）。 */
internal data class InlineMath(val spans: List<MathSpan>) : InlineRun {
    override val text: String get() = spans.joinToString("") { it.text }
}

internal data class InlineLink(override val text: String, val url: String) : InlineRun

internal fun InlineRun.withText(text: String): InlineRun = when (this) {
    is InlineText -> copy(text = text)
    is InlineLink -> copy(text = text)
    is InlineMath -> this // 公式片段由 [MathMl] 生成，边界裁剪没有意义
}
