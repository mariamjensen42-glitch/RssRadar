package com.cycling.rssradar.ui.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.R
import com.cycling.rssradar.core.data.ai.AiArtifactGroup
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.text.formatCount
import com.cycling.rssradar.i18n.labelRes

@Composable

internal fun OverviewRow(total: Int, featureCount: Int, outputChars: Long) {
    val colors = radarColors()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OverviewCell(label = stringResource(R.string.tab_artifacts), value = total.toString(), modifier = Modifier.weight(1f))
        OverviewCell(label = stringResource(R.string.tab_features), value = featureCount.toString(), modifier = Modifier.weight(1f))
        OverviewCell(label = stringResource(R.string.model_output), value = stringResource(R.string.chars_suffix, formatCount(outputChars)), modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.artifacts_page_desc),
        style = MaterialTheme.typography.bodySmall,
        color = colors.textTertiary,
    )
}

@Composable

private fun OverviewCell(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = radarColors()
    Surface(shape = RoundedCornerShape(12.dp), color = colors.surface1, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
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
    val colors = radarColors()
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            text = stringResource(R.string.filter_all),
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        groups.forEach { group ->
            FilterChip(
                text = "${stringResource(group.feature.labelRes())} ${group.total}",
                selected = selected == group.feature.dbValue,
                onClick = { onSelect(group.feature.dbValue) },
            )
        }
    }
}

@Composable

internal fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = radarColors()
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) colors.accent else colors.surface2,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) colors.onAccent else colors.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ── 列表 ────────────────────────────────────────────────────────────────────
