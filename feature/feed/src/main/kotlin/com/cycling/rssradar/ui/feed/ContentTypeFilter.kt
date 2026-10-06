package com.cycling.rssradar.ui.feed

import com.cycling.rssradar.core.data.db.entity.FeedEntity

/**
 * 主页内容分区（issue #75）：与订阅源的 `feeds.contentType` 一一对应，**不含「不过滤」那一档** ——
 * 四类源各占一个分区，「文章」就是默认视图。
 *
 * 入口沿革：PRD 方案 C 原设计为「全部 + 图片/视频/音频」（文章类归入「全部」）
 * → 2026-09-05 因「两行 9 胶囊拥挤」收进筛选弹层 → 2026-10-06 按 Folo 形态搬回首页一行
 * （选中项展开成图标 + 文字的胶囊、未选中项缩成图标方块，见 `ContentTypeFilterRow`）
 * → 同日再对齐 Folo 的分区表，用「文章」替掉「全部」。
 *
 * 这条替换有个必须知道的产品后果：**默认视图只含文章类源**，图片/视频/音频源的条目不再
 * 出现在首页首屏，要切到对应分区才看得到（订阅页与单源页不受影响）。
 * 所以 [dbValue] 是非空 Int —— 每个分区都映射到一个真实的 contentType，没有「恒真」的选项，
 * 仓库层那句 `contentType == null` 的空短路在 UI 这条路上不会再走到。
 *
 * 纯 UI 概念，不进 core/data：DB 值换算（[dbValue]）只发生在这一个文件，
 * 仓库层与 DAO 只见 Int，不依赖 UI 枚举。
 */
enum class ContentTypeFilter(val dbValue: Int) {
    Article(FeedEntity.CONTENT_TYPE_ARTICLE),
    Image(FeedEntity.CONTENT_TYPE_IMAGE),
    Video(FeedEntity.CONTENT_TYPE_VIDEO),
    Audio(FeedEntity.CONTENT_TYPE_AUDIO),
}
