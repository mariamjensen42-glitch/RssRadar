package com.cycling.rssradar.data.parser

import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.max

/** 提取器来源（可观测：哪条路径救回了这篇）。 */
enum class Extractor { READABILITY, JSOUP_FALLBACK, BODY_FALLBACK }

/** 质量问题分类（对应 [FetchFailure] 里 EXTRACT_* 的细分，用于日志与诊断页归因）。 */
enum class ExtractionIssue {
    NONE,
    /** 正文过短（低于 [ExtractConfig.minContentChars]）。 */
    TOO_SHORT,
    /** 一个段落都没有：基本可以断定容器误判。 */
    NO_PARAGRAPH,
    /** 正文空/极短且页面是 JS 空壳（#app/#root/React 容器 + 大量脚本）。 */
    DYNAMIC_RENDER,
    /** 正文短且命中付费墙/登录墙特征。 */
    PAYWALL,
    /** 正文够长但没有标题或时间（只告警，不算不完整）。 */
    METADATA_MISSING,
}

data class ExtractionQuality(
    val chars: Int,
    val paragraphs: Int,
    val images: Int,
    val extractor: Extractor,
    val issue: ExtractionIssue,
) {
    /** 是否可作为完整正文写入。TOO_SHORT/NO_PARAGRAPH/DYNAMIC_RENDER/PAYWALL 都算不完整。 */
    val isComplete: Boolean
        get() = issue == ExtractionIssue.NONE || issue == ExtractionIssue.METADATA_MISSING
}

data class ExtractedArticle(
    /** 已过 [RssParser.sanitizeHtml] 的正文 HTML。 */
    val contentHtml: String,
    val contentText: String,
    val title: String?,
    val author: String?,
    val publishedAt: Long?,
    /** og:image / JSON-LD image，取不到则正文首图（由调用方兜底）。 */
    val coverUrl: String?,
    val quality: ExtractionQuality,
)

data class ExtractConfig(
    /** 正文纯文本低于该字数即判「不完整」。中文 200 字约等于一段话，再短基本是噪声或截断。 */
    val minContentChars: Int = 200,
    /** 候选容器打分时的链接密度上限：超过说明这是导航/推荐列表而不是正文。 */
    val maxLinkDensity: Float = 0.5f,
)

/**
 * 网页正文提取（纯 JVM：jsoup + readability4j，不碰 Android）。
 *
 * 三段式，逐级兜底——readability 对欧美站点准，对中文站点（div 套 div、正文被拆成多个块）
 * 命中率明显下降，所以不能只用一条路径：
 * 1. **readability4j**：Mozilla Readability 的 JVM 移植，ReadYou 生产验证。
 * 2. **jsoup 候选容器打分**：去噪后按「文本长度 + 段落数 + 图片数 − 链接密度惩罚」挑正文容器。
 * 3. **去噪后的 body**：前两路都不够时，把整个 body 当正文（比什么都不显示强，但会标不完整）。
 *
 * 去噪针对的是用户最常抱怨的四类噪声：导航/侧栏、广告、推荐位、评论区。
 * 元数据（标题/作者/发布时间）从**未去噪的原始 DOM** 上取，避免被误删。
 * 图片统一转成绝对 URL（readability 与 jsoup 都可能留下相对路径），并剔除 1×1 占位图。
 */
object ArticleExtractor {

