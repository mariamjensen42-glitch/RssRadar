package com.cycling.rssradar.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SlidersHorizontal
import com.cycling.rssradar.core.ui.theme.radarColors

/**
 * 顶栏筛选底部弹层：分组 + 内容类型（图片/视频/音频）。
 * 内容分区原本常驻首页一行 chip（issue #75 PRD 方案 C），低频操作不值得占一行，
 * 现收进本弹层；分组或内容类型非默认时顶栏 SlidersHorizontal 亮小红点。
 */
@OptIn(ExperimentalMaterial3Api::class)

@Composable

internal fun GroupFilterSheet(
    groups: List<String>,
    selected: String?,
    contentType: ContentTypeFilter,
    onSelectContentType: (ContentTypeFilter) -> Unit,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = radarColors().surface1) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.filter_title),
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            // 内容类型：一排轻量 chip，即时生效且不关弹层（与分组列表「选中即关」区分：
            // 这里是多选前的快速试切，关弹层交给用户下滑手势）
            Text(
                text = stringResource(R.string.content_type),
                color = radarColors().textSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val ctypeLabels = ContentTypeFilter.entries.associateWith { stringResource(it.labelRes()) }
                ContentTypeFilter.entries.forEach { type ->
                    FilterChip(
                        label = ctypeLabels.getValue(type),
                        selected = type == contentType,
                        onClick = { onSelectContentType(type) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.group_title),
                color = radarColors().textSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                item {
                    GroupOption(
                        label = stringResource(R.string.filter_all),
                        selected = selected == null,
                        onClick = { onSelect(null) },
                    )
                }
                items(groups) { group ->
                    GroupOption(
                        label = group,
                        selected = selected == group,
                        onClick = { onSelect(group) },
                    )
                }
            }
        }
    }
}

@Composable

private fun GroupOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) radarColors().accent else radarColors().textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Lucide.Check,
                contentDescription = stringResource(R.string.cd_selected),
                tint = radarColors().accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
