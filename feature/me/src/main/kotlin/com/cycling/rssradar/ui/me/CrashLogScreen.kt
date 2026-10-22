package com.cycling.rssradar.ui.me

import com.cycling.rssradar.core.ui.R as UiR
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.cycling.rssradar.core.data.maintenance.CrashLog
import com.cycling.rssradar.core.data.maintenance.CrashRecord
import com.cycling.rssradar.core.ui.theme.Danger
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Share
import com.composables.icons.lucide.Trash
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import com.cycling.rssradar.core.ui.text.formatLogTimestamp

/** 单条崩溃的全文（dialog 内容）。 */
data class CrashDetail(val name: String, val head: String, val text: String)

/**
 * 崩溃日志（issue #61）：最近 5 次崩溃的清单，点开看全文、可导出分享。
 *
 * 这是「用户手上出问题」时唯一的证据来源——R8 开启后（#62）它会是判断
 * release 断裂的第一现场。
 */
@Composable
fun CrashLogDestination(
    onBack: () -> Unit,
    viewModel: CrashLogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CrashLogScreen(state = state, onBack = onBack, onIntent = viewModel::onIntent)
}

@Composable
fun CrashLogScreen(
    state: CrashLogUiState,
    onBack: () -> Unit,
    onIntent: (CrashLogIntent, Context) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val records = state.records
    val detail = state.detail
    var confirmClear by remember { mutableStateOf(false) }

    // 进页面读一次磁盘；崩溃日志只在打开时变，不做轮询。
    LaunchedEffect(Unit) { onIntent(CrashLogIntent.Refresh, context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, contentDescription = stringResource(UiR.string.back), tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                text = stringResource(R.string.crash_title),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (records.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Lucide.Trash, contentDescription = stringResource(R.string.crash_clear), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (records.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.crash_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.crash_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                records.forEach { record ->
                    CrashRow(record) { onIntent(CrashLogIntent.OpenDetail(record), context) }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    detail?.let { crash ->
        AlertDialog(
            onDismissRequest = { onIntent(CrashLogIntent.CloseDetail, context) },
            title = {
                Text(
                    text = crash.head,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(
                            text = crash.text.ifBlank { stringResource(R.string.crash_lost) },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { context.shareCrashLog(crash.text, crash.head) }) {
                    Text(stringResource(R.string.export), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(CrashLogIntent.CloseDetail, context) }) { Text(stringResource(UiR.string.close), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.crash_confirm_title), color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(stringResource(R.string.crash_confirm_msg, records.size), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onIntent(CrashLogIntent.Clear, context)
                    },
                ) { Text(stringResource(R.string.clear), color = Danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(UiR.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        )
    }
}

@Composable
private fun CrashRow(record: CrashRecord, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Lucide.CircleAlert,
                contentDescription = null,
                tint = Danger,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.head,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = formatLogTimestamp(record.time, withSeconds = true),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = stringResource(R.string.tap_expand),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Icon(
                Lucide.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 导出：纯文本 ACTION_SEND，不引 FileProvider，用户自己决定发到哪儿。 */
private fun Context.shareCrashLog(text: String, head: String) {
    if (text.isBlank()) {
        Toast.makeText(this, getString(R.string.crash_export_empty), Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_file_title))
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(Intent.createChooser(intent, getString(R.string.crash_export_title))) }
        .onFailure { Toast.makeText(this, getString(R.string.crash_export_failed), Toast.LENGTH_SHORT).show() }
}

