package com.cycling.rssradar.ui.library

import com.cycling.rssradar.core.model.library.LibrarySort

/**
 * [LibrarySort] 的文案（文案映射随领域走）。
 *
 * 原先挂在 app/i18n/FeatureTexts.kt —— 那个文件按技术层归拢，收藏的枚举文案
 * 和过滤规则、备份策略的混在一处；模块化时拆散归位。
 * LibraryRange 的文案因搜索也在用，下沉到了 core:ui（core.ui.labels）。
 */
fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.STARRED_AT -> R.string.library_sort_added
    LibrarySort.PUBLISHED_AT -> R.string.library_sort_published
    LibrarySort.FEED -> R.string.library_sort_feed
}
