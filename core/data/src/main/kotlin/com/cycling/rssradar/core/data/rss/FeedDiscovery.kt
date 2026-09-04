package com.cycling.rssradar.core.data.rss

import org.jsoup.Jsoup

/**
 * Feed 自动发现（#5）的纯函数层：从站点 HTML 里挑出候选 feed 地址。
 *
 * 依据标准做法：`<link rel="alternate" type="application/rss+xml|atom+xml">` 是站点
 * 声明自己 feed 的方式（WordPress/Hugo/Hexo/Ghost 都这么干）。type 只认 feed 相关的
 * 几种——`application/xml` 之类太宽泛，会把 sitemap、XLST 之类一起捞进来。
 *
 * 纯 JVM（jsoup），无网络依赖：候选提取是发现链路的测试缝。
 * 抓取与校验（是否真能解析出文章）在 FeedRepository，那里才碰网络。
 */
object FeedDiscovery {

    /** 候选地址上限：够覆盖多语言/多栏目站点，又不至于为奇怪的页面打十几个请求。 */
    const val MAX_CANDIDATES = 8

    /** link 标签里认作 feed 的 MIME 类型。 */
    private val FEED_MIME_TYPES = setOf(
        "application/rss+xml",
        "application/atom+xml",
        "application/rdf+xml",
        "application/rss",
        "application/atom",
        "text/xml",
        "text/rss+xml",
        "text/atom+xml",
    )

    /** 站点没声明 feed 时的兜底猜测路径（静态博客生成器的常见默认输出）。 */
    val COMMON_PATHS = listOf(
        "/feed", "/feed.xml", "/rss", "/rss.xml",
        "/atom.xml", "/index.xml", "/feed/", "/rss/",
    )

    /**
     * 从 HTML 里提取 feed 候选地址（已解析为绝对地址）。
     * 顺序 = 文档顺序（通常主 feed 在前），去重保序。
     */
    fun candidateLinks(baseUrl: String, html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val doc = runCatching { Jsoup.parse(html, baseUrl) }.getOrNull() ?: return emptyList()
        val out = LinkedHashSet<String>()
        doc.select("link[rel~=(?i)alternate]").forEach { link ->
            val type = link.attr("type").trim().lowercase()
            if (type !in FEED_MIME_TYPES) return@forEach
            val href = link.attr("abs:href").trim()
            if (href.startsWith("http")) out += href
        }
        return out.take(MAX_CANDIDATES)
    }

    /** 站点根 + 常见路径：仅在 link 标签一无所获时兜底。 */
    fun guessedLinks(siteUrl: String): List<String> {
        val root = siteUrl.trimEnd('/')
        if (!root.startsWith("http")) return emptyList()
        return COMMON_PATHS.map { root + it }
    }
}
