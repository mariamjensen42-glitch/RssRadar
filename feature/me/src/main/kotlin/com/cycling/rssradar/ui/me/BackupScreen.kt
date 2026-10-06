package com.cycling.rssradar.ui.me

import com.cycling.rssradar.core.ui.R as UiR
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ShieldCheck
import com.cycling.rssradar.core.data.backup.ConflictPolicy
import com.cycling.rssradar.core.data.backup.ImportStrategy
import com.cycling.rssradar.core.ui.components.ConfirmDialog
import com.cycling.rssradar.core.ui.components.NavigateRow
import com.cycling.rssradar.core.ui.components.OptionPickerSheet
import com.cycling.rssradar.core.ui.components.OptionRow
import com.cycling.rssradar.core.ui.components.SectionHeader
import com.cycling.rssradar.core.ui.components.SettingSwitchRow
import com.cycling.rssradar.core.ui.components.SettingsSubPage
import com.cycling.rssradar.core.ui.text.resolve
import com.cycling.rssradar.core.ui.theme.RssRadarTheme
import com.cycling.rssradar.core.ui.theme.radarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupDestination(
    onBack: () -> Unit = {},
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackupScreen(
        state = state,
        onBack = onBack,
        onSetIncludeContent = viewModel::setIncludeContent,
        onExport = viewModel::export,
        onImport = viewModel::import,
        onRebuildIndex = viewModel::rebuildIndex,
    )
}

/** 备份与恢复：全量导出 / 导入，外加检索索引重建。API Key 永不导出。 */
@Composable
fun BackupScreen(
    state: BackupViewModel.UiState,
    onBack: () -> Unit = {},
    onSetIncludeContent: (Boolean) -> Unit = {},
    onExport: (android.net.Uri) -> Unit = {},
    onImport: (android.net.Uri, ImportStrategy, ConflictPolicy) -> Unit = { _, _, _ -> },
    onRebuildIndex: () -> Unit = {},
) {
    var showStrategySheet by remember { mutableStateOf(false) }
    var showOverwriteConfirm by remember { mutableStateOf(false) }
    var pendingStrategy by remember { mutableStateOf(ImportStrategy.MERGE) }
    var conflict by remember { mutableStateOf(ConflictPolicy.KEEP_LOCAL) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let(onExport)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { onImport(it, pendingStrategy, conflict) }
    }

    SettingsSubPage(title = stringResource(R.string.backup_title), onBack = onBack) {
        SectionHeader(stringResource(R.string.backup_export), description = stringResource(R.string.backup_export_desc))
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                SettingSwitchRow(
                    label = stringResource(R.string.backup_include_content),
                    checked = state.includeContent,
                    onChange = onSetIncludeContent,
                    subtitle = stringResource(R.string.backup_include_content_desc),
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { exportLauncher.launch("rssradar-backup-${todayStamp()}.json") },
                    enabled = !state.running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.backup_export_action))
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        SectionHeader(stringResource(R.string.backup_import), description = stringResource(R.string.backup_import_desc))
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                OptionRow(
                    label = stringResource(R.string.backup_conflict),
                    value = conflictLabel(conflict),
                    onClick = {
                        conflict = if (conflict == ConflictPolicy.KEEP_LOCAL) {
                            ConflictPolicy.KEEP_BACKUP
                        } else {
                            ConflictPolicy.KEEP_LOCAL
                        }
                    },
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { showStrategySheet = true },
                    enabled = !state.running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.backup_import_action))
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        SectionHeader(stringResource(R.string.backup_maintain), description = stringResource(R.string.backup_rebuild_index_desc))
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                NavigateRow(
                    label = stringResource(R.string.backup_rebuild_index),
                    onClick = onRebuildIndex,
                )
            }
        }

        if (state.running || state.rebuilding) {
            Spacer(Modifier.height(20.dp))
            LinearWavyProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (state.total > 0) {
                    stringResource(R.string.backup_progress, state.done, state.total)
                } else {
                    stringResource(R.string.backup_working)
                },
                color = radarColors().textTertiary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        state.message?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = message.resolve(),
                color = radarColors().textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        state.report?.let { report ->
            Spacer(Modifier.height(12.dp))
            Column {
                ReportLine(stringResource(R.string.backup_report_feeds, report.feedsAdded, report.feedsSkipped))
                ReportLine(stringResource(R.string.backup_report_articles, report.articlesAdded, report.articlesSkipped))
                ReportLine(stringResource(R.string.backup_report_annotations, report.annotationsAdded))
                ReportLine(stringResource(R.string.backup_report_rules, report.rulesAdded))
                ReportLine(stringResource(R.string.backup_report_artifacts, report.artifactsAdded))
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Lucide.ShieldCheck,
                contentDescription = null,
                tint = radarColors().textTertiary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.backup_excludes_secrets),
                color = radarColors().textTertiary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (showStrategySheet) {
        val labels = mapOf(
            ImportStrategy.MERGE to stringResource(R.string.backup_strategy_merge),
            ImportStrategy.OVERWRITE to stringResource(R.string.backup_strategy_overwrite),
        )
        OptionPickerSheet(
            title = stringResource(R.string.backup_strategy),
            options = ImportStrategy.entries.toList(),
            selected = pendingStrategy,
            label = { labels.getValue(it) },
            onSelect = { strategy ->
                pendingStrategy = strategy
                showStrategySheet = false
                if (strategy == ImportStrategy.OVERWRITE) showOverwriteConfirm = true else importLauncher.launch(FILE_MIME)
            },
            onDismiss = { showStrategySheet = false },
        )
    }

    if (showOverwriteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.backup_strategy_overwrite),
            text = stringResource(R.string.backup_overwrite_confirm),
            confirmText = stringResource(UiR.string.action_confirm),
            onConfirm = {
                showOverwriteConfirm = false
                importLauncher.launch(FILE_MIME)
            },
            onDismiss = { showOverwriteConfirm = false },
            destructive = true,
        )
    }
}

@Composable
private fun ReportLine(text: String) {
    Text(
        text = text,
        color = radarColors().textSecondary,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 1.dp),
    )
}

@Composable
private fun conflictLabel(policy: ConflictPolicy): String = stringResource(
    when (policy) {
        ConflictPolicy.KEEP_LOCAL -> R.string.backup_conflict_keep_local
        ConflictPolicy.KEEP_BACKUP -> R.string.backup_conflict_keep_backup
    },
)

@Preview(showBackground = true, name = "备份与恢复 · 空闲")
@Composable
private fun BackupScreenPreview() {
    RssRadarTheme(darkTheme = false) {
        BackupScreen(state = BackupViewModel.UiState())
    }
}

@Preview(showBackground = true, name = "备份与恢复 · 导出中")
@Composable
private fun BackupScreenRunningPreview() {
    RssRadarTheme(darkTheme = true) {
        BackupScreen(
            state = BackupViewModel.UiState(running = true, done = 42, total = 130),
        )
    }
}

private val FILE_MIME = arrayOf("application/json", "text/plain", "*/*")

private fun todayStamp(): String =
    SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
