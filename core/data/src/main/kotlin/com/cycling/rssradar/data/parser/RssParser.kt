package com.cycling.rssradar.core.data.parser

import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.InputStream


/**
 * 解析 RSS 2.0 / Atom 流的纯 JVM 组件，是订阅链路的 TDD 测试缝。
 *
 * 设计依据 `docs/adr/0001` 与真实样本调查（15 源 / 303 条）：
 * - 全文判定不依赖专用字段名：RSSHub 的 RSS 渲染器把全文塞在 description 里，
 *   Atom 渲染器总是输出可为空的 content，还有站点用无前缀的 `<encoded xmlns>` 变体
 *   （rome 的 content 模块按 namespace URI 识别，天然兼容）。
 *   策略：description 与 content 取文本较长者为正文，另一个做摘要底料。
 * - 日期容忍缺失（实测有 10/10 全部无日期的源），并过滤未来时间戳。
 * - summary 是"短摘要"（列表/检索用），不承担正文职责。
 */
class RssParser {

    data class ParsedArticle(
        val link: String,
        val title: String,
        /** 短摘要（纯文本，≤300 字），列表与检索用。 */
        val summary: String?,
        /** 净化后的正文 HTML；null 表示 feed 没给全文。 */
        val contentHtml: String?,
        /** 正文纯文本副本，供检索与阅读时长计算。 */
        val contentText: String?,
        val author: String?,
        val publishedAt: Long?,
        /** 封面图：enclosure → media:thumbnail → 正文首个 img。 */
        val coverUrl: String?,
        /**
         * 条目级媒体种类（ADR-0014）：enclosure / media 模块是 video/audio 时置对应值，
         * 供列表卡片变形（跳原站播放）。数值与 ArticleEntity.MEDIA_KIND_* 对齐。
         */
        val mediaKind: Int = MEDIA_KIND_NONE,
    )

    data class ParsedFeed(
        val title: String,
        val articles: List<ParsedArticle>,
        /**
         * 站点链接（feed XML 的 channel/根 link，指向源站点首页，见 CONTEXT.md「站点链接」）。
         * 与订阅源地址不同：RSSHub 路由的 feed URL 是 RSSHub 实例地址，
         * 而 siteUrl 才是站点图标的抓取目标。容忍缺失（空串）。
         */
        val siteUrl: String = "",
    )

    /** 解析失败（非法 XML / 非 RSS·Atom 内容）时抛 [IllegalArgumentException]。 */
    fun parse(input: InputStream): ParsedFeed {
        val feed = try {
            SyndFeedInput().build(XmlReader(input))
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a valid RSS/Atom feed", e)
        }
        val articles = feed.entries.mapNotNull { it.toArticle() }
        return ParsedFeed(
            title = feed.title?.trim().orEmpty().ifEmpty { "Untitled feed" },
            articles = articles,
            siteUrl = atomSiteUrl(feed),
        )
    }

    /**
     * 站点主页 URL。rome 对 Atom 的 [SyndFeed.getLink] 会取第一个 `<link>`
     * （常是 rel=self 的 feed 自身地址，#54 站点图标会抓错目标）；
     * 手动按 rel 选 alternate，RSS 走 links 为空时的 fallback。
     */
    private fun atomSiteUrl(feed: SyndFeed): String =
        (feed.links.orEmpty().firstOrNull { it.rel == "alternate" }
            ?: feed.links.orEmpty().firstOrNull { it.rel.isNullOrBlank() })
            ?.href?.trim().orEmpty()
            .ifEmpty { feed.link?.trim().orEmpty() }

