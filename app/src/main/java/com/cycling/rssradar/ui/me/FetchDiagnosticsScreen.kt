package com.cycling.rssradar.ui.me

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.core.data.OnDemandFetch
import com.cycling.rssradar.core.data.db.ContentFetchLogEntity
import com.cycling.rssradar.core.data.db.FetchHostStat
import com.cycling.rssradar.core.data.parser.ExtractionIssue
import com.cycling.rssradar.core.data.parser.FetchFailure
import com.cycling.rssradar.core.ui.theme.Danger
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.cycling.rssradar.core.ui.theme.radarColors

@HiltViewModel
class FetchDiagnosticsViewModel @Inject constructor(
    /** 抓取日志的唯一读取方，直连按需抓取模块，不再经过 FeedRepository 转发。 */
    private val onDemandFetch: OnDemandFetch,
) : ViewModel() {

    /** 有问题的记录：抓取失败 + 抓到但不完整。 */
    val problems: StateFlow<List<ContentFetchLogEntity>> =
        onDemandFetch.observeProblems()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 按站点聚合：总数 / 失败数 / 不完整数。 */
    val hostStats: StateFlow<List<FetchHostStat>> =
        onDemandFetch.observeHostStats()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clear() {
        viewModelScope.launch { onDemandFetch.clearLogs() }
    }
}

/**
 * 全文抓取诊断（ADR-0012 可观测性）。
 *
 * 只展示**有问题的**记录：抓取失败（限流/403/超时…）与「抓到但不完整」（正文过短 /
 * 无段落 / JS 渲染 / 付费墙）。每条都带站点、状态码、重试次数、页数与原因分类——
 * 以前这些信息只存在于一次静默的 null 里。
 */
@Composable
fun FetchDiagnosticsScreen(
    viewModel: FetchDiagnosticsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val problems by viewModel.problems.collectAsState()
    val hostStats by viewModel.hostStats.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(radarColors().bgRoot)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, contentDescription = "返回", tint = radarColors().textPrimary)
            }
            Text(
                text = "全文抓取诊断",
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (problems.isNotEmpty()) {
                IconButton(onClick = viewModel::clear) {
                    Icon(Lucide.Trash, contentDescription = "清空记录", tint = radarColors().textSecondary)
                }
            }
        }

        if (problems.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("暂无失败或不完整的抓取记录", color = radarColors().textSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "打开一篇摘要型文章并触发全文抓取后，这里会出现记录",
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {

        // ---- 按站点归因 ----
        SectionTitle("按站点")
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = radarColors().surface1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                hostStats.take(20).forEach { stat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stat.host.ifBlank { "（未知站点）" },
                            color = radarColors().textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        StatChip(label = "失败 ${stat.failures}", danger = stat.failures > 0)
                        Spacer(Modifier.size(6.dp))
                        StatChip(label = "不完整 ${stat.incomplete}", danger = false)
                    }
                }
            }
        }

        // ---- 明细清单 ----
        Spacer(Modifier.height(16.dp))
        SectionTitle("明细（${problems.size} 条）")
        problems.forEach { log ->
            ProblemRow(log)
            Spacer(Modifier.height(8.dp))
        }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = radarColors().textSecondary,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun StatChip(label: String, danger: Boolean) {
    Surface(shape = RoundedCornerShape(50), color = radarColors().surface2) {
        Text(
            text = label,
            color = if (danger) Danger else radarColors().textSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun ProblemRow(log: ContentFetchLogEntity) {
    val (title, detail) = describe(log)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = radarColors().surface1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = radarColors().textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = log.host,
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = log.link,
                    color = radarColors().textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(detail, color = radarColors().textTertiary, style = MaterialTheme.typography.labelSmall)
                    Text(formatTime(log.createdAt), color = radarColors().textTertiary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** 原因 → 中文文案；未知枚举值（老版本写入的）如实显示原始值，不编造。 */
private fun describe(log: ContentFetchLogEntity): Pair<String, String> {
    if (!log.ok) {
        val failure = runCatching { FetchFailure.valueOf(log.failure.orEmpty()) }.getOrNull()
        return (failure?.label ?: (log.failure ?: "失败")) to facts(log)
    }
    val issue = runCatching { ExtractionIssue.valueOf(log.issue.orEmpty()) }.getOrNull()
    val label = when (issue) {
        ExtractionIssue.TOO_SHORT -> "正文过短"
        ExtractionIssue.NO_PARAGRAPH -> "未找到正文段落"
        ExtractionIssue.DYNAMIC_RENDER -> "疑似 JS 动态渲染"
        ExtractionIssue.PAYWALL -> "疑似付费墙/登录墙"
        ExtractionIssue.METADATA_MISSING -> "缺标题或时间"
        else -> "正文不完整"
    }
    return label to facts(log)
}

private fun facts(log: ContentFetchLogEntity): String = buildString {
    append("重试 ${log.attempts} 次")
    log.statusCode?.let { append(" · HTTP $it") }
    if (log.pages > 1) append(" · ${log.pages} 页")
    append(" · ${log.contentChars} 字")
    append(" · ${log.durationMs} ms")
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
