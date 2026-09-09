package com.cycling.rssradar.ui.me

import androidx.compose.ui.res.stringResource
import com.cycling.rssradar.R
import com.cycling.rssradar.i18n.descriptionRes
import com.cycling.rssradar.i18n.presentationRes
import com.cycling.rssradar.i18n.entryRes
import com.cycling.rssradar.i18n.summaryRes
import com.cycling.rssradar.i18n.labelRes
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.platform.LocalContext
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import com.cycling.rssradar.i18n.resolve
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
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message.resolve(context))
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
                        contentDescription = stringResource(R.string.back),
                        tint = radarColors().textPrimary,
                    )
                }
                Text(
                    text = stringResource(R.string.ai_features_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = radarColors().textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                // 产物中心入口：文字链接太弱（65+ 条产物的家），换填充 chip 提权重
                Surface(
                    shape = RoundedCornerShape(50),
                    color = radarColors().accent,
                    onClick = { onOpenArtifacts(null) },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Lucide.Sparkles,
                            contentDescription = null,
                            tint = radarColors().onAccent,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.view_results),
                            style = MaterialTheme.typography.bodySmall,
                            color = radarColors().onAccent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
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
                        Text(stringResource(R.string.restore_defaults), color = radarColors().textSecondary)
                    }
                    TextButton(onClick = { viewModel.onIntent(AiFeaturesIntent.DisableAllPaid) }) {
                        Text(stringResource(R.string.disable_all_paid), color = radarColors().textSecondary)
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
                    stringResource(R.string.usage_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))

            val limitText = if (budget.dailyLimit <= 0) stringResource(R.string.unlimited) else budget.dailyLimit.toString()
            UsageRow(stringResource(R.string.calls_today), "${budget.usedToday} / $limitText")
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
                // 空进度条状态不明（UI 审计 M4）：0 时直接说明
                if (budget.usedToday == 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.not_used_today), style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
                }
            }
            Spacer(Modifier.height(10.dp))
            UsageRow(stringResource(R.string.io_today), stringResource(R.string.io_today_chars, formatCount(budget.inputCharsToday), formatCount(budget.outputCharsToday)))
            UsageRow(stringResource(R.string.calls_total), stringResource(R.string.calls_suffix, formatCount(budget.totalCalls)))
            UsageRow(stringResource(R.string.failed_total), stringResource(R.string.calls_suffix, formatCount(budget.totalFailed)))
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

/** 英文语境的计数缩写（K/M），与中文 万/亿 口径一致。 */
private fun formatCountEn(value: Long): String = when {
    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
    value >= 10_000 -> String.format("%.1fK", value / 1_000.0)
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
                stringResource(R.string.budget_section),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.budget_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(12.dp))

            val unlimitedLabel = stringResource(R.string.unlimited)
            ChipChoiceRow(
                label = stringResource(R.string.daily_limit),
                options = listOf(0, 50, 100, 200, 500),
                selected = budget.dailyLimit,
                labelOf = { if (it == 0) unlimitedLabel else it.toString() },
                onSelect = { viewModel.onIntent(AiFeaturesIntent.SetDailyLimit(it)) },
            )
            Spacer(Modifier.height(10.dp))
            ChipChoiceRow(
                label = stringResource(R.string.concurrency),
                options = listOf(1, 2, 3, 4),
                selected = budget.concurrentLimit,
                labelOf = { it.toString() },
                onSelect = { viewModel.onIntent(AiFeaturesIntent.SetConcurrent(it)) },
            )
            Spacer(Modifier.height(10.dp))
            ChipChoiceRow(
                label = stringResource(R.string.min_interval),
                options = listOf(0L, 500L, 1_200L, 3_000L),
                selected = budget.minIntervalMs,
                labelOf = { if (it == 0L) unlimitedLabel else "${it}ms" },
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
                    Text(stringResource(R.string.cancel), color = colors.textTertiary)
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

@Composable
private fun CategoryHeader(
    category: AiCategory,
    settings: AiFeatureSettings,
    onSetAll: (Boolean) -> Unit,
) {
    val colors = radarColors()
    val all = AiFeature.ofCategory(category)
    val enabledCount = settings.countIn(category)
    // 一键全开涉及 API 费用（UI 审计 M3）：开启需确认，关闭直接执行
    var confirmEnableAll by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${stringResource(category.labelRes())}  $enabledCount/${all.size}",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(category.descriptionRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
            TextButton(onClick = {
                if (settings.allIn(category)) {
                    onSetAll(false)
                } else {
                    confirmEnableAll = true
                }
            }) {
                Text(
                    text = if (settings.allIn(category)) stringResource(R.string.all_off) else stringResource(R.string.all_on),
                    color = colors.accent,
                )
            }
        }
    }
    if (confirmEnableAll) {
        AlertDialog(
            onDismissRequest = { confirmEnableAll = false },
            containerColor = colors.surface1,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text(stringResource(R.string.enable_all_category, stringResource(category.labelRes())), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            text = { Text(stringResource(R.string.enable_all_warning, all.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnableAll = false
                    onSetAll(true)
                }) {
                    Text(stringResource(R.string.enable), color = colors.accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnableAll = false }) {
                    Text(stringResource(R.string.cancel), color = colors.textTertiary)
                }
            },
        )
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
                        text = stringResource(feature.labelRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stringResource(feature.summaryRes()),
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
                TagChip(text = stringResource(feature.trigger.labelRes()), tint = colors.accent)
                TagChip(
                    text = if (feature.needsLlm) stringResource(R.string.needs_llm) else stringResource(R.string.local),
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
                DetailLine(stringResource(R.string.trigger_label), stringResource(feature.trigger.descriptionRes()))
                DetailLine(stringResource(R.string.entry_label), stringResource(feature.entryRes()))
                DetailLine(stringResource(R.string.presentation_label), stringResource(feature.presentationRes()))

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
                                    if (running) stringResource(R.string.running_now) else stringResource(R.string.run_now),
                                    color = if (running) colors.textTertiary else colors.accent,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        if (hasResults) {
                            TextButton(onClick = onOpenResults) {
                                Text(stringResource(R.string.view_results), color = colors.textSecondary)
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
