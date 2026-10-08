package com.cycling.rssradar.core.data.parser

import com.cycling.rssradar.core.model.ExtractionIssue
import net.dankito.readability4j.extended.Readability4JExtended
import net.dankito.readability4j.extended.processor.ArticleGrabberExtended
import net.dankito.readability4j.extended.processor.PostprocessorExtended
import net.dankito.readability4j.extended.util.RegExUtilExtended
import net.dankito.readability4j.model.ReadabilityOptions
import net.dankito.readability4j.processor.MetadataParser
import net.dankito.readability4j.processor.Preprocessor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.Instant
import java.time.format.DateTimeFormatter

/** 提取器来源（可观测：哪条路径救回了这篇）。 */
enum class Extractor { READABILITY, BODY_FALLBACK }

data class ExtractionQuality(
    val chars: Int,
    val paragraphs: Int,
    val images: Int,
    val extractor: Extractor,
    val issue: ExtractionIssue,
) {
    /** 是否可作为完整正文写入。只有 NONE（正常正文）算完整。 */
    val isComplete: Boolean
        get() = issue == ExtractionIssue.NONE
}

data class ExtractedArticle(
    /** 已过 [RssParser.sanitizeHtml] 的正文 HTML。 */
    val contentHtml: String,
    val contentText: String,
    val title: String?,
    val author: String?,
    val publishedAt: Long?,
    /** og:image / twitter:image，取不到则正文首图（由调用方兜底）。 */
    val coverUrl: String?,
    val quality: ExtractionQuality,
)

data class ExtractConfig(
    /** 正文纯文本低于该字数即判「不完整」。中文 200 字约等于一段话，再短基本是噪声或截断。 */
    val minContentChars: Int = 200,
    /** 最终正文的链接文本占比上限：超过说明抓到的是导航/索引页而不是正文。 */
    val maxLinkDensity: Float = 0.5f,
)

/**
 * 网页正文提取的**唯一入口**（纯 JVM：jsoup + readability4j，不碰 Android）。
 *
 * 算法侧走 readability4j extended 处理器——保留含图兄弟节点、把懒加载 `data-*` 提到 src、
 * 处理 `<base href>` 与 `<amp-img>`。抓取节点用库自带的 [ArticleGrabberExtended]。
 * `ponytail:` 不抄 ReadYou 的 `RYArticleGrabberExtended.prepareNodes`：与上游逐行比对过，只差
 * “不写 `display:inline`”与“不打 `readability-styled`”两行；而已核实（jar 常量池）
 * `ArticleGrabber` 打的正是这两样，[RssParser.sanitizeHtml] 又会剥掉全部 class（只留媒体占位卡）
 * 且 `display` 不在 `STYLE_PROPERTIES` 白名单里——净化后的产物一模一样，
 * 拿 60 行 GPL-3.0 代码换这个零差异不划算（MIT 仓库不该沾 copyleft）。
 * 天花板：从此跟库走，库改了 `prepareNodes` 就会跟着变；升级路径：真有差异时再抄，但得先换协议。
 *
 * 两条路径，逐级兜底，**两份候选都过同一套去噪**——readability 拿的是原始 html，
 * 不补这一步它就会靠「正文块 + 广告块」的总字数反杀干净候选（实测：推广块进正文且被判为完整）：
 * 1. **readability 抽取**：extended 处理器 + RY grabber。
 * 2. **去噪后的 body**：readability 空手时，把整个 body 当正文（比什么都不显示强，但多半会标不完整）。
 *
 * 两份都非空时取纯文本更长的一条。不引入「候选容器打分」：body 的纯文本恒 ≥ 其子容器，
 * 打分结果永远赢不了 body，只见成本不见收益（旧实现里它是死路径）。
 *
 * 去噪针对的是用户最常抱怨的四类噪声：导航/侧栏、广告、推荐位、评论区。
 * 元数据（标题/作者/发布时间）从**未去噪的原始 DOM** 上取，避免被误删。
 * 图片统一转成绝对 URL（readability 与 jsoup 都可能留下相对路径），并剔除 1×1 占位图。
 */
object Readability {

