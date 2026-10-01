package com.cycling.rssradar.ui.article

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.R
import com.cycling.rssradar.core.domain.annotation.AnnotationPalette
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.StickyNote
import com.composables.icons.lucide.X

/**
 * 划词工具条：选中正文文字后浮在底部的一排动作（高亮 / 记笔记 / 复制 / 收起）。
 *
 * 用 Material 3 Expressive 的 [HorizontalFloatingToolbar] 而不是自绘 Surface：
 * 它自带浮动容器的形状、阴影与进出场动效，且避开屏幕边缘的安全距离。
 *
 * 颜色即动作——点某个色点就是"用这个颜色标下来"，不再多一次选择确认。
 * 笔记走对话框：要输入文本，塞进工具条会把工具条撑成一整块面板。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReaderSelectionBar(
    onHighlight: (colorIndex: Int, note: String?) -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var noteColor by remember { mutableStateOf<Int?>(null) }
    var noteText by remember { mutableStateOf("") }

    HorizontalFloatingToolbar(
        modifier = modifier,
        expanded = true,
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AnnotationPalette.swatches.forEachIndexed { index, argb ->
                    Surface(
                        color = Color(argb),
                        shape = CircleShape,
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
                    Icon(Lucide.StickyNote, contentDescription = stringResource(R.string.ann_note_action))
                }
                IconButton(onClick = onCopy) {
                    Icon(Lucide.Copy, contentDescription = stringResource(R.string.ann_copy_action))
                }
                IconButton(onClick = onDismiss) {
                    Icon(Lucide.X, contentDescription = stringResource(R.string.ann_dismiss_action))
                }
            }
        },
    )

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
