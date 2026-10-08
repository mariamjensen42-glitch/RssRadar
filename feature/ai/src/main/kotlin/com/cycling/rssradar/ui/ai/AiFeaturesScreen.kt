package com.cycling.rssradar.ui.ai

import com.cycling.rssradar.core.ui.R as UiR
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.cycling.rssradar.core.model.AiCategory
import com.cycling.rssradar.core.model.AiFeature
import com.cycling.rssradar.core.model.AiFeatureSettings
import com.cycling.rssradar.core.model.AiTrigger
import com.cycling.rssradar.core.ui.components.AppSnackbarHost
import com.cycling.rssradar.core.ui.text.resolve
import com.cycling.rssradar.core.ui.theme.radarSwitchColors

/**
 * 页内三段：功能开关 / 用量与预算 / 任务队列。
 *
 * 这三件事原先挤在同一屏：用量卡、限流设置、队列、12 个开关自上而下平铺。
 * 它们的性质完全不同（只读统计 / 花钱上限 / 后台运维 / 功能选择），
 * 混在一列里滚动时"我在配置什么"是不清楚的——开关被顶到第三屏，
 * 队列里的失败任务又和开关挨着。分成三段之后，每段只回答一个问题。
 */
private enum class AiSection {
    FEATURES,
    USAGE,
    RUNTIME,
}

private fun AiSection.labelRes(): Int = when (this) {
    AiSection.FEATURES -> R.string.ai_section_features
    AiSection.USAGE -> R.string.ai_section_usage
    AiSection.RUNTIME -> R.string.ai_section_runtime
}

/**
 * AI 智能功能总览：16 项功能的独立开关、用量看板、任务队列与预算设置。
 *
 * 四条刻意的 UI 决定：
 * 1. **一页三段**（功能 / 用量 / 运行）。原先四类内容平铺在一列里，见 [AiSection]。
 * 2. **每项默认折叠，点行展开触发方式 / 交互入口 / 结果展示**——
 *    16 项全展开是一堵墙，但"这功能到底什么时候会跑"必须在同一屏里能查到，
 *    否则用户面对一堆开关无从判断该开哪个。
 * 3. **分组收进一张卡里**（而不是每组若干张同权重小卡）：组内用分隔线切行，
 *    分组之间才有边界。原先每项一张一模一样的卡片排下来，三个分组的标题淹没其中。
 * 4. **消耗额度的项打「调用模型」角标**，本地计算的打「本地」——
 *    这两个的成本差了几个数量级，用户有权一眼分辨。
 */
@Composable
fun AiFeaturesDestination(
    onBack: () -> Unit,
    /**
     * 打开 AI 产物中心。参数是预选功能的 dbValue（null = 全部）。
     *
     * 入口刻意放在这一页而不是只放在设置页：用户开启功能后第一反应是回来找结果，
     * 而一部分功能没有专属展示位——产物中心（按功能筛选）是它们的出口，
     * 藏深了等于又一次"跑成功了但看不到结果"。
     */
    onOpenArtifacts: (Int?) -> Unit = { _ -> },
    viewModel: AiFeaturesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AiFeaturesScreen(
        state = state,
        onBack = onBack,
        onOpenArtifacts = onOpenArtifacts,
        onIntent = viewModel::onIntent,
    )
}

