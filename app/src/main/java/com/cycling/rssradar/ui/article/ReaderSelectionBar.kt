package com.cycling.rssradar.ui.article

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.R
import com.cycling.rssradar.core.domain.annotation.AnnotationPalette
import com.cycling.rssradar.core.ui.theme.radarColors
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.StickyNote
import com.composables.icons.lucide.X
import kotlin.math.roundToInt

/** 未捕捉到长按点时的回退锚点：x=0 且 y=Int.MAX_VALUE，工具条据此退回吸底居中。 */
internal val PRESET_BOTTOM_ANCHOR = IntOffset(0, Int.MAX_VALUE)

/**
 * 划词工具条相对长按点的落位（纯函数，JVM 可测）。
 *
 * [anchorX] / [anchorY]：长按点相对父容器的坐标（px）。
 * [toolbarSize]：工具条自身尺寸（px，由 onSizeChanged 量得）。
 * [belowSpace]：长按点下方到容器底边的剩余空间（px）。
 * [viewportWidth]：父容器宽度（px）。
 *
 * 下方放得下 → 工具条顶边贴长按点；放不下 → 翻到上方，底边贴长按点。
 * 水平方向以长按点为中心，并夹在容器内，避免贴边被裁。
 */
internal fun selectionBarOffset(
    anchorX: Float,
    anchorY: Float,
    toolbarSize: IntSize,
    belowSpace: Float,
    viewportWidth: Float,
): IntOffset {
    val belowFits = belowSpace >= toolbarSize.height
    val y = if (belowFits) anchorY else anchorY - toolbarSize.height
    val centeredX = anchorX - toolbarSize.width / 2f
    val maxX = (viewportWidth - toolbarSize.width).coerceAtLeast(0f)
    return IntOffset(
        x = centeredX.coerceIn(0f, maxX).roundToInt(),
        y = y.roundToInt(),
    )
}

/**
 * 划词工具条：长按选中正文后浮在长按点附近的一排动作（高亮 / 记笔记 / 复制 / 收起）。
 *
 * 2026-10-03 改为完全自定义：容器是普通 [Surface]（方形 8dp + surface3 底 + 阴影），
 * **不用 HorizontalFloatingToolbar**——它的 RowScope 按标准浮动工具条量高，内容再小也留大片空白。
 * 高度由内容决定，位置由 [selectionBarOffset] 贴着长按点，越界自动翻上。
 * 透明底在正文上不可读，所以底色必须留着（一度去掉，用户反馈不行）。
 *
 * Compose 的公开 SelectionState 只给 selectedTexts、不给坐标，所以坐标由阅读页在长按发生时
 * 自行捕捉（见 LongPressAnchor.captureLongPressRootAnchor），本组件只负责按坐标落位 + 自量自身尺寸。
 *
 * 坐标系前提：本组件的容器铺满浮层 Box（该 Box 是 Scaffold content、无 padding，原点与 root 一致），
 * 而锚点也是 root 系坐标，故 `offset` 可直接用。**若将来给浮层容器加 padding / 改为非全屏，
 * 必须同步换算锚点**，否则工具条会整体偏移。
 *
 * 可点区不缩：IconButton 官方固定 48dp 且无更小变体，色点保持 28dp，紧凑感只靠图标尺寸与间距。
 */
@Composable
internal fun ReaderSelectionBar(
    anchor: IntOffset,
    onHighlight: (colorIndex: Int, note: String?) -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var noteColor by remember { mutableStateOf<Int?>(null) }
    var noteText by remember { mutableStateOf("") }
    var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val viewportWidthPx = viewportSize.width.toFloat()
    val viewportHeightPx = viewportSize.height.toFloat()

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it },
    ) {
        Surface(
            modifier = Modifier
                .onSizeChanged { toolbarSize = it }
                .offset {
                    val anchorY = if (anchor.y == Int.MAX_VALUE) {
                        (viewportHeightPx - toolbarSize.height).toFloat()
                    } else {
                        anchor.y.toFloat()
                    }
                    val anchorX = if (anchor.x == 0 && anchor.y == Int.MAX_VALUE) {
                        (viewportWidthPx - toolbarSize.width) / 2f
                    } else {
                        anchor.x.toFloat()
                    }
                    selectionBarOffset(
                        anchorX = anchorX,
                        anchorY = anchorY,
                        toolbarSize = toolbarSize,
                        belowSpace = viewportHeightPx - anchorY,
                        viewportWidth = viewportWidthPx,
                    )
                },
            shape = RoundedCornerShape(8.dp),
            color = radarColors().surface3,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AnnotationPalette.swatches.forEachIndexed { index, argb ->
                    // 描边用主题前景色（textPrimary）而非固定白：深色主题下它是白 → 提亮边界，
                    // 浅色主题下它是黑 → 压暗边界。固定白在浅色主题里等于没有（黄点对比度 1.14:1）。
                    // 深色 surface3 上紫点(BA68C8) 只有 2.89:1，低于 3:1 会发糊，故需要这圈描边。
                    Surface(
                        color = Color(argb),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, radarColors().textPrimary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onHighlight(index, null) },
                    ) {}
                }
                IconButton(onClick = {
                    noteText = ""
                    noteColor = AnnotationPalette.defaultIndex
                }) {
                    Icon(
                        Lucide.StickyNote,
                        contentDescription = stringResource(R.string.ann_note_action),
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onCopy) {
                    Icon(
                        Lucide.Copy,
                        contentDescription = stringResource(R.string.ann_copy_action),
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Lucide.X,
                        contentDescription = stringResource(R.string.ann_dismiss_action),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    val color = noteColor
    if (color != null) {
        AlertDialog(
            onDismissRequest = { noteColor = null },
            title = { Text(stringResource(R.string.ann_note_title)) },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text(stringResource(R.string.ann_note_hint)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onHighlight(color, noteText)
                    noteColor = null
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { noteColor = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
