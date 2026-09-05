package com.cycling.rssradar.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.cycling.rssradar.core.data.ai.AiCategory
import com.cycling.rssradar.core.data.ai.AiFeature
import com.cycling.rssradar.core.data.ai.AiQueueSnapshot
import com.cycling.rssradar.core.data.ai.AiTrigger
import com.cycling.rssradar.core.data.store.AiBudgetState
import com.cycling.rssradar.core.data.store.AiFeatureSettings
import com.cycling.rssradar.core.ui.components.AppSnackbarHost
import com.cycling.rssradar.core.ui.theme.radarColors


/**
 * AI 智能功能总览：35 项功能的独立开关、用量看板、任务队列与预算设置。
 *
 * 三条刻意的 UI 决定：
 * 1. **每项默认折叠，点标题展开触发方式 / 交互入口 / 结果展示**——
 *    35 项全展开是一堵墙，但"这功能到底什么时候会跑"必须在同一屏里能查到，
 *    否则用户面对一堆开关无从判断该开哪个。
 * 2. **消耗额度的项打「调用模型」角标**，本地计算的打「本地」——
 *    这两个的成本差了几个数量级，用户有权一眼分辨。
 * 3. **分组标题带 n/m 与一键全开全关**：先整组关掉再逐个试，是这类功能最省心的上手方式。
 */
@Composable
fun AiFeaturesScreen(
    viewModel: AiFeaturesViewModel = hiltViewModel(),
    onBack: () -> Unit,
    /**
     * 打开 AI 产物中心。参数是预选功能的 dbValue（null = 全部）。
     *
     * 入口刻意放在这一页而不是只放在设置页：用户开启功能后第一反应是回来找结果，
     * 而一部分功能没有专属展示位——产物中心（按功能筛选）是它们的出口，
     * 藏深了等于又一次"跑成功了但看不到结果"。
     */
    onOpenArtifacts: (Int?) -> Unit = { _ -> },
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.onIntent(AiFeaturesIntent.ConsumeMessage)
    }

    Scaffold(
        containerColor = radarColors().bgRoot,
        snackbarHost = { AppSnackbarHost(snackbar) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Lucide.ArrowLeft,
                        contentDescription = "返回",
                        tint = radarColors().textPrimary,
                    )
                }
                Text(
                    text = "AI 智能功能",
                    style = MaterialTheme.typography.titleMedium,
                    color = radarColors().textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onOpenArtifacts(null) }) {
                    Text(
                        text = "查看结果",
                        style = MaterialTheme.typography.bodySmall,
                        color = radarColors().accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item { UsageCard(state.budget) }
            item { BudgetSection(state.budget, viewModel) }
            item { QueueSection(state.queue, state.running, viewModel) }

            AiCategory.entries.forEach { category ->
                item {
                    CategoryHeader(
                        category = category,
                        settings = state.settings,
                        onSetAll = { enabled ->
                            viewModel.onIntent(AiFeaturesIntent.SetCategory(category, enabled))
                        },
                    )
                }
                items(AiFeature.ofCategory(category), key = { it.name }) { feature ->
                    FeatureRow(
                        feature = feature,
                        enabled = state.settings.isEnabled(feature),
                        running = state.running,
                        onToggle = { viewModel.onIntent(AiFeaturesIntent.Toggle(feature)) },
                        onRun = { viewModel.onIntent(AiFeaturesIntent.RunFeature(feature)) },
                        onOpenResults = { onOpenArtifacts(feature.dbValue) },
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(onClick = { viewModel.onIntent(AiFeaturesIntent.ResetDefaults) }) {
                        Text("恢复默认", color = radarColors().textSecondary)
                    }
                    TextButton(onClick = { viewModel.onIntent(AiFeaturesIntent.DisableAllPaid) }) {
                        Text("全部关闭（省钱）", color = radarColors().textSecondary)
                    }
                }
            }
        }
    }
}

// ── 用量看板 ────────────────────────────────────────────────────────────────

/**
 * 用量：只显示真实统计到的次数与字数，**不换算金额**。
 * DeepSeek 的单价会调整，硬编码一个系数就是给用户一个看起来精确实则过期的数字。
 */
@Composable
private fun UsageCard(budget: AiBudgetState) {
    val colors = radarColors()
    Surface(shape = RoundedCornerShape(14.dp), color = colors.surface1) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Lucide.Sparkles,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "用量",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))

            val limitText = if (budget.dailyLimit <= 0) "不限" else budget.dailyLimit.toString()
            UsageRow("今日调用", "${budget.usedToday} / $limitText")
            if (budget.dailyLimit > 0) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(colors.surface3, RoundedCornerShape(3.dp)),
                ) {
                    val ratio = (budget.usedToday.toFloat() / budget.dailyLimit).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth(ratio)
                            .height(6.dp)
                            .background(colors.accent, RoundedCornerShape(3.dp)),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            UsageRow("今日输入 / 输出", "${formatCount(budget.inputCharsToday)} / ${formatCount(budget.outputCharsToday)} 字")
            UsageRow("累计调用", "${formatCount(budget.totalCalls)} 次")
            UsageRow("累计失败", "${formatCount(budget.totalFailed)} 次")
        }
    }
}

@Composable
private fun UsageRow(label: String, value: String) {
    val colors = radarColors()
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
    }
}

