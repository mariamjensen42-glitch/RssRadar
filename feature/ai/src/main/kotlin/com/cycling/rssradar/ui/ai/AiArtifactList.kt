package com.cycling.rssradar.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.core.data.ai.AiArtifactItem
import com.cycling.rssradar.core.ui.theme.radarColors
import java.text.SimpleDateFormat
import java.util.Date

@Composable

internal fun ArtifactList(
    items: List<AiArtifactItem>,
    timeFormat: SimpleDateFormat,
    onOpen: (AiArtifactItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = radarColors()
    // 按功能分组，组内按时间倒序（数据库已按时间倒序返回，分组不破坏该顺序）。
    val groups = remember(items) {
        items.groupBy { it.feature }.toList().sortedByDescending { (_, list) -> list.first().createdAt }
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        groups.forEach { (feature, list) ->
            stickyHeader(key = "h-${feature.dbValue}") {
                Surface(color = colors.bgRoot) {
                    Text(
                        text = stringResource(R.string.artifacts_feature_count, stringResource(feature.labelRes()), list.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accent,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    )
                }
            }
            items(list, key = { "${it.feature.dbValue}:${it.subjectId}" }) { item ->
                ArtifactRow(item = item, timeFormat = timeFormat, onOpen = { onOpen(item) })
            }
        }
    }
}

@Composable

private fun ArtifactRow(
    item: AiArtifactItem,
    timeFormat: SimpleDateFormat,
    onOpen: () -> Unit,
) {
    val colors = radarColors()
    val preview = remember(item.payload) { previewOf(item) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surface1,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(item.feature.labelRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = timeFormat.format(Date(item.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = subjectLabel(item),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (preview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
