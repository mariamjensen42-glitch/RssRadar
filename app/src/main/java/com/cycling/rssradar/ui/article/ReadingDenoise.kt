package com.cycling.rssradar.ui.article

/**
 * 显示层正文降噪（沉浸阅读，issue #93）：在 [ReadingNodes] 解析出的中间树上
 * 剥掉网页杂乱元素——分享按钮、推荐位、导航列表、评论区链接、"阅读原文"之类
 * 的站内跳转行——只留正文与图片。
 *
 * 放在**显示层**而非抓取层（ArticleExtractor 已做一次去噪）的理由：
 * feed 自带正文不过提取器，杂乱内容直接进库；显示层清洗对存量文章立即生效，
 * 且不碰数据库，误伤可随算法升级自动恢复。
 *
 * 三类规则（宁可漏杀，不可错杀，正文安全网兜底）：
 * 1. **链接密集段**：链接文本占比 ≥ [MIN_LINK_RATIO] 且全文不超过 [MAX_JUNK_CHARS] 的段——
 *    "阅读原文"、"查看更多"、分享按钮行都是这个形状。
 * 2. **噪声关键词段**：短文本命中站点噪声词（扫码/关注公众号/责任编辑…）。
 *    独立标签行（"广告"、"推广"）单独一套更严的判定：几乎只在这些词本身出现时才删。
 * 3. **链接列表**：整条列表的文本几乎全是链接（导航/相关阅读列表的形状）。
 *    命中"相关阅读/推荐阅读"标题的只删标题本身，后面的列表交给规则 3。
 *
 * **安全网**：清洗后正文总量不足清洗前的 [SAFETY_KEEP_RATIO]（或清空）时，
 * 原样返回——降噪误把正文当噪声时，宁可保留噪声也不丢内容。纯 JVM，可单测。
 */
internal object ReadingDenoise {

    /** 判定为"短段"的字符上限：噪声行都很短，正文段落很少低于这个长度。 */
    const val MAX_JUNK_CHARS = 60

    /** 链接文本占整段文本的比例上限：达到即判"整段都是链接"。 */
    const val MIN_LINK_RATIO = 0.6f

    /** 整条列表判为"链接列表"所需的链接占比。 */
    const val LIST_LINK_RATIO = 0.8f

    /** 安全网阈值：清洗后文本量低于清洗前的该比例，视为误伤，放弃清洗。 */
    const val SAFETY_KEEP_RATIO = 0.5

    /** 长尾噪声关键词：短段（≤[MAX_JUNK_CHARS]）命中即删。 */
    private val NOISE_KEYWORDS = listOf(
        "分享到", "分享至", "扫码", "扫描二维码", "长按识别",
        "关注公众号", "微信公众号", "微信搜一搜", "扫码关注",
        "阅读原文", "原文地址", "戳这里", "点此查看", "点此进入", "点击查看更多",
        "责任编辑", "责任小编", "转载请注明", "版权所有", "本文来自", "本文转载",
        "更多精彩内容", "下载客户端", "打开App", "打开APP", "点击进入专题",
        "相关推荐", "点击排行", "返回搜狐", "点击上方", "点击关注",
        "Subscribe to", "Follow us", "Share this", "Read more at",
    )

    /** 独立标签行：只在整段几乎就是这个词时才删（避免误伤含"广告"的正经句子）。 */
    private val NOISE_LABELS = setOf(
        "广告", "推广", "赞助", "赞助商", "advertisement", "ads", "sponsored", "promo",
    )

    /** 噪声标题：命中只删标题，不连带删正文。 */
    private val NOISE_HEADINGS =
        Regex("^(相关阅读|相关文章|推荐阅读|延伸阅读|热门推荐|热门文章|猜你喜欢|大家都在看|编辑推荐|更多阅读|今日热点)[:：]?$")

