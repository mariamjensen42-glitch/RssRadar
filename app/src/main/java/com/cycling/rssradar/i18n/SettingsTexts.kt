package com.cycling.rssradar.i18n

import androidx.annotation.StringRes
import com.cycling.rssradar.R
import com.cycling.rssradar.core.data.store.KeepArchived
import com.cycling.rssradar.core.data.store.LinkOpenMode
import com.cycling.rssradar.core.data.store.ListDescMode
import com.cycling.rssradar.core.data.store.ListViewMode
import com.cycling.rssradar.core.data.store.ShareContentFormat
import com.cycling.rssradar.core.data.store.SyncInterval

/**
 * 设置域枚举文案的资源映射（ADR-0017）：core 层的中文 label 是数据口径，
 * 界面展示一律走这里按当前语言取 res。label lambda 非 Composable 时
 * 先在 Composable 里取成 map（见 SettingsSubPages 的 languageLabels 模式）。
 */
fun LinkOpenMode.labelRes(): Int = when (this) {
    LinkOpenMode.BROWSER -> R.string.set_link_browser
    LinkOpenMode.ASK -> R.string.set_link_ask
}

fun ShareContentFormat.labelRes(): Int = when (this) {
    ShareContentFormat.TITLE_LINK -> R.string.set_share_title_link
    ShareContentFormat.LINK -> R.string.set_share_link
    ShareContentFormat.TITLE_SUMMARY_LINK -> R.string.set_share_title_summary_link
}

fun ListDescMode.labelRes(): Int = when (this) {
    ListDescMode.NONE -> R.string.set_desc_none
    ListDescMode.SHORT -> R.string.set_desc_short
    ListDescMode.LONG -> R.string.set_desc_long
}

fun ListViewMode.labelRes(): Int = when (this) {
    ListViewMode.LIST -> R.string.set_view_list
    ListViewMode.CARD -> R.string.set_view_card
    ListViewMode.MAGAZINE -> R.string.set_view_magazine
    ListViewMode.GRID -> R.string.set_view_grid
}

fun SyncInterval.labelRes(): Int = when (this) {
    SyncInterval.MANUALLY -> R.string.set_sync_manually
    SyncInterval.EVERY_1_HOUR -> R.string.set_sync_1h
    SyncInterval.EVERY_3_HOURS -> R.string.set_sync_3h
    SyncInterval.EVERY_6_HOURS -> R.string.set_sync_6h
    SyncInterval.EVERY_12_HOURS -> R.string.set_sync_12h
    SyncInterval.EVERY_1_DAY -> R.string.set_sync_1d
}

fun KeepArchived.labelRes(): Int = when (this) {
    KeepArchived.ALWAYS -> R.string.set_keep_always
    KeepArchived.ONE_DAY -> R.string.set_keep_1d
    KeepArchived.TWO_DAYS -> R.string.set_keep_2d
    KeepArchived.THREE_DAYS -> R.string.set_keep_3d
    KeepArchived.ONE_WEEK -> R.string.set_keep_1w
    KeepArchived.TWO_WEEKS -> R.string.set_keep_2w
    KeepArchived.ONE_MONTH -> R.string.set_keep_1m
}