    private fun SyndEntry.toArticle(): ParsedArticle? {
        val link = this.link?.trim().orEmpty().ifEmpty { uri?.trim().orEmpty() }
        val title = this.title?.trim().orEmpty()
        if (link.isEmpty() && title.isEmpty()) return null

        val rawDescription = description?.value
        val rawFull = rawFullText(this)
        // 取较长者为正文，较短者（若存在）做摘要底料；只有一个源时它既是正文也是摘要底料
        val descLen = textLength(rawDescription)
        val fullLen = textLength(rawFull)
        val contentHtml = when {
            fullLen == 0 && descLen == 0 -> null
            fullLen >= descLen -> rawFull
            else -> rawDescription
        }?.let(::sanitizeHtml)?.takeIf { it.isNotBlank() }
        val contentText = contentHtml?.let(::toPlainText)
        val summarySource = when {
            descLen == 0 -> rawFull
            fullLen == 0 -> rawDescription
            descLen < fullLen -> rawDescription
            else -> rawFull
        }
        // 封面候选必须用 sanitize 前的原始内容：cleanAttributes 会剥掉相对路径的
        // img src，sanitize 后再找"正文首图"就会漏掉它们（abs:src 补全在此处做）
        val coverSource = when {
            fullLen == 0 && descLen == 0 -> null
            fullLen >= descLen -> rawFull
            else -> rawDescription
        }

        return ParsedArticle(
            link = link,
            title = title.ifEmpty { link },
            summary = summarySource?.let(::toPlainText)?.take(SUMMARY_MAX_LENGTH)
                ?.takeIf { it.isNotBlank() },
            contentHtml = contentHtml,
            contentText = contentText,
            author = author?.trim()?.takeIf { it.isNotEmpty() },
            publishedAt = sanitizePublishedAt(publishedDate?.time ?: updatedDate?.time),
            coverUrl = extractCover(this, coverSource, link),
            mediaKind = extractMediaKind(this),
        )
    }

    /**
     * 条目级媒体种类：enclosure 的 MIME type 前缀（video/→视频，audio/→音频），
     * 其次 media 模块 content 的 type。都不命中返回 NONE——不猜：RSS 世界里
     * 没有可靠信号就当普通文章，误判比不判更伤（用户会看到错的卡片形态）。
     */
    private fun extractMediaKind(entry: SyndEntry): Int {
        entry.enclosures.orEmpty().forEach { enclosure ->
            val type = enclosure.type.orEmpty().lowercase()
            if (type.startsWith("video")) return MEDIA_KIND_VIDEO
            if (type.startsWith("audio")) return MEDIA_KIND_AUDIO
        }
        val mediaTypes = (entry.getModule(MEDIA_MODULE_URI)
            as? com.rometools.modules.mediarss.MediaEntryModule)
            ?.mediaContents.orEmpty()
            .mapNotNull { it.type }
        if (mediaTypes.any { it.lowercase().startsWith("video") }) return MEDIA_KIND_VIDEO
        if (mediaTypes.any { it.lowercase().startsWith("audio") }) return MEDIA_KIND_AUDIO
        return MEDIA_KIND_NONE
    }

    /**
     * feed 的全文字段：Atom `<content>` 与 RSS `content:encoded`（含无前缀命名空间变体，
     * rome 的 ContentModule 按 namespace URI 识别）取文本较长者。
     * RSSHub Atom 退化的空 `<content src=.../>` 值为空，不会误判为全文。
     */
    private fun rawFullText(entry: SyndEntry): String? {
        val atomContent = entry.contents.orEmpty().joinToString("\n") { it.value.orEmpty() }
        val encoded =
            (entry.getModule(CONTENT_MODULE_URI) as? com.rometools.modules.content.ContentModule)
                ?.contents.orEmpty().joinToString("\n") { it }
        return when {
            textLength(atomContent) >= textLength(encoded) -> atomContent
            else -> encoded
        }.takeIf { textLength(it) > 0 }
    }

    /** 未来的时间戳是脏数据（实测存在），直接丢弃；null 本身合法。 */
    private fun sanitizePublishedAt(ts: Long?): Long? =
        ts?.takeIf { it <= System.currentTimeMillis() + ONE_DAY_MS }

