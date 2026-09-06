package com.cycling.rssradar.core.data

import com.cycling.rssradar.core.data.db.ArticleEntity
import com.cycling.rssradar.core.data.parser.RssParser

/**
 * **「够格正文」判定的唯一落点**（原散布在 RssParser / RefreshEngine / OnDemandFetch /
 * ArticleDetailViewModel 四处，靠跨文件注释互链维系，漏改曾导致摘要型 feed 永远抓不到原文）。
 *
 * 业务规则一句话：低于 [FULL_TEXT_MIN_CHARS] 的摘要级内容**仍然存进 content 列**
 * （列表摘要与检索要用），但 contentSource 记 NONE——详情页据此才会去按需抓原文。
 * 「够不够格」与「存库的能不能当正文用」都从这里问，别处不再各自实现判断。
 */
object ContentQualification {

    /**
     * 「这段内容够不够格当正文」的字数门槛。
     *
     * 背景（正文不完整的根因）：description 与 content 取较长者，只给摘要的 feed
     * （RSSHub 大量路由如此）拿到的就是两三百字的摘要；旧实现只要 `contentHtml != null`
     * 就标 `CONTENT_SOURCE_FEED`，于是按需抓取的「已有正文就不抓」早退条件命中 →
     * 详情页永远不去抓原文，且没有任何失败记录。
     */
    const val FULL_TEXT_MIN_CHARS = 300

    /** 写侧判定：feed 解析出的内容是否够格当正文（够长才算全文）。 */
    fun qualifies(contentHtml: String?, contentText: String?): Boolean {
        if (contentHtml.isNullOrBlank()) return false
        val length = contentText?.length ?: RssParser.textLength(contentHtml)
        return length >= FULL_TEXT_MIN_CHARS
    }

    /** 写侧归类：够格记 feed 自带，不够格记 NONE（内容照存，供摘要与检索）。 */
    fun contentSourceFor(contentHtml: String?, contentText: String?): Int =
        if (qualifies(contentHtml, contentText)) {
            ArticleEntity.CONTENT_SOURCE_FEED
        } else {
            ArticleEntity.CONTENT_SOURCE_NONE
        }

    /**
     * 读侧判定：这篇已入库的文章是否已有「够格」的正文。
     * contentSource != NONE 才够格——摘要级 feed 内容虽然也躺在 content 列，但记的是
     * NONE（见 [FULL_TEXT_MIN_CHARS]），否则详情页永远不会去抓原文。
     */
    fun hasUsableContent(article: ArticleEntity): Boolean =
        article.content != null && article.contentSource != ArticleEntity.CONTENT_SOURCE_NONE
}
