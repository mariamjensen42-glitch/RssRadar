package com.cycling.rssradar.ui.ai

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.core.data.ai.AiArtifactGroup
import com.cycling.rssradar.core.ui.text.formatCount

/**
 * 顶部导语 + 三个计数。
 *
 * 顺序刻意是「先说这是什么，再给数字」：改版前是三个数字在最上、说明文字被夹在
 * 数字与筛选条之间，用户第一眼只看到三个没有上下文的数（原先的标签是「产物 / 功能 /
 * 模型输出」，其中"功能"其实指"有产物的功能数"，靠猜）。
 * 现在标签自解释（产物条数 / 覆盖功能 / 输出字数），导语提到最前面。
 */
@Composable
internal fun OverviewRow(total: Int, featureCount: Int, outputChars: Long) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = stringResource(R.string.artifacts_page_desc),
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OverviewCell(label = stringResource(R.string.ai_artifacts_stat_items), value = total.toString(), modifier = Modifier.weight(1f))
        OverviewCell(label = stringResource(R.string.ai_artifacts_stat_features), value = featureCount.toString(), modifier = Modifier.weight(1f))
        OverviewCell(label = stringResource(R.string.ai_artifacts_stat_chars), value = formatCount(outputChars), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun OverviewCell(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceContainerLowest, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun FeatureFilterRow(
    groups: List<AiArtifactGroup>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 「全部」带上条数：否则它和右边的功能 chip 长得一样，
        // 用户不知道当前到底筛掉了多少。
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.ai_artifacts_filter_all, groups.sumOf { it.total })) },
        )
        groups.forEach { group ->
            FilterChip(
                selected = selected == group.feature.dbValue,
                onClick = { onSelect(group.feature.dbValue) },
                label = { Text("${stringResource(group.feature.labelRes())} ${group.total}") },
            )
        }
    }
}

// ── 列表 ────────────────────────────────────────────────────────────────────