    /**
     * 封面三级取：enclosure（image 类型）→ media:thumbnail → 正文首个 img。
     * 第三级用 **sanitize 前**的原始 HTML + 文章链接做 baseUri，相对路径 src
     * 靠 abs:src 补全（sanitize 会把相对 src 剥掉，事后找就晚了）。
     */
    private fun extractCover(entry: SyndEntry, rawHtml: String?, baseUri: String): String? {
        entry.enclosures.orEmpty().firstOrNull { it.url != null && it.type.orEmpty().startsWith("image") }
            ?.let { return it.url }

        (entry.getModule(MEDIA_MODULE_URI) as? com.rometools.modules.mediarss.MediaEntryModule)
            ?.mediaContents.orEmpty().firstNotNullOfOrNull { mc ->
                mc.metadata?.thumbnail.orEmpty().firstOrNull()?.url?.toString()
            }
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
        /** 列表摘要长度。ReadYou 用 280，这里给中文多一点余量。 */
        const val SUMMARY_MAX_LENGTH = 300

        /** 条目级媒体种类，与 ArticleEntity.MEDIA_KIND_* 数值对齐（ADR-0014）。 */
        const val MEDIA_KIND_NONE = 0
        const val MEDIA_KIND_VIDEO = 1
        const val MEDIA_KIND_AUDIO = 2

        /**
         * 「这段内容够不够格当正文」的字数门槛。
         *
         * 背景（正文不完整的根因）：description 与 content 取较长者，只给摘要的 feed
         * （RSSHub 大量路由如此）拿到的就是两三百字的摘要；旧实现只要 `contentHtml != null`
         * 就标 `CONTENT_SOURCE_FEED`，于是 `OnDemandFetch.fetch` 的
         * 「已有正文就不抓」早退条件命中 → 详情页永远不去抓原文 → 用户只看到摘要，
         * 且没有任何失败记录。
         *
         * 低于门槛的内容**仍然存进 content 列**（列表摘要与检索要用），但 contentSource 记 NONE，
         * 详情页才会去抓原文。
         */
        const val FULL_TEXT_MIN_CHARS = 300

        /** 该 feed 内容是否够格当正文（够长才算全文，见 [FULL_TEXT_MIN_CHARS]）。 */
        internal fun isFullText(contentHtml: String?, contentText: String?): Boolean {
            if (contentHtml.isNullOrBlank()) return false
            val length = contentText?.length ?: textLength(contentHtml)
            return length >= FULL_TEXT_MIN_CHARS
        }

        private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

        /** 按"可见文本长度"比较，避免把带更多 HTML 标签的串误判为更长。 */
        internal fun textLength(html: String?): Int =
            html?.takeIf { it.isNotBlank() }?.let { Jsoup.parse(it).text().length } ?: 0

        /**
         * 净化 HTML：去 script/style 等危险元素与事件属性，保留结构与白名单属性。
         * 嵌入媒体（iframe/object/embed/video）不静默删除，替换为媒体占位卡
         * （CONTEXT.md「媒体占位卡」）：不开 JS、不动 ADR-0007，视频类源至少可见可跳。
         */
        internal fun sanitizeHtml(html: String): String {
            val doc = Jsoup.parseBodyFragment(html)
            doc.select("script, style, object, embed, form, noscript, svg, link, meta").remove()
            // 占位卡替换必须在属性净化之前：cleanAttributes 会剥掉非白名单属性，src 就没了
            doc.select("iframe").forEach { el ->
                absoluteMediaSrc(el.attr("src"))
                    ?.let { el.replaceWith(mediaCard(doc, it, "嵌入内容")) }
                    ?: el.remove()
            }
            doc.select("video").forEach { el ->
                val src = absoluteMediaSrc(el.attr("src"))
                    ?: absoluteMediaSrc(el.selectFirst("source[src]")?.attr("src"))
                if (src != null) el.replaceWith(mediaCard(doc, src, "视频")) else el.remove()
            }
            val body = doc.body()
            body.select("*").forEach { el -> cleanAttributes(el) }
            return body.html()
        }

        /** 媒体地址归一化：只认 http(s) 与协议相对（//host/...），相对路径一律丢弃。 */
        private fun absoluteMediaSrc(raw: String?): String? = when {
            raw == null -> null
            raw.startsWith("http") -> raw
            raw.startsWith("//") -> "https:$raw"
            else -> null
        }

        /** 构建占位卡：<a class="media-card"><span>▶</span>标签 · 域名</a>。 */
        private fun mediaCard(doc: org.jsoup.nodes.Document, src: String, label: String): Element {
            val host = runCatching { java.net.URI(src).host }
                .getOrNull().orEmpty().ifEmpty { "外部内容" }
            val card = doc.createElement("a")
                .attr("class", "media-card")
                .attr("href", src)
            card.appendElement("span").text("▶")
            card.appendText("$label · $host")
            return card
        }

        /** 纯文本副本：块级元素转分段空白，压缩连续空白。 */
        internal fun toPlainText(html: String): String? =
            Jsoup.parse(html).text()
                .replace(Regex("\\s+"), " ")
                .trim()
                .takeIf { it.isNotEmpty() }

        private fun cleanAttributes(el: Element) {
            val keep = mutableMapOf<String, String>()
            // style 声明级白名单（原生渲染器还原行内样式 + WebView 路 CSS 视觉），
            // 其余属性照旧全剥
            sanitizeStyle(el.attr("style"))?.let { keep["style"] = it }
            when (el.tagName()) {
                // class 仅媒体占位卡在用（外来自带的 class 无害：样式只匹配 .media-card/.play）
                "a" -> {
                    el.attr("href")?.takeIf { it.startsWith("http") }?.let { keep["href"] = it }
                    el.attr("class")?.takeIf { it == "media-card" }?.let { keep["class"] = it }
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

        /** 允许保留的 CSS 声明：纯视觉、无 JS 面、不破坏阅读布局（不放行 font-size/position 等）。 */
        private val STYLE_PROPERTIES = setOf(
            "color", "background-color", "font-weight", "font-style",
            "text-decoration", "text-decoration-line", "vertical-align", "text-align",
        )

        /** 安全颜色：hex / rgb() / rgba() / 常见命名色。url() 等一律拒绝。 */
        private val CSS_COLOR = Regex(
            "#[0-9a-fA-F]{3,8}|rgba?\\(\\s*[0-9.]+\\s*,\\s*[0-9.]+\\s*,\\s*[0-9.]+\\s*(,\\s*[0-9.]+\\s*)?\\)",
            RegexOption.IGNORE_CASE,
        )
        private val NAMED_COLORS = setOf(
            "black", "white", "red", "green", "blue", "yellow", "orange", "purple",
            "gray", "grey", "silver", "maroon", "navy", "olive", "lime", "aqua",
            "teal", "fuchsia", "cyan", "magenta", "gold", "pink", "brown",
        )

        /**
         * 行内样式声明级过滤：只留 [STYLE_PROPERTIES] 且值合法的声明，
         * 用 `key:value` 分号拼接（解析端与 WebView 都按标准格式消费）。
         * 全部声明不合法时返回 null（不写 style 属性）。
         */
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
            // 括号里只有数字的 rgb()/rgba() 才放行；函数值一律不进 url()/attr() 等
            value.contains('(') -> CSS_COLOR.matches(value)
            prop == "color" || prop == "background-color" ->
                value.lowercase() in NAMED_COLORS || CSS_COLOR.matches(value)
            else -> value.length <= 32 && !value.containsAnyOf("<>{}\\\"'")
        }

        private fun String.containsAnyOf(chars: CharSequence): Boolean =
            chars.any { this.contains(it) }
    }
}
