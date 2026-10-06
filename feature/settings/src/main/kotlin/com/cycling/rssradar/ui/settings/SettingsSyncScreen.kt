package com.cycling.rssradar.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.cycling.rssradar.core.model.KeepArchived
import com.cycling.rssradar.core.model.SyncInterval
import com.cycling.rssradar.core.model.SyncState
import com.cycling.rssradar.core.ui.R as UiR
import com.cycling.rssradar.core.ui.components.OptionPickerSheet
import com.cycling.rssradar.core.ui.components.SectionHeader
import com.cycling.rssradar.core.ui.components.SettingSwitchRow
import com.cycling.rssradar.core.ui.components.SettingsSubPage
import com.cycling.rssradar.core.ui.theme.RssRadarTheme
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.text.resolve

@Composable
fun SettingsSyncDestination(
    onBack: () -> Unit = {},
    viewModel: RssHubSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsSyncScreen(
        state = state,
        onBack = onBack,
        onSetKeepArchived = viewModel::setKeepArchived,
        onUpdateSync = viewModel::updateSync,
        onSetNotifyEnabled = viewModel::setNotifyEnabled,
        onNotifyPermissionResult = viewModel::onNotifyPermissionResult,
    )
}

@Composable
fun SettingsSyncScreen(
    state: RssHubSettingsUiState,
    onBack: () -> Unit = {},
    onSetKeepArchived: (KeepArchived) -> Unit = {},
    onUpdateSync: ((SyncState) -> SyncState) -> Unit = {},
    onSetNotifyEnabled: (Boolean) -> Unit = {},
    onNotifyPermissionResult: (Boolean) -> Unit = {},
) {
    var showKeepSheet by remember { mutableStateOf(false) }
    var showIntervalSheet by remember { mutableStateOf(false) }
    // Android 13+ 的通知运行时权限：用户点开开关时才请求，不在进页面时打扰
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> onNotifyPermissionResult(granted) }

    SettingsSubPage(title = stringResource(R.string.settings_sync), onBack = onBack) {
        // 自动同步（issue #58）
        SectionHeader(
            stringResource(R.string.auto_sync),
            description = stringResource(R.string.auto_sync_desc),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showIntervalSheet = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.sync_interval),
                        color = radarColors().textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = state.sync.interval.label,
                        color = radarColors().accent,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                SettingSwitchRow(
                    label = stringResource(R.string.wifi_only),
                    checked = state.sync.onlyOnWifi,
                    onChange = { v -> onUpdateSync { it.copy(onlyOnWifi = v) } },
                )
                SettingSwitchRow(
                    label = stringResource(R.string.charging_only),
                    checked = state.sync.onlyWhenCharging,
                    onChange = { v -> onUpdateSync { it.copy(onlyWhenCharging = v) } },
                )
                SettingSwitchRow(
                    label = stringResource(R.string.sync_on_launch),
                    checked = state.sync.syncOnStart,
                    onChange = { v -> onUpdateSync { it.copy(syncOnStart = v) } },
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // 文章清理（issue #57）
        SectionHeader(
            stringResource(R.string.article_cleanup),
            description = stringResource(R.string.cleanup_desc),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showKeepSheet = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.keep_over),
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = state.keepArchived.label,
                    color = radarColors().accent,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Lucide.ChevronRight,
                    contentDescription = stringResource(UiR.string.select),
                    tint = radarColors().textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // 新文章通知（#31）
        SectionHeader(
            stringResource(R.string.new_article_notify),
            description = stringResource(R.string.notify_desc),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                SettingSwitchRow(
                    label = stringResource(R.string.enable_notify),
                    checked = state.notifyEnabled,
                    onChange = { enabled ->
                        if (enabled && !state.notifyPermissionGranted && needsNotificationPermission()) {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onSetNotifyEnabled(enabled)
                        }
                    },
                )
                state.notifyMessage?.let { message ->
                    Text(
                        text = message.resolve(),
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showKeepSheet) {
        val keepLabels = KeepArchived.entries.associateWith { stringResource(it.labelRes()) }
        OptionPickerSheet(
            title = stringResource(R.string.keep_over),
            options = KeepArchived.entries.toList(),
            selected = state.keepArchived,
            label = { keepLabels.getValue(it) },
            onSelect = onSetKeepArchived,
            onDismiss = { showKeepSheet = false },
        )
    }

    if (showIntervalSheet) {
        val intervalLabels = SyncInterval.entries.associateWith { stringResource(it.labelRes()) }
        OptionPickerSheet(
            title = stringResource(R.string.sync_interval),
            options = SyncInterval.entries.toList(),
            selected = state.sync.interval,
            label = { intervalLabels.getValue(it) },
            onSelect = { interval -> onUpdateSync { it.copy(interval = interval) } },
            onDismiss = { showIntervalSheet = false },
        )
    }
}

@Preview(showBackground = true, name = "同步与清理 · 浅色")
@Composable
private fun SettingsSyncScreenPreview() {
    RssRadarTheme(darkTheme = false) {
        SettingsSyncScreen(state = RssHubSettingsUiState())
    }
}

@Preview(showBackground = true, name = "同步与清理 · 通知权限被拒")
@Composable
private fun SettingsSyncScreenNotifyDeniedPreview() {
    RssRadarTheme(darkTheme = true) {
        SettingsSyncScreen(
            state = RssHubSettingsUiState(
                notifyEnabled = true,
                notifyPermissionGranted = false,
            ),
        )
    }
}
