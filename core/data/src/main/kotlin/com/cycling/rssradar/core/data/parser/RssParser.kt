package com.cycling.rssradar.core.data.parser

import com.rometools.modules.mediarss.MediaEntryModule
import com.rometools.modules.mediarss.types.PlayerReference
import com.rometools.modules.mediarss.types.UrlReference
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.StringReader

/**
 * 单份 feed 超过 [RssParser.MAX_FEED_BYTES]。
 *
 * 必须继承 IOException 而不是 IllegalArgumentException：后者会被
 * [com.cycling.rssradar.core.domain.rss.FeedProbeResult.from] 归成 INVALID_FEED，
 * 进而触发失效源自愈——把只是「太大」的健康源当成坏源改地址。
 */
class FeedTooLargeException(val bytes: Int) : IOException("Feed too large: $bytes bytes")

/** 解析 RSS 2.0 / Atom 流的纯 JVM 组件。 */
class RssParser {

    data class ParsedArticle(
        val link: String,
        val title: String,
        /** 短摘要（纯文本，≤[SUMMARY_MAX_LENGTH] 字），列表与检索用。 */
        val summary: String?,
        /** 净化后的正文 HTML；null 表示 feed 没给全文。 */
        val contentHtml: String?,
        val contentText: String?,
        val author: String?,
        val publishedAt: Long?,
        /** 封面图：enclosure → media:thumbnail → 正文首个 img。 */
        val coverUrl: String?,
        /** 与 ArticleEntity.MEDIA_KIND_* 对齐。 */
        val mediaKind: Int = MEDIA_KIND_NONE,
        val mediaUrl: String? = null,
    )

    data class ParsedFeed(
        val title: String,
        val articles: List<ParsedArticle>,
        /** 站点首页（channel/根 link），与订阅源地址不同；容忍缺失（空串）。 */
        val siteUrl: String = "",
    )

    fun parse(input: InputStream): ParsedFeed {
        val raw = readCapped(input)
        val feed = try {
            SyndFeedInput().build(XmlReader(ByteArrayInputStream(raw)))
        } catch (first: Exception) {
            // 真实世界的 feed 大量是"稍微坏掉"的 XML（控制字符、HTML 实体、裸 &）。
            // 严格解析失败后清洗重试，还不行才算无效；第二次失败挂第一次的原因成 suppressed。
            try {
                val text = XmlReader(ByteArrayInputStream(raw)).use { it.readText() }
                SyndFeedInput().build(StringReader(sanitizeXml(text)))
            } catch (second: Exception) {
                second.addSuppressed(first)
                throw IllegalArgumentException("Not a valid RSS/Atom feed", second)
            }
        }
        val articles = feed.entries.mapNotNull { it.toArticle() }
        return ParsedFeed(
            title = feed.title?.trim().orEmpty().ifEmpty { "Untitled feed" },
            articles = articles,
            siteUrl = atomSiteUrl(feed),
        )
    }

