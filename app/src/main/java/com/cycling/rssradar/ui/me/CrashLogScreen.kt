package com.cycling.rssradar.ui.me

import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.runtime.collectAsState
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
import com.cycling.rssradar.core.data.CrashLog
import com.cycling.rssradar.core.data.CrashRecord
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.BgRoot
import com.cycling.rssradar.ui.theme.Danger
import com.cycling.rssradar.ui.theme.Surface1
import com.cycling.rssradar.ui.theme.TextPrimary
import com.cycling.rssradar.ui.theme.TextSecondary
import com.cycling.rssradar.ui.theme.TextTertiary
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

/** 单条崩溃的全文（dialog 内容）。 */
data class CrashDetail(val name: String, val head: String, val text: String)

@HiltViewModel
class CrashLogViewModel @Inject constructor() : ViewModel() {

    private val _records = MutableStateFlow<List<CrashRecord>>(emptyList())
    val records: StateFlow<List<CrashRecord>> = _records

    private val _detail = MutableStateFlow<CrashDetail?>(null)
    val detail: StateFlow<CrashDetail?> = _detail

    /** 落盘/读取都在 IO：崩溃日志可能有几十 KB，别在主线程啃文件。 */
    fun refresh(context: Context) {
        viewModelScope.launch(Dispatchers.IO) { _records.value = CrashLog.list(context) }
    }

    fun open(context: Context, record: CrashRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            _detail.value = CrashDetail(record.name, record.head, CrashLog.read(context, record.name))
        }
    }

    fun close() {
        _detail.value = null
    }

    fun clear(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            CrashLog.clear(context)
            _records.value = emptyList()
            _detail.value = null
        }
    }
}

/**
 * 崩溃日志（issue #61）：最近 5 次崩溃的清单，点开看全文、可导出分享。
 *
 * 这是「用户手上出问题」时唯一的证据来源——R8 开启后（#62）它会是判断
 * release 断裂的第一现场。
 */
@Composable
fun CrashLogScreen(
    viewModel: CrashLogViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val records by viewModel.records.collectAsState()
    val detail by viewModel.detail.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    // 进页面读一次磁盘；崩溃日志只在打开时变，不做轮询。
    LaunchedEffect(Unit) { viewModel.refresh(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgRoot)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, contentDescription = "返回", tint = TextPrimary)
            }
            Text(
                text = "崩溃日志",
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (records.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Lucide.Trash, contentDescription = "清空日志", tint = TextSecondary)
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
                Text("暂无崩溃记录", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "应用崩溃时会自动记录异常与设备信息，最多保留 5 份",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                records.forEach { record ->
                    CrashRow(record) { viewModel.open(context, record) }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    detail?.let { crash ->
        AlertDialog(
            onDismissRequest = viewModel::close,
            title = {
                Text(
                    text = crash.head,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(
                            text = crash.text.ifBlank { "（日志已丢失或读取失败）" },
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { context.shareCrashLog(crash.text, crash.head) }) {
                    Text("导出", color = Accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::close) { Text("关闭", color = TextSecondary) }
            },
            containerColor = Surface1,
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空崩溃日志？", color = TextPrimary) },
            text = { Text("已记录的 ${records.size} 份崩溃日志会被删除，无法恢复。", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        viewModel.clear(context)
                    },
                ) { Text("清空", color = Danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消", color = TextSecondary) }
            },
            containerColor = Surface1,
        )
    }
}

@Composable
private fun CrashRow(record: CrashRecord, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Surface1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
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
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = formatTime(record.time),
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = "点击查看全文",
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Icon(
                Lucide.Share,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 导出：纯文本 ACTION_SEND，不引 FileProvider，用户自己决定发到哪儿。 */
private fun Context.shareCrashLog(text: String, head: String) {
    if (text.isBlank()) {
        Toast.makeText(this, "日志为空，无法导出", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "RssRadar 崩溃日志")
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(Intent.createChooser(intent, "导出崩溃日志")) }
        .onFailure { Toast.makeText(this, "无法导出", Toast.LENGTH_SHORT).show() }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
