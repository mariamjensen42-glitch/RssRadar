package com.cycling.rssradar.data

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
    )

    data class ParsedFeed(
        val title: String,
        val articles: List<ParsedArticle>,
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
        )
    }

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

        return ParsedArticle(
            link = link,
            title = title.ifEmpty { link },
            summary = summarySource?.let(::toPlainText)?.take(SUMMARY_MAX_LENGTH)
                ?.takeIf { it.isNotBlank() },
            contentHtml = contentHtml,
            contentText = contentText,
            author = author?.trim()?.takeIf { it.isNotEmpty() },
            publishedAt = sanitizePublishedAt(publishedDate?.time ?: updatedDate?.time),
            coverUrl = extractCover(this, contentHtml),
        )
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

    /** 封面三级取：enclosure（image 类型）→ media:thumbnail → 正文首个 img。 */
    private fun extractCover(entry: SyndEntry, contentHtml: String?): String? {
        entry.enclosures.orEmpty().firstOrNull { it.url != null && it.type.orEmpty().startsWith("image") }
            ?.let { return it.url }

        (entry.getModule(MEDIA_MODULE_URI) as? com.rometools.modules.mediarss.MediaEntryModule)
            ?.mediaContents.orEmpty().firstNotNullOfOrNull { mc ->
                mc.metadata?.thumbnail.orEmpty().firstOrNull()?.url?.toString()
            }
            ?.let { return it }

        if (contentHtml != null) {
            Jsoup.parseBodyFragment(contentHtml).select("img[src]").firstOrNull()?.attr("abs:src")?.let {
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
        private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

        /** 按"可见文本长度"比较，避免把带更多 HTML 标签的串误判为更长。 */
        internal fun textLength(html: String?): Int =
            html?.takeIf { it.isNotBlank() }?.let { Jsoup.parse(it).text().length } ?: 0

        /** 净化 HTML：去 script/style/iframe 等危险元素与事件属性，保留结构与白名单属性。 */
        internal fun sanitizeHtml(html: String): String {
            val doc = Jsoup.parseBodyFragment(html)
            doc.select("script, style, iframe, object, embed, form, noscript, svg, link, meta").remove()
            val body = doc.body()
            body.select("*").forEach { el -> cleanAttributes(el) }
            return body.html()
        }

        /** 纯文本副本：块级元素转分段空白，压缩连续空白。 */
        internal fun toPlainText(html: String): String? =
            Jsoup.parse(html).text()
                .replace(Regex("\\s+"), " ")
                .trim()
                .takeIf { it.isNotEmpty() }

        private fun cleanAttributes(el: Element) {
            val keep = mutableMapOf<String, String>()
            when (el.tagName()) {
                "a" -> el.attr("href")?.takeIf { it.startsWith("http") }?.let { keep["href"] = it }
                "img" -> {
                    el.attr("src")?.takeIf { it.startsWith("http") }?.let { keep["src"] = it }
                    el.attr("alt")?.let { keep["alt"] = it }
                }
            }
            el.attr("title")?.takeIf { it.isNotBlank() }?.let { keep["title"] = it }
            el.clearAttributes()
            keep.forEach { (k, v) -> el.attr(k, v) }
        }
    }
}
