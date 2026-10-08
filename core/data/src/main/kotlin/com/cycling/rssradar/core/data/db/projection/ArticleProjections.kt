package com.cycling.rssradar.core.data.db.projection

import androidx.room.Embedded
import com.cycling.rssradar.core.data.db.entity.ArticleEntity

/** 文章 + 所属订阅的扁平视图，方便 UI 直接渲染。 */
data class ArticleWithFeed(
    @Embedded val article: ArticleEntity,
    val feedTitle: String,
    val feedGroup: String,
    val feedIconUrl: String?,
    /**
     * AI 判定的「值不值得读」（0~100）：降噪的信息价值优先，没有就退回质量总分；
     * null = 这篇还没被评估过。只有走 `AI_VALUE_JOIN` 的查询会填它（默认时间排序的
     * 查询不带这个 JOIN，所以恒为 null）——卡片上的角标据此决定显示与否。
     */
    val aiValue: Int? = null,
)

/** 重建检索索引的输入行：标题与摘要优先，正文用于补齐语料。 */
data class ArticleSearchSourceRow(
    val id: Long,
    val title: String,
    val summary: String?,
    val contentText: String?,
)

/**
 * 过滤规则扫描行：规则要判的字段（标题/摘要/正文/作者）+ 作用域所需的 feedId 与分组，
 * 外加两个豁免标记（收藏与稍后读不吃 HIDE）。
 */
data class RuleScanRow(
    val id: Long,
    val feedId: Long,
    val feedGroup: String,
    val title: String,
    val summary: String?,
    val contentText: String?,
    val author: String?,
    val isStarred: Boolean,
    val isBookmarked: Boolean,
)

/**
 * 推荐画像的输入行：所有"用户真实表达过兴趣"的文章。
 * 打开过（lastOpenedAt 非空）、收藏、稍后读都算——三选一即可入样本，
 * 没有这些信号的文章不参与画像（画像只由真实行为驱动，不预置兴趣类别）。
 */
data class EngagementRow(
    val id: Long,
    val feedId: Long,
    val title: String,
    val summary: String?,
    val lastOpenedAt: Long?,
    val isStarred: Boolean,
    val isBookmarked: Boolean,
)
