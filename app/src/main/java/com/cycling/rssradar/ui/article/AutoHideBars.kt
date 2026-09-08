package com.cycling.rssradar.ui.article

/**
 * 阅读页工具栏随滚动自动隐藏（ReadYou 差距表第 22 项）的判据，抽成纯函数以便 JVM 单测。
 *
 * 注意术语：**这不是「沉浸阅读」**。沉浸阅读（`ReadingPrefs.immersive`）干的是
 * 内容降噪——剥掉分享按钮、推荐阅读、评论区；这里只是把顶栏/底栏收起来腾出阅读空间。
 * 两件事在 ReadYou 里都叫 immersive，我们按 CONTEXT.md 的术语分开叫，避免开关意义含混。
 */

/**
 * 判定滚动方向的最小位移。比它小的抖动（惯性回弹、WebView 的亚像素滚动）不改状态，
 * 否则工具栏会在手指微动时抽搐。
 */
internal const val BARS_SCROLL_SLOP = 12

/** 顶部这段距离内工具栏恒显：刚进文章时必须是可见的，否则读者找不到返回键。 */
internal const val BARS_TOP_EDGE = 8

/**
 * 下一帧工具栏该不该显示。
 *
 * @param autoHide 偏好开关；关则恒显，本函数等价于 `true`。
 * @param previous 上一次采样到的滚动量，@param current 这一次。
 * @param wasVisible 当前显示状态；位移在死区内时保持原状。
 */
internal fun nextBarsVisible(
    autoHide: Boolean,
    previous: Int,
    current: Int,
    wasVisible: Boolean,
): Boolean {
    if (!autoHide) return true
    if (current <= BARS_TOP_EDGE) return true
    val delta = current - previous
    if (delta > BARS_SCROLL_SLOP) return false // 下滚：让出空间
    if (delta < -BARS_SCROLL_SLOP) return true // 上滚：读者要操作了
    return wasVisible
}