@Composable
fun AiFeaturesScreen(
    state: AiFeaturesUiState,
    onBack: () -> Unit,
    /**
     * 打开 AI 产物中心。参数是预选功能的 dbValue（null = 全部）。
     *
     * 入口刻意放在这一页而不是只放在设置页：用户开启功能后第一反应是回来找结果，
     * 而一部分功能没有专属展示位——产物中心（按功能筛选）是它们的出口，
     * 藏深了等于又一次"跑成功了但看不到结果"。
     */
    onOpenArtifacts: (Int?) -> Unit = { _ -> },
    onIntent: (AiFeaturesIntent) -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var section by remember { mutableStateOf(AiSection.FEATURES) }
    val listState = rememberLazyListState()

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message.resolve(context))
        onIntent(AiFeaturesIntent.ConsumeMessage)
    }

    // 三段的内容长度差得远，切段后沿用上一段的滚动位置会落在"半空"里。
    LaunchedEffect(section) { listState.scrollToItem(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
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
                        contentDescription = stringResource(UiR.string.back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = stringResource(R.string.ai_features_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                // 产物中心入口：文字链接太弱（65+ 条产物的家），换填充 chip 提权重
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onOpenArtifacts(null) },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Lucide.Sparkles,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.view_results),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SectionSwitcher(selected = section, onSelect = { section = it })
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                when (section) {
                    AiSection.FEATURES -> {
                        AiCategory.entries.forEach { category ->
                            item(key = "cat-${category.name}") {
                                CategoryBlock(
                                    category = category,
                                    settings = state.settings,
                                    running = state.running,
                                    onToggle = { onIntent(AiFeaturesIntent.Toggle(it)) },
                                    onRun = { onIntent(AiFeaturesIntent.RunFeature(it)) },
                                    onOpenResults = { onOpenArtifacts(it.dbValue) },
                                    onSetAll = { enabled ->
                                        onIntent(AiFeaturesIntent.SetCategory(category, enabled))
                                    },
                                )
                            }
                        }
                        item(key = "footer") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                TextButton(onClick = { onIntent(AiFeaturesIntent.ResetDefaults) }) {
                                    Text(stringResource(R.string.restore_defaults), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { onIntent(AiFeaturesIntent.DisableAllPaid) }) {
                                    Text(stringResource(R.string.disable_all_paid), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    AiSection.USAGE -> {
                        item(key = "usage") { UsageCard(state.budget) }
                        item(key = "budget") { BudgetSection(state.budget, onIntent) }
                    }

                    AiSection.RUNTIME -> {
                        item(key = "queue") { QueueSection(state.queue, state.running, onIntent) }
                        item(key = "runtime-hint") {
                            Text(
                                text = stringResource(R.string.ai_runtime_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── 段切换 ──────────────────────────────────────────────────────────────────

@Composable
private fun SectionSwitcher(selected: AiSection, onSelect: (AiSection) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        AiSection.entries.forEachIndexed { index, item ->
            SegmentedButton(
                selected = selected == item,
                onClick = { onSelect(item) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = AiSection.entries.size),
                label = { Text(stringResource(item.labelRes())) },
            )
        }
    }
}

// ── 分组与功能行 ────────────────────────────────────────────────────────────

/**
 * 一个功能分组（内容处理 / 推荐发现 / 辅助推送）：组头 + 组内功能行，装在同一张卡里。
 *
 * 间距一律留在卡外（`spacedBy(14.dp)` 由 LazyColumn 给），卡内只用分隔线切行——
 * 组内的行是同一件事的不同选项，把它们拆成独立卡片会让"这属于一组"这个信息消失。
 */
@Composable
private fun CategoryBlock(
    category: AiCategory,
    settings: AiFeatureSettings,
    running: Boolean,
    onToggle: (AiFeature) -> Unit,
    onRun: (AiFeature) -> Unit,
    onOpenResults: (AiFeature) -> Unit,
    onSetAll: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // 只列「可开关」的功能：用量看板 / 任务队列 / 提示词管理挂在同一张枚举表里，
    // 但它们的 isEnabled 无人读取（拨动不改变行为），所以只作为设置入口、不进开关列表。
    val all = AiFeature.configurableOfCategory(category)
    val enabledCount = all.count { settings.isEnabled(it) }
    val allEnabled = all.isNotEmpty() && all.all { settings.isEnabled(it) }
    // 一键全开涉及 API 费用（UI 审计 M3）：开启需确认，关闭直接执行
    var confirmEnableAll by remember { mutableStateOf(false) }

    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerLowest) {
        Column {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(category.labelRes()),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.ai_category_enabled_count, enabledCount, all.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (enabledCount > 0) colors.primary else colors.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(category.descriptionRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (all.isNotEmpty()) {
                    TextButton(onClick = {
                        if (allEnabled) {
                            onSetAll(false)
                        } else {
                            confirmEnableAll = true
                        }
                    }) {
                        Text(
                            text = if (allEnabled) stringResource(R.string.all_off) else stringResource(R.string.all_on),
                            color = colors.primary,
                        )
                    }
                }
            }

            all.forEach { feature ->
                HorizontalDivider(color = colors.surfaceContainer, thickness = 1.dp)
                FeatureRow(
                    feature = feature,
                    enabled = settings.isEnabled(feature),
                    running = running,
                    onToggle = { onToggle(feature) },
                    onRun = { onRun(feature) },
                    onOpenResults = { onOpenResults(feature) },
                )
            }
        }
    }

    if (confirmEnableAll) {
        AlertDialog(
            onDismissRequest = { confirmEnableAll = false },
            containerColor = colors.surfaceContainerLowest,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurfaceVariant,
            title = { Text(stringResource(R.string.enable_all_category, stringResource(category.labelRes())), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            text = { Text(stringResource(R.string.enable_all_warning, all.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnableAll = false
                    onSetAll(true)
                }) {
                    Text(stringResource(R.string.enable), color = colors.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnableAll = false }) {
                    Text(stringResource(UiR.string.cancel), color = colors.onSurfaceVariant)
                }
            },
        )
    }
}

/**
 * 单项功能行：标题 + 成本角标 + 摘要 + 开关；点行展开说明与操作。
 *
 * 开关与展开是两个独立动作（`Switch` 自行消费点击），
 * 所以行上的 `clickable` 只负责展开——不会出现"想改开关却把行展开了"。
 */
@Composable
private fun FeatureRow(
    feature: AiFeature,
    enabled: Boolean,
    running: Boolean,
    onToggle: () -> Unit,
    onRun: () -> Unit,
    onOpenResults: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(feature.labelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(feature.summaryRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagChip(text = stringResource(feature.trigger.labelRes()), tint = colors.primary)
                    TagChip(
                        text = if (feature.needsLlm) stringResource(R.string.needs_llm) else stringResource(R.string.local),
                        tint = if (feature.needsLlm) colors.onSurfaceVariant else colors.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
                colors = radarSwitchColors(),
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            ) {
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
                                    color = if (running) colors.onSurfaceVariant else colors.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        if (hasResults) {
                            TextButton(onClick = onOpenResults) {
                                Text(stringResource(R.string.view_results), color = colors.onSurfaceVariant)
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
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(6.dp), color = colors.surfaceContainer) {
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
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

// ── 用量看板 ────────────────────────────────────────────────────────────────
