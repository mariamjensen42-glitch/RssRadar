package com.cycling.rssradar.data

import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import java.io.InputStream

/** 解析 RSS 2.0 / Atom 流的纯 JVM 组件，是订阅链路的 TDD 测试缝。 */
class RssParser {

    data class ParsedArticle(
        val link: String,
        val title: String,
        val summary: String?,
        val publishedAt: Long?,
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
        return ParsedArticle(
            link = link,
            title = title.ifEmpty { link },
            summary = description?.value?.stripHtml(),
            publishedAt = publishedDate?.time ?: updatedDate?.time,
        )
    }

    private fun String.stripHtml(): String? =
        replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(500)
            .takeIf { it.isNotEmpty() }
}