    /** 沉浸模式主入口：剥掉噪声块；安全网兜底，永不返回空列表（输入非空时）。 */
    fun clean(nodes: List<ReadingNode>): List<ReadingNode> {
        if (nodes.isEmpty()) return nodes
        val before = totalText(nodes)
        val cleaned = nodes.mapNotNull(::cleanNode)
        val after = totalText(cleaned)
        if (cleaned.isEmpty()) return nodes
        if (before >= 200 && after < before * SAFETY_KEEP_RATIO) return nodes
        return cleaned
    }

    private fun cleanNode(node: ReadingNode): ReadingNode? = when (node) {
        is NodeParagraph -> node.takeIf { !isJunkParagraph(node.runs, node.runs.textLength()) }
        is NodeHeading -> node.takeIf { !NOISE_HEADINGS.matches(node.runs.plainText().trim()) }
        is NodeList -> node.takeIf { !isLinkList(node) }
        is NodeQuote -> clean(node.blocks).takeIf { it.isNotEmpty() }?.let { NodeQuote(it) }
        is NodeGroup -> clean(node.nodes).takeIf { it.isNotEmpty() }?.let { NodeGroup(it) }
        is NodeDetails -> {
            val summary = node.summaryRuns.orEmpty()
            val blocks = clean(node.blocks)
            if (blocks.isEmpty() && summary.textLength() <= MAX_JUNK_CHARS &&
                isJunkParagraph(summary, summary.textLength())
            ) {
                null
            } else {
                node.copy(blocks = blocks)
            }
        }
        else -> node
    }

    /** 链接密集段 / 噪声关键词段 / 独立标签行的统一判定。 */
    private fun isJunkParagraph(runs: List<InlineRun>, plainLength: Int): Boolean {
        if (runs.isEmpty() || plainLength == 0 || plainLength > MAX_JUNK_CHARS) return false
        val linkLength = runs.filterIsInstance<InlineLink>().sumOf { it.text.length }
        if (linkLength >= plainLength * MIN_LINK_RATIO) return true
        val text = runs.plainText().trim()
        if (NOISE_KEYWORDS.any { text.contains(it, ignoreCase = true) }) return true
        // 独立标签行：去标点后的短文本与标签词几乎一致
        val normalized = text.replace(Regex("[:：!！.。\\s]+"), "").lowercase()
        return normalized.length <= 6 && NOISE_LABELS.any { normalized.contains(it.trim()) }
    }

    /** 整条列表几乎全是链接文本 → 导航/推荐列表。单条链接列表不删（可能是脚注）。 */
    private fun isLinkList(node: NodeList): Boolean {
        if (node.items.size < 2) return false
        var total = 0
        var link = 0
        for (item in node.items) {
            val len = item.runs.textLength()
            total += len
            link += item.runs.filterIsInstance<InlineLink>().sumOf { it.text.length }
        }
        if (total == 0) return true
        return link >= total * LIST_LINK_RATIO
    }

    private fun totalText(nodes: List<ReadingNode>): Int = nodes.sumOf { nodeText(it) }

    private fun nodeText(node: ReadingNode): Int = when (node) {
        is NodeParagraph -> node.runs.textLength()
        is NodeHeading -> node.runs.textLength()
        is NodeList -> node.items.sumOf { it.runs.textLength() }
        is NodeQuote -> totalText(node.blocks)
        is NodeGroup -> totalText(node.nodes)
        is NodeDetails -> node.summaryRuns.orEmpty().textLength() + totalText(node.blocks)
        is NodeCaption -> node.runs.textLength()
        is NodeDefList -> node.items.sumOf { it.termRuns.textLength() + it.descRuns.textLength() }
        is NodeTable -> node.rows.sumOf { row -> row.cells.sumOf { cells -> cells.textLength() } }
        is NodeImage -> node.alt?.length ?: 0
        is NodeMediaCard -> node.label.length
        else -> 0
    }

    private fun List<InlineRun>.textLength(): Int = sumOf { it.text.length }

    private fun List<InlineRun>.plainText(): String = joinToString("") { it.text }
}
