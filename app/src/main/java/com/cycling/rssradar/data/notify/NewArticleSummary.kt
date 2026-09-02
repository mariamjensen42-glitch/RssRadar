package com.cycling.rssradar.data.notify

import com.cycling.rssradar.data.db.ArticleWithFeed

/**
 * 新文章通知（#31）的文案汇总：纯函数，不碰 Android，JVM 可测。
 *
 * 原则同 AI 摘要——数字必须真实：标题条数就是列表长度，多出来的只报总数，
 * 不编造"等 N 篇"以外的任何信息。
 */
object NewArticleSummary {

    /** 通知里最多展开几条标题。 */
    const val MAX_TITLES = 3

    data class Summary(
        /** 通知标题行，如「12 篇新文章」。 */
        val title: String,
        /** 展开后的正文（BigText）：逐条「源名 · 标题」。 */
        val bigText: String,
        /** 收起时的一行摘要。 */
        val contentText: String,
    )

    fun build(articles: List<ArticleWithFeed>): Summary? {
        if (articles.isEmpty()) return null
        val total = articles.size
        val shown = articles.take(MAX_TITLES)
        val lines = shown.map { item ->
            val feed = item.feedTitle.trim()
            val title = item.article.title.trim().ifBlank { "（无标题）" }
            if (feed.isEmpty()) title else "$feed · $title"
        }
        val bigText = buildString {
            append(lines.joinToString("\n"))
            if (total > shown.size) append("\n还有 ${total - shown.size} 篇…")
        }
        val first = lines.first()
        val contentText = if (total == 1) first else "$first 等 $total 篇"
        return Summary(
            title = "$total 篇新文章",
            bigText = bigText,
            contentText = contentText,
        )
    }
}