    fun extract(url: String, html: String, config: ExtractConfig = ExtractConfig()): ExtractedArticle? {
        if (html.isBlank()) return null
        val doc = runCatching { Jsoup.parse(html, url) }.getOrNull() ?: return null

        val title = extractTitle(doc)
        val author = extractAuthor(doc)
        val publishedAt = extractPublishedAt(doc)
        val coverUrl = extractOgImage(doc)

        val readability = runCatching { Readability4J(url, html).parse() }.getOrNull()
        val readabilityHtml = readability?.content?.takeIf { it.isNotBlank() }
        val cleaned = cleanedClone(doc)
        val fallbackHtml = bestContainer(cleaned, config)
        val bodyHtml = cleaned.body().html().takeIf { it.isNotBlank() }

        val candidates = listOfNotNull(
            readabilityHtml?.let { Extractor.READABILITY to it },
            fallbackHtml?.let { Extractor.JSOUP_FALLBACK to it },
            bodyHtml?.let { Extractor.BODY_FALLBACK to it },
        )
        if (candidates.isEmpty()) return null

        // 取纯文本最长的一条；同长度时优先 readability（ordinal 更小）。
        // readability 在中文站点常常只捞到正文前半段，而容器打分能拿到整块——
        // 比长度而不是「无条件优先 readability」，实测差异很大。
        val picked = candidates.maxWith(
            compareBy<Pair<Extractor, String>> { RssParser.textLength(it.second) }
                .thenBy { -it.first.ordinal },
        )
        val extractor = picked.first
        val contentHtml = RssParser.sanitizeHtml(prepareImages(picked.second, url))
        val contentText = readability?.textContent
            ?.takeIf { extractor == Extractor.READABILITY && it.isNotBlank() }
            ?: RssParser.toPlainText(contentHtml)
            ?: return null
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

    // ———————————————————————————————————————————————
    // 去噪与候选容器
    // ———————————————————————————————————————————————

    /** 在副本上去噪，原始 DOM 留给元数据提取。 */
    private fun cleanedClone(doc: Document): Document {
        val clone = doc.clone()
        clone.select(NOISE_SELECTOR).remove()
        // 隐藏节点（display:none 的广告/评论）：jsoup 不解析外部 CSS，只能看 inline style
        clone.select("[style]").forEach { el ->
            val style = el.attr("style").replace(Regex("\\s+"), "")
            if (style.contains("display:none") || style.contains("visibility:hidden")) el.remove()
        }
        clone.select("[hidden]").remove()
        return clone
    }

    /**
     * 候选容器打分：文本长度为主，段落/图片加权，链接密度过高则判为导航或推荐列表。
     */
    private fun bestContainer(doc: Document, config: ExtractConfig): String? {
        val candidates = doc.select(CONTAINER_SELECTOR)
        val all = if (candidates.isEmpty()) {
            // 没有语义容器：退化到「div 里文本最长且段落最多的那个」
            doc.select("div")
        } else {
            candidates
        }
        var best: Pair<Double, Element>? = null
        for (el in all) {
            if (el.select("p").isEmpty() && el.ownText().length < config.minContentChars) continue
            val text = el.text()
            val chars = text.length
            if (chars < config.minContentChars) continue
            val links = el.select("a").sumOf { it.text().length }
            val density = if (chars == 0) 1f else links.toFloat() / chars
            if (density > config.maxLinkDensity) continue
            val score = chars +
                el.select("p").size * 80 +
                el.select("img").size * 40 -
                (density * 200).toInt()
            if (best == null || score > best!!.first) best = score.toDouble() to el
        }
        return best?.second?.html()?.takeIf { it.isNotBlank() }
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
            img.attr("alt", img.attr("alt"))
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

    private data class Stats(val chars: Int, val paragraphs: Int, val images: Int)

    private fun measure(sanitizedHtml: String): Stats {
        val doc = Jsoup.parseBodyFragment(sanitizedHtml)
        return Stats(
            chars = doc.text().length,
            // 段落块：p 之外的列表项/代码块/引用/单元格也算正文块，
            // 否则纯 `<pre>` 或纯表格的文章会被误判成「一个段落都没有」
            paragraphs = doc.select("p, li, pre, blockquote, td").size,
            images = doc.select("img").size,
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
            ?: jsonLdFirst(doc, "headline")
            ?: doc.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.title()?.trim()?.takeIf { it.isNotEmpty() }

    private fun extractAuthor(doc: Document): String? =
        doc.selectFirst("meta[name=author]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[property=article:author]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("[rel=author]")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: jsonLdAuthor(doc)
            ?: doc.selectFirst(".byline, .author, .author-name, [class*=author]")?.text()
                ?.trim()?.takeIf { it.length in 1..40 }

    private fun extractPublishedAt(doc: Document): Long? {
        val raw = doc.selectFirst("meta[property=article:published_time]")?.attr("content")
            ?: doc.selectFirst("meta[name=pubdate]")?.attr("content")
            ?: doc.selectFirst("meta[itemprop=datePublished]")?.attr("content")
            ?: doc.selectFirst("time[datetime]")?.attr("datetime")
            ?: jsonLdFirst(doc, "datePublished")
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

    /** 从 `<script type="application/ld+json">` 里抓字段（避免引 org.json，正则够用）。 */
    private fun jsonLdFirst(doc: Document, key: String): String? {
        for (script in doc.select("script[type=application/ld+json]")) {
            val json = script.data().ifBlank { script.html() }
            val m = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"").find(json)
            if (m != null) return m.groupValues[1].trim().takeIf { it.isNotEmpty() }
        }
        return null
    }

    private fun jsonLdAuthor(doc: Document): String? {
        for (script in doc.select("script[type=application/ld+json]")) {
            val json = script.data().ifBlank { script.html() }
            val block = Regex("\"author\"\\s*:\\s*\\{[^}]*\\}").find(json)?.value
                ?: Regex("\"author\"\\s*:\\s*\"([^\"]+)\"").find(json)?.let { return it.groupValues[1].trim() }
                ?: continue
            Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(block)?.let {
                return it.groupValues[1].trim().takeIf { v -> v.isNotEmpty() }
            }
        }
        return null
    }

    private fun extractOgImage(doc: Document): String? {
        val raw = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst("meta[name=twitter:image]")?.attr("content")
            ?: jsonLdFirst(doc, "image")
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

    /** 正文容器候选：语义标签优先，其次是各 CMS 的惯用类名。 */
    private val CONTAINER_SELECTOR = listOf(
        "article", "main", "[role=article]", "[role=main]",
        ".post-content", ".entry-content", ".article-content", ".article-body", ".articleBody",
        ".article", ".article-detail", ".post-body", ".post", ".story", ".story-body",
        "#content", "#article", "#article-content", "#main-content", "#js_content",
        ".content-article", ".news-content", ".detail-content", ".detail", ".text",
        ".rich-text", ".rich_media_content", ".markdown-body", ".blog-post", ".post-text",
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
