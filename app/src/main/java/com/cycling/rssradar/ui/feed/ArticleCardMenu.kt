package com.cycling.rssradar.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.ui.components.ArticleContextMenu
import com.cycling.rssradar.ui.components.ArticleMenuActions
import com.cycling.rssradar.ui.components.articleMenuOffset

/**
 * 杂志/网格卡的通用长按菜单容器：负责按压缩点定位与菜单弹出，
 * 内容卡通过 [content] 拿到 onLongClick 挂进自己的 combinedClickable。
 */
@Composable

internal fun ArticleMenuBox(
    itemCount: Int,
    actions: ArticleMenuActions,
    content: @Composable (() -> Unit) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    // 菜单偏移：贴着长按手指出现（与 ArticleCard 同一套预判逻辑）
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var pressPos by remember { mutableStateOf(Offset.Zero) }
    var cardTopInWindowPx by remember { mutableStateOf(0f) }
    var cardHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val windowHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    Box {
        Box(
            modifier = Modifier
                .onGloballyPositioned {
                    cardTopInWindowPx = it.localToWindow(Offset.Zero).y
                    cardHeightPx = it.size.height
                }
                // 旁观手势：只记录按下坐标，不消费事件，长按仍由内容卡的 clickable 触发
                .pointerInput(Unit) {
                    awaitEachGesture {
                        pressPos = awaitFirstDown(requireUnconsumed = false).position
                    }
                },
        ) {
            content {
                menuOffset = articleMenuOffset(
                    pressPos = pressPos,
                    cardTopInWindowPx = cardTopInWindowPx,
                    cardHeightPx = cardHeightPx,
                    menuItemCount = itemCount,
                    windowHeightPx = windowHeightPx,
                    density = density,
                )
                menuExpanded = true
            }
        }
        ArticleContextMenu(
            expanded = menuExpanded,
            offset = menuOffset,
            actions = actions,
            onDismiss = { menuExpanded = false },
        )
    }
}