private fun formatCount(value: Long): String = when {
    value >= 100_000_000 -> String.format("%.1f亿", value / 100_000_000.0)
    value >= 10_000 -> String.format("%.1f万", value / 10_000.0)
    else -> value.toString()
}

// ── 预算设置 ────────────────────────────────────────────────────────────────

@Composable
private fun BudgetSection(
    budget: AiBudgetState,
    viewModel: AiFeaturesViewModel,
) {
    val colors = radarColors()
    Surface(shape = RoundedCornerShape(14.dp), color = colors.surface1) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "限流与预算",
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "上限到顶后当天不再发起请求，任务留在队列里等第二天。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(12.dp))

            ChipChoiceRow(
                label = "每日上限",
                options = listOf(0, 50, 100, 200, 500),
                selected = budget.dailyLimit,
                labelOf = { if (it == 0) "不限" else it.toString() },
                onSelect = { viewModel.onIntent(AiFeaturesIntent.SetDailyLimit(it)) },
            )
            Spacer(Modifier.height(10.dp))
            ChipChoiceRow(
                label = "并发数",
                options = listOf(1, 2, 3, 4),
                selected = budget.concurrentLimit,
                labelOf = { it.toString() },
                onSelect = { viewModel.onIntent(AiFeaturesIntent.SetConcurrent(it)) },
            )
            Spacer(Modifier.height(10.dp))
            ChipChoiceRow(
                label = "请求间隔",
                options = listOf(0L, 500L, 1_200L, 3_000L),
                selected = budget.minIntervalMs,
                labelOf = { if (it == 0L) "不限" else "${it}ms" },
                onSelect = { viewModel.onIntent(AiFeaturesIntent.SetMinInterval(it)) },
            )
        }
    }
}

@Composable
private fun <T> ChipChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val colors = radarColors()
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val isSelected = option == selected
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) colors.accent else colors.surface2,
                    modifier = Modifier.clickable { onSelect(option) },
                ) {
                    Text(
                        text = labelOf(option),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) colors.onAccent else colors.textSecondary,
                    )
                }
            }
        }
    }
}

// ── 任务队列 ────────────────────────────────────────────────────────────────

@Composable
private fun QueueSection(
    queue: AiQueueSnapshot,
    running: Boolean,
    viewModel: AiFeaturesViewModel,
) {
    val colors = radarColors()
    Surface(shape = RoundedCornerShape(14.dp), color = colors.surface1) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "任务队列",
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                QueueStat("待执行", queue.pending, colors.textPrimary)
                QueueStat("进行中", queue.running, colors.accent)
                QueueStat("已完成", queue.done, colors.textTertiary)
                QueueStat("失败", queue.failed, colors.textTertiary)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !running,
                    onClick = { viewModel.onIntent(AiFeaturesIntent.RunNow) },
                ) {
                    Text(if (running) "执行中…" else "立即执行", color = colors.accent)
                }
                TextButton(onClick = { viewModel.onIntent(AiFeaturesIntent.RetryFailed) }) {
                    Text("重试失败", color = colors.textSecondary)
                }
                TextButton(onClick = { viewModel.onIntent(AiFeaturesIntent.ClearPending) }) {
                    Text("清空待执行", color = colors.textSecondary)
                }
            }
        }
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

@Composable
private fun CategoryHeader(
    category: AiCategory,
    settings: AiFeatureSettings,
    onSetAll: (Boolean) -> Unit,
) {
    val colors = radarColors()
    val all = AiFeature.ofCategory(category)
    val enabledCount = settings.countIn(category)
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${category.label}  $enabledCount/${all.size}",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
            TextButton(onClick = { onSetAll(!settings.allIn(category)) }) {
                Text(
                    text = if (settings.allIn(category)) "全部关闭" else "全部开启",
                    color = colors.accent,
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    feature: AiFeature,
    enabled: Boolean,
    running: Boolean,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
    onOpenResults: () -> Unit,
) {
    val colors = radarColors()
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surface1,
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = feature.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = feature.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onAccent,
                        checkedTrackColor = colors.accent,
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TagChip(text = feature.trigger.label, tint = colors.accent)
                TagChip(
                    text = if (feature.needsLlm) "调用模型" else "本地",
                    tint = if (feature.needsLlm) colors.textTertiary else colors.textSecondary,
                )
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.surface2),
                )
                Spacer(Modifier.height(10.dp))
                DetailLine("触发方式", feature.trigger.description)
                DetailLine("交互入口", feature.entry)
                DetailLine("结果展示", feature.presentation)

                // 通用操作行：会落产物的功能给「查看结果」（产物中心预选本功能）；
                // 批处理功能再给「立即运行」——不等每日任务，当场把结果跑出来。
                // REALTIME（问答、划词解释）不落库，产物中心没有它的东西，按钮不出现。
                val hasResults = feature.needsLlm && feature.trigger != AiTrigger.REALTIME
                val canRun = feature.trigger == AiTrigger.BATCH
                if (hasResults || canRun) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canRun) {
                            TextButton(
                                enabled = !running,
                                onClick = onRun,
                            ) {
                                Text(
                                    if (running) "执行中…" else "立即运行",
                                    color = if (running) colors.textTertiary else colors.accent,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        if (hasResults) {
                            TextButton(onClick = onOpenResults) {
                                Text("查看结果", color = colors.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagChip(text: String, tint: Color) {
    val colors = radarColors()
    Surface(shape = RoundedCornerShape(6.dp), color = colors.surface2) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    val colors = radarColors()
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}
