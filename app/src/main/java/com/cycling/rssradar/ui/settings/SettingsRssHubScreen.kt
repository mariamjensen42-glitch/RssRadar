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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.cycling.rssradar.R
import com.cycling.rssradar.core.data.rsshub.RssHubInstanceStore
import com.cycling.rssradar.core.model.rsshub.CatalogSource
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.text.resolve
import com.cycling.rssradar.core.ui.components.SectionHeader
import com.cycling.rssradar.core.ui.components.SettingsSubPage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun SettingsRssHubScreen(
    viewModel: RssHubSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    SettingsSubPage(title = "RSSHub", onBack = onBack) {
        SectionHeader(
            stringResource(R.string.rsshub_instance),
            stringResource(R.string.instance_desc),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.current_instance),
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = state.activeHost,
                        color = radarColors().textPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = viewModel::probeNow,
                    enabled = !state.probing,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = radarColors().accent,
                        contentColor = radarColors().onAccent,
                    ),
                ) {
                    if (state.probing) {
                        CircularProgressIndicator(color = radarColors().onAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.probing), style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text(stringResource(R.string.auto_probe), style = MaterialTheme.typography.labelLarge)
                    }
                }
                state.probeMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = message.resolve(), color = radarColors().textTertiary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.custom_instance),
            color = radarColors().textSecondary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.customInput,
            onValueChange = viewModel::onCustomInputChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://your-rsshub.example.com", color = radarColors().textTertiary, style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = radarColors().surface2,
                unfocusedContainerColor = radarColors().surface2,
                focusedBorderColor = radarColors().accent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = radarColors().textPrimary,
                unfocusedTextColor = radarColors().textPrimary,
                cursorColor = radarColors().accent,
            ),
        )
        Text(
            text = stringResource(R.string.custom_instance_hint),
            color = radarColors().textTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
                Spacer(Modifier.height(10.dp))
                // 主操作用实心按钮（UI 审计 G3）：文本链接样式地位不符、点击面积小
                Button(
                    onClick = viewModel::saveCustomHost,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = radarColors().accent,
                        contentColor = radarColors().onAccent,
                    ),
                ) {
                    Text(stringResource(R.string.save), style = MaterialTheme.typography.labelLarge)
                }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.builtin_mirrors),
            color = radarColors().textTertiary,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(6.dp))
        RssHubInstanceStore.BUILTIN_INSTANCES.forEachIndexed { index, host ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = radarColors().surface1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable { viewModel.onCustomInputChange(host) },
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}. $host",
                        color = radarColors().textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (host == state.activeHost) {
                        Text(stringResource(R.string.current_tag), color = radarColors().accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 路由目录（issue #59）
        SectionHeader(
            stringResource(R.string.route_catalog),
            stringResource(R.string.catalog_desc),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.catalog_count_label),
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (state.catalogRouteCount > 0) stringResource(R.string.catalog_routes_count, state.catalogRouteCount) else stringResource(R.string.loading_ellipsis),
                        color = radarColors().textPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.data_time),
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatCatalogTimestamp(state.catalogGeneratedAt) +
                            if (state.catalogSource == CatalogSource.UPDATED) stringResource(R.string.updated_suffix) else stringResource(R.string.builtin_suffix),
                        color = radarColors().textPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = viewModel::refreshCatalog,
                    enabled = !state.catalogRefreshing,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = radarColors().accent,
                        contentColor = radarColors().onAccent,
                    ),
                ) {
                    if (state.catalogRefreshing) {
                        CircularProgressIndicator(color = radarColors().onAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.updating), style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text(stringResource(R.string.update_catalog), style = MaterialTheme.typography.labelLarge)
                    }
                }
                state.catalogMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = message.resolve(), color = radarColors().textTertiary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// —— 4. AI 与诊断：DeepSeek Key / 全文抓取诊断 / 崩溃日志 ——