    fun extract(url: String, html: String, config: ExtractConfig = ExtractConfig()): ExtractedArticle? {
        if (html.isBlank()) return null
        val doc = runCatching { Jsoup.parse(html, url) }.getOrNull() ?: return null

        val title = extractTitle(doc)
        val author = extractAuthor(doc)
        val publishedAt = extractPublishedAt(doc)
        val coverUrl = extractOgImage(doc)

        // 输出仍要走去噪：readability 读的是原始 html，不过这一步就会带着广告/评论
        // 一起参与「取最长」，把去噪过的 body 顶掉。
        val readabilityHtml = parseToContent(html, url)
            ?.let { stripNoiseInFragment(it) }
            ?.takeIf { it.isNotBlank() }
        val bodyHtml = cleanedClone(doc).body().html().takeIf { it.isNotBlank() }

        val candidates = listOfNotNull(
            readabilityHtml?.let { Extractor.READABILITY to it },
            bodyHtml?.let { Extractor.BODY_FALLBACK to it },
        )

        // 取纯文本最长的一条；同长度时优先 readability（ordinal 更小）。
        // readability 在中文站点常常只捞到正文前半段，而 body 能拿到整块——
        // 比长度而不是「无条件优先 readability」，实测差异很大。
        val picked = candidates.maxWithOrNull(
            compareBy<Pair<Extractor, String>> { RssParser.textLength(it.second) }
                .thenBy { -it.first.ordinal },
        ) ?: return null
        val extractor = picked.first
        val contentHtml = RssParser.sanitizeHtml(prepareImages(picked.second, url))
        // 纯文本一律取自净化后的 HTML：readability 的 textContent 没走过噪声清洗，
        // 混用它会让 AI/搜索/过滤规则看到与阅读页不一样的正文（页面干净、喂给模型的带广告）。
        val contentText = RssParser.toPlainText(contentHtml) ?: return null
        if (contentHtml.isBlank()) return null

        val stats = measure(contentHtml)
        return ExtractedArticle(
            contentHtml = contentHtml,
            contentText = contentText,
            title = title,
            author = author,
            publishedAt = publishedAt,
            coverUrl = coverUrl,
            quality = ExtractionQuality(
                chars = stats.chars,
                paragraphs = stats.paragraphs,
                images = stats.images,
                extractor = extractor,
                issue = diagnose(stats, doc, config),
            ),
        )
    }

    /** 只取正文 HTML，不做元数据与完整性判定。 */
    fun parseToContent(html: String?, uri: String?): String? {
        html ?: return null
        if (html.isBlank()) return null
        return runCatching { create(uri, html).parse().content?.takeIf { it.isNotBlank() } }.getOrNull()
    }

    /**
     * 删掉正文里与文章标题完全相同的标题行（ReadYou 同款处理）。
     *
     * 站点常在正文开头再塞一个 h1/h2，阅读页于是把标题显示两遍——头部已经有标题了。
     * 只删文本**完全相同**的标题行：宁可漏删，也不误伤正文里恰好同名的小标题。
     */
    fun dropDuplicateTitle(html: String, title: String?): String {
        val target = title?.trim().orEmpty()
        if (target.isBlank() || html.isBlank()) return html
        val doc = runCatching { Jsoup.parseBodyFragment(html) }.getOrNull() ?: return html
        var removed = false
        doc.select("h1, h2").forEach { heading ->
            if (heading.text().trim() == target) {
                heading.remove()
                removed = true
            }
        }
        return if (removed) doc.body().html() else html
    }

    private fun create(uri: String?, html: String): Readability4JExtended {
        val options = ReadabilityOptions()
        val regExUtil = RegExUtilExtended()
        return Readability4JExtended(
            uri = uri ?: "",
            html = html,
            options = options,
            regExUtil = regExUtil,
            preprocessor = Preprocessor(regExUtil),
            metadataParser = MetadataParser(regExUtil),
            articleGrabber = ArticleGrabberExtended(options, regExUtil),
            postprocessor = PostprocessorExtended(),
        )
    }

    // ———————————————————————————————————————————————
    // 去噪与图片
    // ———————————————————————————————————————————————

    /** 在副本上去噪，原始 DOM 留给元数据提取。 */
    private fun cleanedClone(doc: Document): Document = doc.clone().also { stripNoise(it) }

    /** 对 HTML 片段去噪（readability 的输出走这条）。 */
    private fun stripNoiseInFragment(fragmentHtml: String): String {
        val doc = Jsoup.parseBodyFragment(fragmentHtml)
        stripNoise(doc)
        return doc.body().html()
    }

    private fun stripNoise(root: Element) {
        root.select(NOISE_SELECTOR).remove()
        // 隐藏节点（display:none 的广告/评论）：jsoup 不解析外部 CSS，只能看 inline style
        root.select("[style]").forEach { el ->
            val style = el.attr("style").replace(Regex("\\s+"), "")
            if (style.contains("display:none") || style.contains("visibility:hidden")) el.remove()
        }
        root.select("[hidden]").remove()
    }

