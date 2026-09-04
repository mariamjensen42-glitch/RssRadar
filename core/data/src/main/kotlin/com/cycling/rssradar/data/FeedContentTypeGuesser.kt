package com.cycling.rssradar.core.data

import com.cycling.rssradar.core.data.db.FeedEntity

/**
 * 订阅时的内容类型预判（ADR-0014）：从订阅地址与标题的关键词猜 feed 的内容类型。
 *
 * 为什么 feed 级：RSS 的 item 没有可靠的「这是什么」字段；而订阅源几乎总是
 * 单一性质的（一个推特路由全是推文、一个播客路由全是音频）。feed 级猜一次、
 * 用户可改，比逐条 item 猜稳定得多。猜错不伤数据——改类型只影响列表形态。
 *
 * 只认高置信信号（域名/路由名），宁缺勿滥：猜不出来就是文章类。
 */
object FeedContentTypeGuesser {

    /** 关键词按顺序匹配，先命中先得。都是实际存在的 RSSHub 路由/站点域名形态。 */
    private val RULES = listOf(
        // 图片：整条内容就是一张图的源
        FeedEntity.CONTENT_TYPE_IMAGE to listOf(
            "rsshub://pixiv", "pixiv.net", "instagram.com", "nasa/apod",
        ),
        // 视频：视频平台路由，内容主体是视频
        FeedEntity.CONTENT_TYPE_VIDEO to listOf(
            "rsshub://bilibili", "bilibili.com", "youtube.com", "/youtube/",
            "douyin.com", "/douyin/",
        ),
        // 音频：播客
        FeedEntity.CONTENT_TYPE_AUDIO to listOf(
            "xiaoyuzhoufm.com", "podcast", "/podcast/",
        ),
    )

    /**
     * 猜内容类型。信号 = 订阅地址（含 RSSHub 路由路径）与标题的小写形态；
     * 都不命中返回 [FeedEntity.CONTENT_TYPE_ARTICLE]。
     */
    fun guess(feedUrl: String, feedTitle: String): Int {
        val haystack = "${feedUrl.lowercase()} ${feedTitle.lowercase()}"
        RULES.forEach { (type, keywords) ->
            if (keywords.any { haystack.contains(it) }) return type
        }
        return FeedEntity.CONTENT_TYPE_ARTICLE
    }
}
