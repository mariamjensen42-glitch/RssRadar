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
 * 滚动位置指示条：按 LazyList 的 layoutInfo 画一个 thumb。
 * - draw 阶段直接读 layoutInfo（snapshot state），滚动时自动重绘，不引入重组；
 * - 分页适配：[totalCount] 非空时以 DB 总数为分母——翻页追加不改它，thumb 位置
 *   与长度都稳定（thumb 长度 = 已加载占比）；null 时退回按已加载量估算，会随
 *   翻页轻微收缩。粘性日期头也占槽位，条目数与文章数有少量出入，指示条容忍。
 * - 一屏放得下时不画；纯指示不做拖拽定位（首页列表不需要双向交互）。
 */
internal fun Modifier.articleScrollbar(
    state: LazyListState,
    color: Color,
    totalCount: Int? = null,
): Modifier =
    drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val loaded = info.totalItemsCount
        val visible = info.visibleItemsInfo.size
        // 分母取 DB 总数与已加载量的较大者：删除等局部变更后 count 可能暂时小于 loaded
        val total = maxOf(loaded, totalCount ?: 0)
        if (total <= 0 || visible >= total) return@drawWithContent
        val viewport = size.height
        val thumbWidth = 4.dp.toPx()
        // thumb 长度 = 已加载占比：翻页时分母不变，长度不跳
        val thumbHeight = (viewport * loaded / total).coerceAtLeast(32.dp.toPx())
        val firstIndex = info.visibleItemsInfo.first().index
        val scrollFraction = firstIndex / (total - visible).toFloat()
        val thumbY = scrollFraction * (viewport - thumbHeight)
        drawRoundRect(
            color = color.copy(alpha = 0.55f),
            topLeft = Offset(size.width - thumbWidth, thumbY),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbWidth / 2f),
        )
    }