    private fun prepareImages(fragmentHtml: String, url: String): String {
        val doc = Jsoup.parseBodyFragment(fragmentHtml, url)
        for (img in doc.select("img")) {
            val src = img.absUrl("src").ifBlank { img.absUrl("data-src") }.ifBlank { img.absUrl("data-original") }
            if (src.isBlank() || !src.startsWith("http")) {
                img.remove()
                continue
            }
            // 1×1 追踪像素 / 占位图：src 或尺寸特征命中就删，否则正文里会出现一排空白
            val w = img.attr("width").toIntOrNull() ?: Int.MAX_VALUE
            val h = img.attr("height").toIntOrNull() ?: Int.MAX_VALUE
            if (w <= 2 || h <= 2 || PLACEHOLDER_IMG.containsMatchIn(src)) {
                img.remove()
                continue
            }
            img.attr("src", src)
        }
        // <picture>/<source> 的懒加载兜底：把 srcset 首地址提上来
        for (source in doc.select("source[data-srcset], source[srcset]")) {
            val first = source.attr("abs:data-srcset").ifBlank { source.attr("abs:srcset") }
                .split(",").firstOrNull()?.trim()?.substringBefore(" ")
                .orEmpty()
            if (first.startsWith("http")) {
                source.parent()?.appendElement("img")?.attr("src", first)
                source.remove()
            }
        }
        return doc.body().html()
    }

    private data class Stats(val chars: Int, val paragraphs: Int, val images: Int, val linkChars: Int)

    private fun measure(sanitizedHtml: String): Stats {
        val doc = Jsoup.parseBodyFragment(sanitizedHtml)
        return Stats(
            chars = doc.text().length,
            // 段落块：p 之外的列表项/代码块/引用/单元格也算正文块，
            // 否则纯 `<pre>` 或纯表格的文章会被误判成「一个段落都没有」
            paragraphs = doc.select("p, li, pre, blockquote, td").size,
            images = doc.select("img").size,
            // 链接文本是「列表页伪装成正文」唯一的量化特征（导航页的 li 也算段落块）
            linkChars = doc.select("a").sumOf { it.text().length },
        )
    }

    // ———————————————————————————————————————————————
    // 完整性判定
    // ———————————————————————————————————————————————

    private fun diagnose(stats: Stats, doc: Document, config: ExtractConfig): ExtractionIssue {
        // 先判「短」：短 + JS 空壳 = 动态渲染，短 + 付费墙特征 = 付费墙，短但没特征 = 过短。
        // 顺序很重要——一个段落都没有的 JS 空壳应该归到 DYNAMIC_RENDER 而不是 NO_PARAGRAPH。
        if (stats.chars < config.minContentChars) {
            if (looksLikeJsShell(doc)) return ExtractionIssue.DYNAMIC_RENDER
            if (hitsPaywall(stats, doc)) return ExtractionIssue.PAYWALL
            return ExtractionIssue.TOO_SHORT
        }
        // 够长却一个正文块都没有 = 容器误判（纯导航/纯表格骨架）
        if (stats.paragraphs == 0) return ExtractionIssue.NO_PARAGRAPH
        // 够长、有段落块，但一半以上是链接文字 = 抓到的是索引/推荐页，不是正文
        if (stats.linkChars.toFloat() / stats.chars > config.maxLinkDensity) {
            return ExtractionIssue.LINK_LIST
        }
        return ExtractionIssue.NONE
    }

    /** JS 渲染页特征：内容容器是空的 + 页面里塞了大堆脚本（或明确要求开启 JS）。 */
    private fun looksLikeJsShell(doc: Document): Boolean {
        val shell = doc.select("#app, #root, [data-reactroot], #__next, [id*=app], [id*=root]")
            .any { it.text().isBlank() && it.select("script").isNotEmpty() }
        val scriptHeavy = doc.select("script").size >= 10
        val asksJs = doc.select("noscript").text().contains("JavaScript", ignoreCase = true)
        return shell || scriptHeavy || asksJs
    }

    private fun hitsPaywall(stats: Stats, doc: Document): Boolean {
        if (stats.chars >= PAYWALL_TEXT_LIMIT) return false
        val text = doc.text()
        return PAYWALL_WORDS.any { text.contains(it, ignoreCase = true) } ||
            doc.select(".paywall, [class*=paywall], [id*=paywall], [class*=subscribe-wall]").isNotEmpty()
    }

