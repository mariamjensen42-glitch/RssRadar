package com.cycling.rssradar.core.data.parser

import java.net.URI

/**
 * 条目链接的补全与兜底（必应每日壁纸这类源的修复）。
 *
 * 两个现实问题在同一个位置上解决：
 * 1. **相对链接**：RSS 规范要求 `<link>` 是绝对 URL，但必应每日壁纸
 *    （`HPImageArchive.aspx?format=rss`）返回的是 `/th?id=...jpg&pid=hp` —— 没有 host。
 *    原样入库会让链接点不开，也让封面的三级提取（enclosure / media:thumbnail / 正文首图）
 *    全部落空。
 * 2. **链接本身就是图片**：这类源的一个条目就是一张图，上面三级一条都不命中，
 *    列表里只剩一个字母色块。所以补一级兜底：链接指向图片资源时把它当封面。
 *
 * 归一化还有一个不可省的用途：`link` 是文章的**去重键**（见 RefreshEngine 的 upsert）。
 * 库里可能存着旧版本解析出的相对 link，补全动作一旦生效而匹配侧不归一化，
 * 同一篇文章会被当成新的再插一遍。
 *
 * public 是为了让守门测试能从 app/src/test 直接调（CI 只跑 app 的单测，不跑本模块的）。
 */
object FeedUrlResolver {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")

    /** 以 [feedUrl] 为基准把相对链接补成绝对地址；已经是绝对的、空的、或没有基准时原样返回。 */
    fun absolutize(url: String, feedUrl: String): String {
        if (url.isBlank() || feedUrl.isBlank()) return url
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        return runCatching { URI(feedUrl).resolve(url).toString() }.getOrDefault(url)
    }

    /**
     * 链接指向图片资源时返回它本身，否则 null。
     *
     * 路径后缀命中即可；另外认「查询串里带图片扩展名」—— 必应的图片地址正是这种形态
     * （`/th?id=OHR.xxx_1920x1080.jpg&pid=hp`，路径本身没有后缀）。这条判定只作为封面的
     * 最后一级兜底，所以误判的代价仅限于「本来就没有封面的条目多显示一张可能加载失败的图」。
     */
    fun imageUrlOrNull(url: String): String? {
        if (!url.startsWith("http")) return null
        val noFragment = url.substringBefore('#')
        if (noFragment.substringBefore('?').substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS) {
            return url
        }
        val query = noFragment.substringAfter('?', "").lowercase()
        if (query.isEmpty()) return null
        return url.takeIf { IMAGE_EXTENSIONS.any { ext -> query.contains(".$ext") } }
    }

    /**
     * 补全条目链接，并在解析器给不出封面时用图片型链接兜底。
     * 逐条只在真的变了时才 `copy`，避免整份 feed 无谓重建。
     */
    fun resolveFeed(feed: RssParser.ParsedFeed, feedUrl: String): RssParser.ParsedFeed {
        val resolved = feed.articles.map { article ->
            val link = absolutize(article.link, feedUrl)
            val cover = article.coverUrl ?: imageUrlOrNull(link)
            if (link == article.link && cover == article.coverUrl) {
                article
            } else {
                article.copy(link = link, coverUrl = cover)
            }
        }
        return feed.copy(articles = resolved)
    }
}
