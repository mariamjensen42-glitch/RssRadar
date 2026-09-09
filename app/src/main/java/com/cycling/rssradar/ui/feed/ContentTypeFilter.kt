package com.cycling.rssradar.ui.feed

import com.cycling.rssradar.core.data.db.FeedEntity

/**
 * 主页内容分区 chip（issue #75，PRD 方案 C）：文章即默认态，不设「文章」chip
 * （ADR-0014 的 contentType=0 归入「全部」）。
 *
 * 纯 UI 概念，不进 core/data：DB 值换算（[dbValue]）只发生在这一个文件，
 * 仓库层与 DAO 只见 Int，不依赖 UI 枚举。
 */
enum class ContentTypeFilter(val dbValue: Int?) {
    All(null),
    Image(FeedEntity.CONTENT_TYPE_IMAGE),
    Video(FeedEntity.CONTENT_TYPE_VIDEO),
    Audio(FeedEntity.CONTENT_TYPE_AUDIO),
}
