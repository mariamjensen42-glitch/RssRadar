package com.cycling.rssradar.ui.subscriptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.core.data.db.DEFAULT_GROUP
import com.cycling.rssradar.core.data.db.entity.FeedEntity
import com.cycling.rssradar.core.ui.theme.Danger
import com.composables.icons.lucide.Eraser
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import com.cycling.rssradar.core.data.ai.AiPrompts
import com.cycling.rssradar.core.ui.components.SettingSwitchRow
import com.cycling.rssradar.core.ui.theme.radarOutlinedTextFieldColors

/**
 * 订阅源操作（重命名 / 移动分组 / 删除）的内联底部弹层（该目的地形态已废弃：
 * 整页导航只为弹个 sheet 没有必要，收回 [SubscriptionsScreen] 内，与 [GroupActionSheet] 同形态）。
 * feed 与 groupOptions 由传入的 SubscriptionsViewModel 解析，重命名子对话框自包含，
 * 关闭统一走 onDismiss。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedActionDestination(
    feedId: Long,
    onDismiss: () -> Unit,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    // getFeed/observeFeedAiProfile 每次 fun 调用都会新建 StateFlow（初始值 null），
    // 直接在 Composable 里调用会随重组重建 flow、把 feed 打回 null，
    // 导致 ModalBottomSheet 被反复卸载（空白且无法返回）。必须 remember 固定实例。
    val feed by remember(feedId) { viewModel.getFeed(feedId) }.collectAsStateWithLifecycle()
    val groupOptions by remember { viewModel.groupsList }.collectAsStateWithLifecycle()
    val aiProfile by remember(feedId) { viewModel.observeFeedAiProfile(feedId) }.collectAsStateWithLifecycle()
    val aiFlags by remember(feedId) { viewModel.observeFeedAiFlags(feedId) }.collectAsStateWithLifecycle()
    FeedActionScreen(
        feedId = feedId,
        feed = feed,
        groupOptions = groupOptions,
        // 生效值由 VM 解析（per-feed 优先、否则跟随全局），与 FeedAiProfile 的三态语义一致
        aiFlags = aiFlags,
        aiProfile = aiProfile,
        onDismiss = onDismiss,
        onIntent = viewModel::onIntent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedActionScreen(
    feedId: Long,
    feed: FeedEntity?,
    groupOptions: List<String>,
    aiFlags: FeedAiFlags = FeedAiFlags(),
    aiProfile: com.cycling.rssradar.core.data.db.FeedAiProfileEntity? = null,
    onDismiss: () -> Unit,
    onIntent: (SubscriptionsIntent) -> Unit,
) {
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var aiPromptTarget by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    feed?.let { f ->
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            // 单锚点：内容较高时默认的双锚点（半开/全开）拖动过渡会反复重算高度造成抖动，
            // skipPartiallyExpanded 直接全开，拖拽只做关闭手势
            sheetState =
                rememberBottomSheetState(
                    initialValue = SheetValue.Hidden,
                    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
                ),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // 内容超过一屏时自身滚动，不与 sheet 的拖拽手势抢事件
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = f.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = f.url.removePrefix("https://").removePrefix("http://"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "移动到分组",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groupOptions.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { group ->
                                val selected = group == f.groupName.ifBlank { DEFAULT_GROUP }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable { onIntent(SubscriptionsIntent.MoveFeed(f.id, group)); onDismiss() },
                                ) {
                                    Text(
                                        text = group,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "同步与预设",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(8.dp))
                // 自动同步开关（issue #58）：屏蔽后不参与后台自动同步，手动刷新照常
                SettingSwitchRow(
                    label = "参与自动同步",
                    subtitle = "关闭后此订阅源不再后台自动刷新",
                    checked = f.syncEnabled,
                    onChange = { v ->
                        onIntent(SubscriptionsIntent.SetSyncEnabled(f.id, v))
                    },
                )
                Spacer(Modifier.height(4.dp))
                // 全文抓取开关（issue #9）：关闭后详情页不再自动抓原网页正文
                SettingSwitchRow(
                    label = "自动抓取全文",
                    subtitle = "关闭后详情页只显示订阅源自带内容",
                    checked = f.fullContentEnabled,
                    onChange = { v ->
                        onIntent(SubscriptionsIntent.SetFullContentEnabled(f.id, v))
                    },
                )
                Spacer(Modifier.height(4.dp))
                // 通知开关（#31）：Feed 级第二道闸；全局通知开关关时一律不发
                SettingSwitchRow(
                    label = "新文章通知",
                    subtitle = "关闭后此订阅源的新文章不进系统通知",
                    checked = f.notificationsEnabled,
                    onChange = { v ->
                        onIntent(SubscriptionsIntent.SetNotificationsEnabled(f.id, v))
                    },
                )
                Spacer(Modifier.height(12.dp))
                // 内容类型：决定列表浏览形态（图片画廊/视频音频卡），订阅时已按信号预判
                Text(
                    text = "内容类型",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        FeedEntity.CONTENT_TYPE_ARTICLE to "文章",
                        FeedEntity.CONTENT_TYPE_IMAGE to "图片",
                        FeedEntity.CONTENT_TYPE_VIDEO to "视频",
                        FeedEntity.CONTENT_TYPE_AUDIO to "音频",
                    ).forEach { (type, label) ->
                        val selected = f.contentType == type
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    onIntent(SubscriptionsIntent.SetContentType(f.id, type))
                                },
                        ) {
                            Text(
                                text = label,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                // AI 摘要提示词（AI 智能功能模块）：订阅源级覆盖，留空则跟随内置模板
                Text(
                    text = "AI",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { aiPromptTarget = true },
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("摘要提示词", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (aiProfile?.summaryPrompt.isNullOrBlank()) "使用内置模板" else "已自定义",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text("编辑", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(4.dp))
                // 三条同组：间距交给组，不在行之间手写 Spacer（SettingSwitchRow 自带无外部间距）
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AiSwitchRow(
                        title = "刷新后自动生成摘要",
                        subtitle = "关闭后此源的新文章不自动跑 AI 摘要",
                        state = aiFlags.summary,
                        onCheckedChange = { onIntent(SubscriptionsIntent.SetFeedAutoSummary(f.id, it)) },
                    )
                    AiSwitchRow(
                        title = "自动评分",
                        subtitle = "质量与降噪评分，列表可据此按信息价值排序",
                        state = aiFlags.score,
                        onCheckedChange = { onIntent(SubscriptionsIntent.SetFeedAiFlag(f.id, FeedAiFlag.SCORE, it)) },
                    )
                    AiSwitchRow(
                        title = "健康监控",
                        subtitle = "诊断这个源是否失效、降频或内容质量下滑",
                        state = aiFlags.health,
                        onCheckedChange = { onIntent(SubscriptionsIntent.SetFeedAiFlag(f.id, FeedAiFlag.HEALTH, it)) },
                    )
                }
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { renameTarget = f.title },
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Lucide.Pencil, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("重命名", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                // 清空文章（issue #8）：只删文章保留订阅源，与「删除订阅（含其文章）」区分
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { confirmClear = true },
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Lucide.Eraser, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("清空文章（保留订阅）", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                // 删除是级联不可逆动作（UI 审计 F4）：红色警示 + 二次确认，不再一键直发
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Danger.copy(alpha = 0.10f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { confirmDelete = true },
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Lucide.Trash2, contentDescription = null, tint = Danger, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("删除订阅（含其文章）", color = Danger, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    renameTarget?.let { initial ->
        var value by remember { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("重命名订阅", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    colors = radarOutlinedTextFieldColors(),
                )
            },
            confirmButton = {
                TextButton(onClick = { onIntent(SubscriptionsIntent.RenameFeed(feedId, value)); renameTarget = null; onDismiss() }) {
                    Text("保存", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    // 订阅源级摘要提示词编辑（AI 智能功能模块）：留空 = 回落内置模板
    if (aiPromptTarget) {
        var value by remember { mutableStateOf(aiProfile?.summaryPrompt.orEmpty()) }
        AlertDialog(
            onDismissRequest = { aiPromptTarget = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = {
                Text("摘要提示词", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column {
                    Text(
                        text = "只影响这个订阅源。留空则使用内置模板。" +
                            "可用变量：" + AiPrompts.summaryVariableHelp(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        maxLines = 8,
                        placeholder = {
                            Text(
                                "例如：用一句话说清这条快讯发生了什么",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        colors = radarOutlinedTextFieldColors(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onIntent(SubscriptionsIntent.SetFeedSummaryPrompt(feedId, value))
                        aiPromptTarget = false
                    },
                ) {
                    Text("保存", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { aiPromptTarget = false }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    // 清空文章二次确认（issue #8）：删的是文章，订阅源保留
    if (confirmClear) {
        val title = feed?.title.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("清空文章", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "将删除「$title」的全部文章，收藏与稍后读保留，操作不可撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onIntent(SubscriptionsIntent.ClearFeedArticles(feedId, title))
                        confirmClear = false
                        onDismiss()
                    },
                ) {
                    Text("清空", color = Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
        )
    }

    // 删除订阅二次确认（UI 审计 F4）：级联删文章不可逆，明确影响范围后再动手
    if (confirmDelete) {
        val title = feed?.title.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("删除订阅", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "将删除「$title」及其全部文章，此操作不可撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onIntent(SubscriptionsIntent.DeleteFeed(feedId, title))
                        confirmDelete = false
                        onDismiss()
                    },
                ) {
                    Text("删除", color = Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
        )
    }
}

/**
 * 单源 AI 开关行。
 *
 * 副标题会追加来源标注：未单独配置时说「跟随全局」，配置过说「已单独设置」——
 * 否则用户分不清这一项是自己设的还是跟着全局变的（这正是这四个字段被埋住的后果）。
 */
@Composable
private fun AiSwitchRow(
    title: String,
    subtitle: String,
    state: FeedAiSwitch,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingSwitchRow(
        label = title,
        checked = state.enabled,
        onChange = onCheckedChange,
        subtitle = subtitle + if (state.overridden) "（已单独设置）" else "（跟随全局）",
    )
}
