package com.cycling.rssradar.ui.feed

import com.cycling.rssradar.core.model.MarkAsReadCondition

/**
 * 信息流与订阅域的枚举 → 文案资源映射（ADR-0017 §3）。
 *
 * 枚举本身不带中文：core 与 ui 的枚举保持纯数据，翻译只在 UI 层按当前语言取，
 * 纯 JVM 测试因此不碰 android 资源。
 *
 * 原先在 app 的 i18n 包里，但那时的 import 关系是双向的——本文件要 import
 * `ui.feed.ContentTypeFilter`，而 ui/feed 又要 import 本文件的 `labelRes`。
 * 双向纠缠使 feed 无法出 app，故随域搬进 feature:feed。
 * 资源引用的是本模块 R（namespace 与 package 同为 ui.feed，裸名可用）。
 */
fun ContentTypeFilter.labelRes(): Int = when (this) {
    ContentTypeFilter.Article -> R.string.ctype_article
    ContentTypeFilter.Image -> R.string.ctype_image
    ContentTypeFilter.Video -> R.string.ctype_video
    ContentTypeFilter.Audio -> R.string.ctype_audio
}

fun ContentTypeFilter.emptyTitleRes(): Int = R.string.ctype_empty_title

fun ContentTypeFilter.emptyDescRes(): Int = R.string.ctype_empty_desc

fun MarkAsReadCondition.labelRes(): Int = when (this) {
    MarkAsReadCondition.ONE_DAY -> R.string.mark_cond_1d
    MarkAsReadCondition.THREE_DAYS -> R.string.mark_cond_3d
    MarkAsReadCondition.SEVEN_DAYS -> R.string.mark_cond_7d
    MarkAsReadCondition.ALL -> R.string.filter_all
}
