package com.cycling.rssradar.ui.ai

import com.cycling.rssradar.core.ui.R as UiR
import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.cycling.rssradar.core.data.ai.AiPayloadLine
import com.cycling.rssradar.core.model.AiScope
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.text.formatCount
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)

@Composable

internal fun AiArtifactDetailSheet(
    detail: AiArtifactDetail,
    timeFormat: SimpleDateFormat,
    onOpenArticle: (Long) -> Unit,
    onOpenFeed: (Long) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = radarColors()
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    var showRaw by remember(detail) { mutableStateOf(false) }
    val item = detail.item

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.bgRoot,
    ) {
        Column(
            // 用确定高度（屏高 92%）而不是 wrap + max：面板高度不定时，
            // 下面内容区的 weight(1f) 拿不到确定约束，滚动区会被压成 0。
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = stringResource(item.feature.labelRes()),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subjectLabel(item),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = listOf(
                    item.model,
                    timeFormat.format(Date(item.createdAt)),
                    stringResource(R.string.artifacts_input_chars, formatCount(item.inputChars.toLong())),
                    stringResource(R.string.artifacts_output_chars, formatCount(item.outputChars.toLong())),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(12.dp))

            // 结构化内容：可能很长，必须可滚动，否则会被父容器截断。
            // weight(1f) 而非 fill=false——fill=false 时高度由内容决定，
            // 内容多高就要多高，verticalScroll 反而失去意义。
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (detail.lines.isEmpty()) {
                    Text(
                        text = stringResource(R.string.artifact_unparseable),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                } else {
                    detail.lines.forEach { line ->
                        PayloadLineRow(line = line)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = { showRaw = !showRaw }) {
                Text(
                    text = if (showRaw) stringResource(R.string.collapse_raw) else stringResource(R.string.view_raw),
                    color = colors.accent,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (showRaw) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surface2,
                    modifier = Modifier.heightIn(max = 220.dp),
                ) {
                    Box(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                        Text(
                            text = detail.raw,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
                TextButton(onClick = { clipboardScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("artifact", detail.raw))) } }) {
                    Icon(
                        imageVector = Lucide.Copy,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.copy_raw), color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 跳转按钮只在有落点时出现：点一个没接线的按钮，用户只会以为又坏了。
                if (item.scope == AiScope.ARTICLE) {
                    TextButton(onClick = { onOpenArticle(item.subjectId) }) {
                        Text(stringResource(R.string.open_article), color = colors.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (item.scope == AiScope.FEED) {
                    TextButton(onClick = { onOpenFeed(item.subjectId) }) {
                        Text(stringResource(R.string.open_feed), color = colors.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) {
                    Icon(
                        imageVector = Lucide.Trash2,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(UiR.string.delete), color = colors.textTertiary)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable

private fun PayloadLineRow(line: AiPayloadLine) {
    val colors = radarColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (line.depth * 12).dp, top = 3.dp, bottom = 3.dp),
    ) {
        // label 来自另一模块的 data class，smart cast 不可用，必须先落到本地变量
        val label = line.label
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.width(84.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = line.value,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}
