package com.cycling.rssradar.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.rssradar.core.data.rsshub.RssHubInstanceStore
import com.cycling.rssradar.core.model.rsshub.CatalogSource
import com.cycling.rssradar.core.ui.R as UiR
import com.cycling.rssradar.core.ui.components.SectionHeader
import com.cycling.rssradar.core.ui.components.SettingsSubPage
import com.cycling.rssradar.core.ui.text.resolve
import com.cycling.rssradar.core.ui.theme.RssRadarTheme
import com.cycling.rssradar.core.ui.theme.radarOutlinedTextFieldColors

@Composable
fun SettingsRssHubDestination(
    onBack: () -> Unit = {},
    viewModel: RssHubSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsRssHubScreen(
        state = state,
        onBack = onBack,
        onProbeNow = viewModel::probeNow,
        onCustomInputChange = viewModel::onCustomInputChange,
        onSaveCustomHost = viewModel::saveCustomHost,
        onRefreshCatalog = viewModel::refreshCatalog,
    )
}

@Composable
fun SettingsRssHubScreen(
    state: RssHubSettingsUiState,
    onBack: () -> Unit = {},
    onProbeNow: () -> Unit = {},
    onCustomInputChange: (String) -> Unit = {},
    onSaveCustomHost: () -> Unit = {},
    onRefreshCatalog: () -> Unit = {},
) {
    SettingsSubPage(title = "RSSHub", onBack = onBack) {
        SectionHeader(
            stringResource(R.string.rsshub_instance),
            description = stringResource(R.string.instance_desc),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.current_instance),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = state.activeHost,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onProbeNow,
                    enabled = !state.probing,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (state.probing) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.probing), style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text(stringResource(R.string.auto_probe), style = MaterialTheme.typography.labelLarge)
                    }
                }
                state.probeMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = message.resolve(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.custom_instance),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.customInput,
            onValueChange = onCustomInputChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://your-rsshub.example.com", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = radarOutlinedTextFieldColors(),
        )
        Text(
            text = stringResource(R.string.custom_instance_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        // 主操作用实心按钮（UI 审计 G3）：文本链接样式地位不符、点击面积小
        Button(
            onClick = onSaveCustomHost,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(stringResource(UiR.string.save), style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.builtin_mirrors),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(6.dp))
        RssHubInstanceStore.BUILTIN_INSTANCES.forEachIndexed { index, host ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onCustomInputChange(host) },
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}. $host",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (host == state.activeHost) {
                        Text(stringResource(R.string.current_tag), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 路由目录（issue #59）
        SectionHeader(
            stringResource(R.string.route_catalog),
            description = stringResource(R.string.catalog_desc),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.catalog_count_label),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (state.catalogRouteCount > 0) stringResource(R.string.catalog_routes_count, state.catalogRouteCount) else stringResource(R.string.loading_ellipsis),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.data_time),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatCatalogTimestamp(state.catalogGeneratedAt) +
                            if (state.catalogSource == CatalogSource.UPDATED) stringResource(R.string.updated_suffix) else stringResource(R.string.builtin_suffix),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onRefreshCatalog,
                    enabled = !state.catalogRefreshing,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (state.catalogRefreshing) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.updating), style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text(stringResource(R.string.update_catalog), style = MaterialTheme.typography.labelLarge)
                    }
                }
                state.catalogMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = message.resolve(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Preview(showBackground = true, name = "RSSHub 实例 · 默认")
@Composable
private fun SettingsRssHubScreenPreview() {
    RssRadarTheme(darkTheme = false) {
        SettingsRssHubScreen(state = RssHubSettingsUiState(activeHost = "rsshub.app"))
    }
}

@Preview(showBackground = true, name = "RSSHub 实例 · 探测中")
@Composable
private fun SettingsRssHubScreenProbingPreview() {
    RssRadarTheme(darkTheme = true) {
        SettingsRssHubScreen(
            state = RssHubSettingsUiState(
                activeHost = "rsshub.app",
                probing = true,
                catalogRouteCount = 8421,
                catalogRefreshing = true,
            ),
        )
    }
}
