package com.cycling.rssradar.ui.feed

import com.cycling.rssradar.core.data.db.FeedEntity

/**
 * 主页内容分区 chip（issue #75，PRD 方案 C）：文章即默认态，不设「文章」chip
 * （ADR-0014 的 contentType=0 归入「全部」）。
 *
 * 纯 UI 概念，不进 core/data：DB 值换算（[dbValue]）只发生在这一个文件，
 * 仓库层与 DAO 只见 Int，不依赖 UI 枚举。
 */
enum class ContentTypeFilter(val dbValue: Int?, val label: String) {
    All(null, "全部"),
    Image(FeedEntity.CONTENT_TYPE_IMAGE, "图片"),
    Video(FeedEntity.CONTENT_TYPE_VIDEO, "视频"),
    Audio(FeedEntity.CONTENT_TYPE_AUDIO, "音频");

    /**
     * 空分区空态文案（纯函数，可测）：「订阅 XX 类源后在此聚合」。
     * 「全部」为空 = 还没有任何订阅，文案沿用 All tab 现有口径。
     */
    fun emptyCopy(): Pair<String, String> = when (this) {
        Image -> "「图片」分区还没有源" to "订阅图片类源后，在这里聚合浏览"
        Video -> "「视频」分区还没有源" to "订阅视频类源后，在这里聚合浏览"
        Audio -> "「音频」分区还没有源" to "订阅音频类源后，在这里聚合浏览"
        All -> "还没有订阅" to "去订阅页添加你的第一个 RSS / Atom 源"
    }
}
