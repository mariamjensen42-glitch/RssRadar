package com.cycling.rssradar.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一次「按需抓原文」的留痕（可观测性）。
 *
 * 抓不到正文时旧实现只有一个静默的 null，既不知道是站点反爬、限流还是提取器没认出容器——
 * 有了这张表才能回答「哪些站点抓不到、为什么、重试了几次」。
 */
@Entity(
    tableName = "content_fetch_log",
    indices = [Index("host"), Index("createdAt")],
)
data class ContentFetchLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val link: String,
    val host: String,
    val statusCode: Int?,
    val attempts: Int,
    /**
     * `ponytail:` 多页拼接已从抓取层移除（`FetchReport` 不再有这个字段），成功恒 1、失败恒 0，
     * 全仓无读取点，只剩历史行的真实页数。
     * 天花板：这一列既不被读也不再反映新行的真实页数，是一次“留着待清”的取舍；
     * 升级路径：下一次 schema 变更（v18 → v19）随重建表迁移一并删列，本行同时消失。
     */
    val pages: Int,
    /** 是否拿到了可写入的正文（false = 抓取/提取失败）。 */
    val ok: Boolean,
    /** [com.cycling.rssradar.core.model.FetchFailure] 的 name，成功时 null。 */
    val failure: String?,
    /** [com.cycling.rssradar.core.model.ExtractionIssue] 的 name，成功时非空。 */
    val issue: String?,
    val contentChars: Int,
    val durationMs: Long,
    val createdAt: Long,
)