    // ———————————————————————————————————————————————
    // 元数据
    // ———————————————————————————————————————————————

    private fun extractTitle(doc: Document): String? =
        doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[name=twitter:title]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.title()?.trim()?.takeIf { it.isNotEmpty() }

    private fun extractAuthor(doc: Document): String? =
        doc.selectFirst("meta[name=author]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[property=article:author]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("[rel=author]")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst(".byline, .author, .author-name, [class*=author]")?.text()
                ?.trim()?.takeIf { it.length in 1..40 }

    private fun extractPublishedAt(doc: Document): Long? {
        val raw = doc.selectFirst("meta[property=article:published_time]")?.attr("content")
            ?: doc.selectFirst("meta[name=pubdate]")?.attr("content")
            ?: doc.selectFirst("meta[itemprop=datePublished]")?.attr("content")
            ?: doc.selectFirst("time[datetime]")?.attr("datetime")
            ?: return null
        return parseDateTime(raw)
    }

    internal fun parseDateTime(raw: String): Long? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        runCatching { return Instant.parse(v).toEpochMilli() }
        runCatching { return Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(v)).toEpochMilli() }
        runCatching {
            return Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(v)).toEpochMilli()
        }
        runCatching {
            return java.time.LocalDate.parse(v).atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        }
        return null
    }

    private fun extractOgImage(doc: Document): String? {
        val raw = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst("meta[name=twitter:image]")?.attr("content")
            ?: return null
        val v = raw.trim()
        return when {
            v.startsWith("http") -> v
            v.startsWith("//") -> "https:$v"
            v.startsWith("/") -> runCatching { java.net.URI(doc.baseUri()).resolve(v).toString() }.getOrNull()
            else -> null
        }
    }

    // ———————————————————————————————————————————————

    /**
     * 噪声选择器。不含裸 `header`——文章头部常带 h1 与作者；`nav/footer/aside` 才是稳定的噪声。
     * 中英文站点混用：类名规则兼顾 WordPress 系（.entry-content 旁的 .widget/.comment）与国内站（.related/.recommend）。
     */
    private val NOISE_SELECTOR = listOf(
        "nav", "footer", "aside", "form", "script", "style", "noscript", "svg", "iframe",
        "button", "input", "select", "textarea", "template",
        "[role=navigation]", "[role=banner]", "[role=complementary]", "[role=search]",
        "[aria-hidden=true]",
        ".ad", ".ads", ".adbox", ".ad-wrapper", ".advert", ".advertisement", ".google-ad", ".adsbygoogle",
        ".social", ".social-share", ".share", ".sharing", ".share-buttons", ".sharethis",
        ".related", ".related-posts", ".recommend", ".recommended", ".read-more", ".more-news", ".hot-news",
        ".comment", ".comments", ".comment-list", ".commentbox", "#comments", "#disqus_thread",
        ".breadcrumb", ".breadcrumbs", ".pagination", ".pager", ".page-nav",
        ".tags", ".tagcloud", ".tag-list",
        ".newsletter", ".subscribe", ".subscription", ".paywall", ".subscribe-wall",
        ".popup", ".modal", ".overlay", ".cookie", ".cookie-banner", ".gdpr",
        ".sidebar", ".widget", ".promo", ".sponsor", ".sponsored", ".author-bio", ".copyright",
        ".footer", ".header-ad", ".topbar", ".toolbar",
        "[class*=advert]", "[class*=sponsor]", "[class*=promo]", "[id*=advert]",
    ).joinToString(", ")

    private val PLACEHOLDER_IMG =
        Regex("(spacer|placeholder|blank\\.gif|1x1|pixel|transparent\\.png|/icon|loading\\.gif)", RegexOption.IGNORE_CASE)

    /** 付费墙/登录墙特征词；仅在正文短于 [PAYWALL_TEXT_LIMIT] 时才判定，避免长文误伤。 */
    private val PAYWALL_WORDS = listOf(
        "订阅后查看", "订阅以继续", "开通会员", "会员专享", "付费内容", "付费文章",
        "登录后查看", "登录后阅读", "登录后可查看", "成为会员", "立即订阅",
        "Subscribe to continue", "Subscription required", "Subscribers only", "Members only",
        "Premium content", "To continue reading", "Sign in to read", "Log in to read",
        "Create an account to continue", "This article is for subscribers", "Register to continue",
    )

    private const val PAYWALL_TEXT_LIMIT = 1200
}
