package com.cycling.rssradar.ui.ai

import com.cycling.rssradar.core.ui.R as UiR
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.core.data.ai.AiQueueSnapshot
import com.cycling.rssradar.core.ui.theme.radarColors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun QueueSection(
    queue: AiQueueSnapshot,
    running: Boolean,
    viewModel: AiFeaturesViewModel,
) {
    val colors = radarColors()
    // 清空待执行是批量丢弃（UI 审计 M3）：二次确认，不一键直发
    var confirmClearPending by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(14.dp), color = colors.surface1) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.queue_title),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                QueueStat(stringResource(R.string.status_pending), queue.pending, colors.textPrimary)
                QueueStat(stringResource(R.string.status_running), queue.running, colors.accent)
                QueueStat(stringResource(R.string.status_done), queue.done, colors.textTertiary)
                QueueStat(stringResource(R.string.status_failed), queue.failed, colors.textTertiary)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !running,
                    onClick = { viewModel.onIntent(AiFeaturesIntent.RunNow) },
                ) {
                    Text(if (running) stringResource(R.string.running_now) else stringResource(R.string.run_now), color = colors.accent)
                }
                TextButton(onClick = { viewModel.onIntent(AiFeaturesIntent.RetryFailed) }) {
                    Text(stringResource(R.string.retry_failed), color = colors.textSecondary)
                }
                TextButton(onClick = { confirmClearPending = true }) {
                    Text(stringResource(R.string.clear_pending), color = colors.textSecondary)
                }
            }
        }
    }
    if (confirmClearPending) {
        AlertDialog(
            onDismissRequest = { confirmClearPending = false },
            containerColor = colors.surface1,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text(stringResource(R.string.clear_pending), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            text = { Text(stringResource(R.string.clear_pending_warning, queue.pending)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearPending = false
                    viewModel.onIntent(AiFeaturesIntent.ClearPending)
                }) {
                    Text(stringResource(R.string.clear), color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearPending = false }) {
                    Text(stringResource(UiR.string.cancel), color = colors.textTertiary)
                }
            },
        )
    }
}

@Composable
private fun QueueStat(label: String, value: Int, color: Color) {
    val colors = radarColors()
    Column(Modifier.padding(end = 18.dp)) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
    }
}

// ── 功能开关 ────────────────────────────────────────────────────────────────
