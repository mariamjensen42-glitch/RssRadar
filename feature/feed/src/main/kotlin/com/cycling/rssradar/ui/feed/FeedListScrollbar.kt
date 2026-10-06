package com.cycling.rssradar.ui.feed

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * thumb 长度占轨道高度的比例。**常量** —— 长度不参与任何计算，也就不可能随滚动或翻页变。
 */
private const val THUMB_HEIGHT_RATIO = 0.14f

/** thumb 的像素下限：短列表上也要看得见（本指示条不接手势，纯看的）。 */
private val THUMB_MIN_HEIGHT = 40.dp

/**
 * thumb 顶端位置占可滚动范围的比例（0..1）。
 *
 * 抽成纯函数是为了用测试锁住那条不变式：**结果只由 [firstVisibleIndex] 与 [totalCount] 决定**，
 * 分母里不能混进任何会随滚动或翻页变化的量。这个指示条连续三次栽在「会变的量」上 ——
 * 「已加载条数」（翻页时变大 ⇒ 位置后退）、「可见条目数」（滚动中在 4~6 浮动 ⇒ 位置抖动）。
 * 只要签名里没有这些量，它们就再也进不来。
 *
 * public 是为了让守门测试能从 app/src/test 直接调（CI 只跑 app 的单测、不跑本模块的）。
 */
fun scrollbarThumbFraction(firstVisibleIndex: Int, totalCount: Int): Float =
    if (totalCount <= 0) 0f else (firstVisibleIndex.toFloat() / totalCount).coerceIn(0f, 1f)

/**
 * 滚动位置指示条：按 LazyList 的 layoutInfo 在右缘画一个 thumb。
 *
 * 只有两条不变式，其它量一律不参与计算：
 *
 * 1. **长度是常量**。分页列表的「内容总量」是滚出来的，任何以它为分母的长度公式都会随翻页
 *    变化：按「已加载 ÷ DB 总数」算，分子一路涨 ⇒ 条子**越滚越长**。
 * 2. **位置只由 [scrollbarThumbFraction] 的两个入参决定**（见那里的说明）。
 *
 * 代价是移动幅度小：只加载了全文一小段时，thumb 本来就该待在靠近顶部的位置。
 * [totalCount] 为 null（推荐流 / 单源页）时退回已加载量，那种场景翻页时会跳，
 * 但没有更稳的基准 —— 与其猜一个假总量，不如承认它在少数场景里不完美。
 *
 * 一屏放得下时不画；纯指示不做拖拽定位（首页列表不需要双向交互）。
 */
internal fun Modifier.articleScrollbar(
    state: LazyListState,
    color: Color,
    totalCount: Int? = null,
): Modifier =
    drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val items = info.visibleItemsInfo
        val loaded = info.totalItemsCount
        val total = totalCount ?: loaded
        if (items.isEmpty() || loaded <= 0 || total <= 0 || items.size >= loaded) return@drawWithContent
        val viewport = size.height
        val thumbWidth = 4.dp.toPx()
        val thumbHeight = (viewport * THUMB_HEIGHT_RATIO)
            .coerceAtLeast(THUMB_MIN_HEIGHT.toPx())
            .coerceAtMost(viewport)
        val thumbY = scrollbarThumbFraction(items.first().index, total) * (viewport - thumbHeight)
        drawRoundRect(
            color = color.copy(alpha = 0.55f),
            topLeft = Offset(size.width - thumbWidth, thumbY),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbWidth / 2f),
        )
    }
