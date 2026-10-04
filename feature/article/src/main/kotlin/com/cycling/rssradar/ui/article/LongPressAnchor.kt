package com.cycling.rssradar.ui.article

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * 长按点相对**根容器**左上角的坐标，回调给 [ReaderSelectionBar] 落位。
 *
 * 为什么需要它：Compose 的公开 `SelectionState` 只给 `selectedTexts`，不暴露选区坐标
 * （内部的 TextLayoutCoordinates 不是公开契约），所以在长按发生的瞬间自己记一笔。
 *
 * 换算方式：节点在 root 的位置 + 长按点相对节点的位置。工具条的浮层容器铺满全屏、
 * 与本节点同在 root 坐标系内，故 root 系坐标可直接用于落位（工具条内部再按自身尺寸做居中与翻转）。
 *
 * 只观察不消费：不消费按下事件，划词选择仍由 SelectionContainer 正常处理。
 */
internal fun Modifier.captureLongPressRootAnchor(
    onAnchor: (IntOffset) -> Unit,
): Modifier = composed {
    var nodePos by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { nodePos = it.positionInRoot() }
        .pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { local ->
                    onAnchor(
                        IntOffset(
                            x = (nodePos.x + local.x).roundToInt(),
                            y = (nodePos.y + local.y).roundToInt(),
                        )
                    )
                },
            )
        }
}
