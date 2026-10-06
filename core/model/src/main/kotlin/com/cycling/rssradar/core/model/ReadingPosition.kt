package com.cycling.rssradar.core.model

/**
 * 阅读位置的比例换算（阅读位置记忆）。
 *
 * 存**比例**而不是像素：同一篇文章的字号、行距、可用宽度、图片是否已加载都会改变
 * 总高度，像素位置在下次打开时可能指向完全不同的段落；比例在排版变化后仍落在同一篇
 * 文章的相近位置。
 *
 * 两种滚动宿主共用同一套换算：整页模式的外层 Compose `ScrollState`
 * 与视口模式的 WebView 内部滚动，都只要给出「当前位置」与「最大可滚动量」。
 */
object ReadingPosition {

    /** 低于这个比例视为还在开头（多是很短的停留），不值得记。 */
    const val MIN_RATIO = 0.02f

    /** 高于这个比例视为已经读完，下次打开回到开头——读完的文章再进来通常是重读或回看开头。 */
    const val MAX_RATIO = 0.98f

    /** 滚动量 → 比例（0..1）。内容不足一屏（maxScroll <= 0）时为 0。 */
    fun ratio(scrollPos: Int, maxScroll: Int): Float =
        if (maxScroll <= 0) 0f else (scrollPos.toFloat() / maxScroll).coerceIn(0f, 1f)

    /** 比例 → 滚动量。maxScroll 未知（<= 0）时回到开头。 */
    fun pixels(saved: Float, maxScroll: Int): Int =
        if (maxScroll <= 0) 0 else (saved.coerceIn(0f, 1f) * maxScroll).toInt()

    /** 这个比例值不值得记住：还在开头或已经读完，都不记（并把旧记录作废）。 */
    fun isWorthRemembering(saved: Float): Boolean =
        saved > MIN_RATIO && saved < MAX_RATIO
}
