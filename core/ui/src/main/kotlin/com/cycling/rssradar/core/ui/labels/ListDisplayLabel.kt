package com.cycling.rssradar.core.ui.labels

import com.cycling.rssradar.core.model.FeedValueMode
import com.cycling.rssradar.core.model.ListViewMode
import com.cycling.rssradar.core.ui.R

/**
 * [ListViewMode] 的文案（文案映射随领域走）。
 *
 * 为什么在 core:ui：信息流顶栏的样式切换与设置页「列表样式」都要它 ——
 * 判据与前例一致：**被依赖 >=2 次即非 feature 私有物**。
 * 与它同为设置域的 LinkOpenMode / SyncInterval 等只有设置页用，仍留在 feature:settings。
 */
fun ListViewMode.labelRes(): Int = when (this) {
    ListViewMode.LIST -> R.string.set_view_list
    ListViewMode.CARD -> R.string.set_view_card
    ListViewMode.MAGAZINE -> R.string.set_view_magazine
    ListViewMode.GRID -> R.string.set_view_grid
}

/** [FeedValueMode] 的文案（短名）。信息流顶栏与设置页共用。 */
fun FeedValueMode.labelRes(): Int = when (this) {
    FeedValueMode.OFF -> R.string.set_value_time
    FeedValueMode.BY_VALUE -> R.string.set_value_by_value
    FeedValueMode.ONLY_GOOD -> R.string.set_value_only_good
}
