package com.cycling.rssradar.core.ui.labels

import com.cycling.rssradar.core.model.library.LibraryRange
import com.cycling.rssradar.core.ui.R

/**
 * [LibraryRange] 的文案（ADR-0017：文案映射随领域走）。
 *
 * 为什么在 core:ui 而不在 feature:library：收藏页与搜索的二次筛选都要它，
 * 留在 library 里会逼搜索反向依赖 library（feature 之间禁止互依）——
 * 判据就是「被依赖 ≥2 次即非 feature 私有物」。
 * 兄弟枚举 [LibrarySort] 只有收藏页用，因此仍留在 feature:library。
 */
fun LibraryRange.labelRes(): Int = when (this) {
    LibraryRange.ALL -> R.string.library_range_all
    LibraryRange.WEEK -> R.string.library_range_week
    LibraryRange.MONTH -> R.string.library_range_month
    LibraryRange.YEAR -> R.string.library_range_year
}