    /** 带上限读完流：二次容错解析必须先看到全量，但刷新 32 路并发下无上限就是 OOM 入口。 */
    private fun readCapped(input: InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > MAX_FEED_BYTES) throw FeedTooLargeException(total)
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    /**
     * XML 清洗（容错二次解析用），**单趟**扫描：
     * 1. XML 1.0 非法控制字符（解析失败第一大来源）；
     * 2. 合法命名实体 → 规范形式（大小写不敏感）；
     * 3. 未知命名实体 → 退化成字面文本；
     * 4. 裸 & → `&amp;`。
     *
     * 单趟而不是「filter 一遍 + 每个实体 replace 一遍」：后者是 O(N×实体数) 的全串拷贝。
     */
    internal fun sanitizeXml(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (isIllegalXmlChar(c)) {
                i++
                continue
            }
            if (c != '&') {
                out.append(c)
                i++
                continue
            }
            // 找不到配对 `;` 或实体名超长，一律按裸 & 处理
            val end = text.indexOf(';', i + 1)
            val body = if (end > i + 1 && end <= i + MAX_ENTITY_LENGTH) text.substring(i + 1, end) else null
            if (body == null) {
                out.append("&amp;")
                i++
                continue
            }
            if (body[0] == '#') {
                out.append('&').append(body).append(';')
            } else {
                val lowered = body.lowercase()
                val codes = IntArray(2)
                val count = Entities.codepointsForName(lowered, codes)
                when {
                    // XML 预定义实体：保留命名形式（&AMP; 在 XML 里非法，统一成小写）
                    lowered in XML_PREDEFINED_ENTITIES -> out.append('&').append(lowered).append(';')
                    count > 0 -> for (k in 0 until count) out.append("&#").append(codes[k]).append(';')
                    else -> out.append("&amp;").append(body).append(';')
                }
            }
            i = end + 1
        }
        return out.toString()
    }

    /** XML 1.0 合法字符集之外（Tab/LF/CR 保留，DEL 与 0xFFFE/0xFFFF 一并清掉）。 */
    private fun isIllegalXmlChar(c: Char): Boolean =
        (c.code < 0x20 && c != '\t' && c != '\n' && c != '\r') ||
            c.code == 0x7F || c.code == 0xFFFE || c.code == 0xFFFF

    /** Atom 的 `getLink` 会取第一个 `<link>`（常是 rel=self），手动按 rel 选 alternate。 */
    private fun atomSiteUrl(feed: SyndFeed): String {
        val links = feed.links.orEmpty()
        return (links.firstOrNull { it.rel == "alternate" }
            ?: links.firstOrNull { it.rel.isNullOrBlank() })
            ?.href?.trim().orEmpty()
            .ifEmpty { feed.link?.trim().orEmpty() }
    }

    private fun SyndEntry.toArticle(): ParsedArticle? {
        val link = this.link?.trim().orEmpty().ifEmpty { uri?.trim().orEmpty() }
        val title = this.title?.trim().orEmpty()
        if (link.isEmpty() && title.isEmpty()) return null

        val rawDescription = description?.value
        val rawFull = rawFullText(this)
        val descLen = textLength(rawDescription)
        val fullLen = textLength(rawFull)
        val bodySource = when {
            fullLen == 0 && descLen == 0 -> null
            fullLen >= descLen -> rawFull
            else -> rawDescription
        }
        val contentHtml = bodySource?.let(::sanitizeHtml)?.takeIf { it.isNotBlank() }
        val contentText = contentHtml?.let(::toPlainText)
        val summarySource = when {
            descLen == 0 -> rawFull
            fullLen == 0 -> rawDescription
            descLen < fullLen -> rawDescription
            else -> rawFull
        }

        return ParsedArticle(
            link = link,
            title = title.ifEmpty { link },
            summary = summarySource?.let(::toPlainText)?.let(::stripMarkdown)?.take(SUMMARY_MAX_LENGTH)
                ?.takeIf { it.isNotBlank() },
            contentHtml = contentHtml,
            contentText = contentText,
            author = author?.trim()?.takeIf { it.isNotEmpty() },
            publishedAt = sanitizePublishedAt(publishedDate?.time ?: updatedDate?.time),
            coverUrl = extractCover(this, bodySource, link),
            mediaKind = extractMediaKind(this),
            mediaUrl = extractMediaUrl(this),
        )
    }

    /** 媒体直链：只在真有地址时返回——列表上摆一个点不开的播放按钮比不摆更糟。 */
    private fun extractMediaUrl(entry: SyndEntry): String? {
        entry.enclosures.orEmpty().forEach { enclosure ->
            val type = enclosure.type.orEmpty().lowercase()
            val url = enclosure.url
            if ((type.startsWith("audio") || type.startsWith("video")) && !url.isNullOrBlank()) {
                return url
            }
        }
        for (content in entry.mediaModule()?.mediaContents.orEmpty()) {
            val type = content.type.orEmpty().lowercase()
            if (!type.startsWith("audio") && !type.startsWith("video")) continue
            val url = when (val reference = content.reference) {
                is UrlReference -> reference.url?.toString()
                is PlayerReference -> reference.url?.toString()
                else -> null
            }
            if (!url.isNullOrBlank()) return url
        }
        return null
    }

    /** 媒体种类：enclosure 的 MIME 前缀，其次 media 模块 content 的 type。都不命中返回 NONE——不猜。 */
    private fun extractMediaKind(entry: SyndEntry): Int {
        entry.enclosures.orEmpty().forEach { enclosure ->
            val type = enclosure.type.orEmpty().lowercase()
            if (type.startsWith("video")) return MEDIA_KIND_VIDEO
            if (type.startsWith("audio")) return MEDIA_KIND_AUDIO
        }
        val mediaTypes = entry.mediaModule()?.mediaContents.orEmpty().mapNotNull { it.type }
        if (mediaTypes.any { it.lowercase().startsWith("video") }) return MEDIA_KIND_VIDEO
        if (mediaTypes.any { it.lowercase().startsWith("audio") }) return MEDIA_KIND_AUDIO
        return MEDIA_KIND_NONE
    }

    private fun SyndEntry.mediaModule(): MediaEntryModule? =
        getModule(MEDIA_MODULE_URI) as? MediaEntryModule

    /** 全文字段：Atom `<content>` 与 RSS `content:encoded`（rome 按 namespace URI 识别）取较长者。 */
    private fun rawFullText(entry: SyndEntry): String? {
        val atomContent = entry.contents.orEmpty().joinToString("\n") { it.value.orEmpty() }
        val encoded =
            (entry.getModule(CONTENT_MODULE_URI) as? com.rometools.modules.content.ContentModule)
                ?.contents.orEmpty().joinToString("\n") { it }
        return (if (textLength(atomContent) >= textLength(encoded)) atomContent else encoded)
            .takeIf { textLength(it) > 0 }
    }

    /** 未来时间戳是脏数据，直接丢弃；null 本身合法。 */
    private fun sanitizePublishedAt(ts: Long?): Long? =
        ts?.takeIf { it <= System.currentTimeMillis() + ONE_DAY_MS }

    /**
     * 封面三级取。第三级用 **sanitize 前**的原始 HTML + 文章链接做 baseUri，
     * 靠 abs:src 补全相对路径（sanitize 会把相对 src 剥掉，事后找就晚了）。
     */
    private fun extractCover(entry: SyndEntry, rawHtml: String?, baseUri: String): String? {
        entry.enclosures.orEmpty().firstOrNull { it.url != null && it.type.orEmpty().startsWith("image") }
            ?.let { return it.url }

        entry.mediaModule()?.mediaContents.orEmpty()
            .firstNotNullOfOrNull { mc -> mc.metadata?.thumbnail.orEmpty().firstOrNull()?.url?.toString() }
            ?.let { return it }

        if (rawHtml != null) {
            Jsoup.parseBodyFragment(rawHtml, baseUri).select("img[src]").firstOrNull()?.attr("abs:src")?.let {
                if (it.startsWith("http")) return it
            }
        }
        return null
    }

    companion object {
        private const val CONTENT_MODULE_URI = "http://purl.org/rss/1.0/modules/content/"
        private const val MEDIA_MODULE_URI = "http://search.yahoo.com/mrss/"

        const val SUMMARY_MAX_LENGTH = 300

        const val MEDIA_KIND_NONE = 0
        const val MEDIA_KIND_VIDEO = 1
        const val MEDIA_KIND_AUDIO = 2

        /**
         * 正文媒体占位卡的类名（与 `ReadingNodes.MEDIA_CARD_CLASS` 对齐）。
         * `-video`/`-audio` 是直链媒体文件（允许内嵌播放），`-embed` 是第三方页面只给外跳。
         */
        const val MEDIA_CARD_CLASS = "media-card"
        const val MEDIA_CARD_VIDEO = "media-card-video"
        const val MEDIA_CARD_AUDIO = "media-card-audio"
        const val MEDIA_CARD_EMBED = "media-card-embed"

        private val MEDIA_CARD_KINDS = setOf(MEDIA_CARD_VIDEO, MEDIA_CARD_AUDIO, MEDIA_CARD_EMBED)

        private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

        /** 实测最大的聚合源约 4MB，12MB 留足余量。 */
        const val MAX_FEED_BYTES = 12 * 1024 * 1024

        /** HTML5 最长的实体名是 CounterClockwiseContourIntegral（31）。 */
        private const val MAX_ENTITY_LENGTH = 32

        private val XML_PREDEFINED_ENTITIES = setOf("amp", "lt", "gt", "quot", "apos")

        /** 按"可见文本长度"比较，避免把带更多 HTML 标签的串误判为更长。 */
        internal fun textLength(html: String?): Int =
            html?.takeIf { it.isNotBlank() }?.let { Jsoup.parse(it).text().length } ?: 0

        /**
         * 净化 HTML：去危险元素与事件属性，保留结构与白名单属性。
         * iframe/object/embed（第三方页面）与 video/audio（直链媒体）都替换为占位卡，正文里永不执行第三方脚本。
         */
        internal fun sanitizeHtml(html: String): String {
            val doc = Jsoup.parseBodyFragment(html)
            doc.select("script, style, object, embed, form, noscript, svg, link, meta").remove()
            // 占位卡替换必须在属性净化之前：cleanAttributes 会剥掉非白名单属性，src 就没了
            doc.select("iframe").forEach { el ->
                absoluteMediaSrc(el.attr("src"))
                    ?.let { el.replaceWith(mediaCard(doc, it, "嵌入内容", MEDIA_CARD_EMBED)) }
                    ?: el.remove()
            }
            doc.select("video").forEach { el ->
                mediaSource(el)?.let { el.replaceWith(mediaCard(doc, it, "视频", MEDIA_CARD_VIDEO)) }
                    ?: el.remove()
            }
            doc.select("audio").forEach { el ->
                mediaSource(el)?.let { el.replaceWith(mediaCard(doc, it, "音频", MEDIA_CARD_AUDIO)) }
                    ?: el.remove()
            }
            val body = doc.body()
            body.select("*").forEach { el -> cleanAttributes(el) }
            return body.html()
        }

        private fun mediaSource(el: Element): String? =
            absoluteMediaSrc(el.attr("src"))
                ?: absoluteMediaSrc(el.selectFirst("source[src]")?.attr("src"))

        /** 只认 http(s) 与协议相对（//host/...），相对路径一律丢弃。 */
        private fun absoluteMediaSrc(raw: String?): String? = when {
            raw == null -> null
            raw.startsWith("http") -> raw
            raw.startsWith("//") -> "https:$raw"
            else -> null
        }

        private fun mediaCard(
            doc: org.jsoup.nodes.Document,
            src: String,
            label: String,
            kindClass: String,
        ): Element {
            val host = runCatching { java.net.URI(src).host }
                .getOrNull().orEmpty().ifEmpty { "外部内容" }
            val card = doc.createElement("a")
                .attr("class", "$MEDIA_CARD_CLASS $kindClass")
                .attr("href", src)
            card.appendElement("span").text("▶")
            card.appendText("$label · $host")
            return card
        }

        internal fun toPlainText(html: String): String? =
            Jsoup.parse(html).text()
                .replace(Regex("\\s+"), " ")
                .trim()
                .takeIf { it.isNotEmpty() }

        /** 部分源的 description/content 是 markdown 纯文本，jsoup 只当普通文本放行，语法符号会原样上卡片。 */
        internal fun stripMarkdown(text: String): String =
            text
                .replace(Regex("!\\[\\S*?]\\([^)]*\\)"), "")
                .replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
                .replace(Regex("```[a-zA-Z]*\\n?|```"), "")
                .replace(Regex("`+"), "")
                // 文本已被压成单行，不能用行首锚点，用「行首或空白后」的位置断言
                .replace(Regex("(^|\\s)#{1,6}\\s+"), "$1")
                .replace(Regex("(^|\\s)>\\s?"), "$1")
                .replace(Regex("(\\*\\*|__)(.*?)\\1"), "$2")
                .replace(Regex("(\\*|_)(.*?)\\1"), "$2")
                .replace(Regex("(^|\\s)([-*_]\\s*){3,}(\\s|$)"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

        private fun cleanAttributes(el: Element) {
            val keep = mutableMapOf<String, String>()
            sanitizeStyle(el.attr("style"))?.let { keep["style"] = it }
            when (el.tagName()) {
                // class 只给媒体占位卡留存：外来自带的 class 一律剥掉
                "a" -> {
                    el.attr("href")?.takeIf { it.startsWith("http") }?.let { keep["href"] = it }
                    mediaCardClass(el.attr("class"))?.let { keep["class"] = it }
                }
                "img" -> {
                    el.attr("src")?.takeIf { it.startsWith("http") }?.let { keep["src"] = it }
                    el.attr("alt")?.let { keep["alt"] = it }
                }
            }
            el.attr("title")?.takeIf { it.isNotBlank() }?.let { keep["title"] = it }
            el.clearAttributes()
            keep.forEach { (k, v) -> el.attr(k, v) }
        }

        /** 逐令牌重建而不是 `startsWith` 放行——否则 `class="media-card evil"` 会把 evil 带出去。 */
        private fun mediaCardClass(raw: String?): String? {
            val tokens = raw.orEmpty().split(' ').filter { it.isNotEmpty() }
            if (MEDIA_CARD_CLASS !in tokens) return null
            val kind = tokens.firstOrNull { it in MEDIA_CARD_KINDS }
            return if (kind == null) MEDIA_CARD_CLASS else "$MEDIA_CARD_CLASS $kind"
        }

        /** 纯视觉、无 JS 面、不破坏阅读布局（不放行 font-size/position 等）。 */
        private val STYLE_PROPERTIES = setOf(
            "color", "background-color", "font-weight", "font-style",
            "text-decoration", "text-decoration-line", "vertical-align", "text-align",
        )

        private val CSS_COLOR = Regex(
            "#[0-9a-fA-F]{3,8}|rgba?\\(\\s*[0-9.]+\\s*,\\s*[0-9.]+\\s*,\\s*[0-9.]+\\s*(,\\s*[0-9.]+\\s*)?\\)",
            RegexOption.IGNORE_CASE,
        )

        /** 声明级过滤，用 `key:value` 分号拼接；全部不合法时返回 null（不写 style 属性）。 */
        internal fun sanitizeStyle(raw: String?): String? {
            val v = raw?.trim().orEmpty()
            if (v.isEmpty()) return null
            val out = v.split(';').mapNotNull { decl ->
                val i = decl.indexOf(':')
                if (i <= 0) return@mapNotNull null
                val prop = decl.substring(0, i).trim().lowercase()
                val value = decl.substring(i + 1).trim()
                if (prop !in STYLE_PROPERTIES || !validStyleValue(prop, value)) return@mapNotNull null
                "$prop:$value"
            }
            return out.takeIf { it.isNotEmpty() }?.joinToString(";")
        }

        private fun validStyleValue(prop: String, value: String): Boolean = when {
            value.isEmpty() -> false
            // 函数值一律只认纯数字 rgb()/rgba()，url()/attr() 全部拒绝
            value.contains('(') -> CSS_COLOR.matches(value)
            // 颜色关键字（red / rebeccapurple / transparent）无 url/JS 面，纯字母即放行
            prop == "color" || prop == "background-color" -> CSS_COLOR.matches(value) || value.all { it.isLetter() }
            else -> value.length <= 32 && !value.containsAnyOf("<>{}\\\"'")
        }

        private fun String.containsAnyOf(chars: CharSequence): Boolean =
            chars.any { this.contains(it) }
    }
}
