package com.cycling.rssradar.ui.subscriptions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowDownUp
import com.composables.icons.lucide.CheckCheck
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.FileDown
import com.composables.icons.lucide.FileUp
import com.composables.icons.lucide.FolderInput
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import com.cycling.rssradar.core.data.store.model.FeedSortMode
import com.cycling.rssradar.core.ui.components.AppSnackbarHost
import com.cycling.rssradar.core.ui.components.OptionPickerSheet
import com.cycling.rssradar.core.ui.components.tabBarBottomClearance
import com.cycling.rssradar.core.ui.theme.Danger
import com.cycling.rssradar.core.ui.theme.LocalReducedMotion
import com.cycling.rssradar.core.ui.theme.effectsSpec
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.theme.spatialSpec
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun SubscriptionsScreen(
    viewModel: SubscriptionsViewModel,
    onAddSubscription: () -> Unit = {},
    onCreateGroup: () -> Unit = {},
    /** 点击订阅源 → 进「订阅源文章列表」（issue #51）。 */
    onOpenFeed: (Long) -> Unit = {},
) {
    val groups by viewModel.groups.collectAsState()
    val expandedIds by viewModel.expandedGroupIds.collectAsState()
    val totalUnread by viewModel.totalUnread.collectAsState()
    val groupOptions by viewModel.groupsList.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    // 失效源筛选（#82）
    val unhealthyOnly by viewModel.unhealthyOnly.collectAsState()
    val unhealthyCount by viewModel.unhealthyCount.collectAsState()
    val unhealthyFeeds by viewModel.unhealthyFeeds.collectAsState()
    // 批量移动（issue #7）：多选模式与勾选集合在 ViewModel，弹层显隐是纯 UI 状态留在页面
    val selectionMode by viewModel.selectionMode.collectAsState()
    val selectedIds by viewModel.selectedFeedIds.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val message = viewModel.uiMessage

    // 对话框状态
    var createGroupDialog by remember { mutableStateOf(false) }
    /** 分组操作底栏（重命名/清空文章/删除分组，issue #8）。 */
    var groupActionTarget by remember { mutableStateOf<String?>(null) }
    /** 订阅源操作底栏（重命名/移动分组/删除等），内联弹层不进导航栈。 */
    var feedActionTarget by remember { mutableStateOf<Long?>(null) }
    var batchMoveDialog by remember { mutableStateOf(false) }
    /** 批量删除二次确认：级联删文章不可逆，不能一键直发。 */
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    /** 订阅列表排序选择弹层。 */
    var showSortSheet by remember { mutableStateOf(false) }
    /** 「全部标记为已读」二次确认（批量不可逆，不能一键直发）。 */
    var showMarkAllReadConfirm by remember { mutableStateOf(false) }
    /** 一键删除失效源二次确认（级联删文章不可逆，不能一键直发）。 */
    var showDeleteUnhealthyConfirm by remember { mutableStateOf(false) }
    /** 订阅源搜索：非空时拍平展示命中的订阅行，绕过分组结构直达。 */
    var searchQuery by remember { mutableStateOf("") }

    // 列表 item 动画（docs/motion.md #4）：订阅列表增删 + 位移全开；
    // reduce-motion 时全部置 null = 直接增删（红线：所有动画响应降级）
    val reducedMotion = LocalReducedMotion.current
    val itemFadeSpec: FiniteAnimationSpec<Float>? =
        if (reducedMotion) null else effectsSpec()
    val itemPlacementSpec: FiniteAnimationSpec<IntOffset>? =
        if (reducedMotion) null else spatialSpec()

    // OPML 导入：SAF 文件选择器（mime 放宽，规避文件管理器标注不一致，见 ADR-0004）
    val opmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.onIntent(SubscriptionsIntent.ImportOpml(it)) }
    }
    // OPML 导出（#4）：SAF 另存为，用户自己决定存哪/分享给谁。
    // 文件名固定带日期，避免多次导出互相覆盖。
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/x-opml"),
    ) { uri ->
        uri?.let { viewModel.onIntent(SubscriptionsIntent.ExportOpml(it)) }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onIntent(SubscriptionsIntent.ConsumeMessage)
        }
    }

    Scaffold(
        containerColor = radarColors().bgRoot,
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            if (selectionMode) {
                // 多选态顶栏：计数 + 执行移动 + 退出
                SelectionTopBar(
                    selectedCount = selectedIds.size,
                    canMove = selectedIds.isNotEmpty(),
                    onMove = { batchMoveDialog = true },
                    onDelete = { showBatchDeleteConfirm = true },
                    onCancel = { viewModel.onIntent(SubscriptionsIntent.ToggleSelectionMode) },
                )
            } else {
                SubscriptionsTopBar(
                    onImport = {
                        opmlLauncher.launch(
                            arrayOf("text/*", "application/xml", "application/octet-stream"),
                        )
                    },
                    onExport = {
                        exportLauncher.launch("rssradar-subscriptions-${todayStamp()}.opml")
                    },
                    onSort = { showSortSheet = true },
                    onBatchMove = { viewModel.onIntent(SubscriptionsIntent.ToggleSelectionMode) },
                    onAdd = onAddSubscription,
                    totalUnread = totalUnread,
                    onMarkAllRead = { showMarkAllReadConfirm = true },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // 底部让位悬浮 TabBar（含导航栏 inset）
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = tabBarBottomClearance(),
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 订阅源搜索（700+ 源时滚动翻找不现实）：命中时拍平为单列结果
            item(key = "search", contentType = "search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    placeholder = { Text("搜索订阅源", color = radarColors().textTertiary, style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Lucide.Search, contentDescription = null, tint = radarColors().textTertiary, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                                Icon(Lucide.X, contentDescription = "清空", tint = radarColors().textTertiary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = radarColors().surface1,
                        unfocusedContainerColor = radarColors().surface1,
                        focusedBorderColor = radarColors().accent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = radarColors().textPrimary,
                        unfocusedTextColor = radarColors().textPrimary,
                        cursorColor = radarColors().accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 失效源筛选（#82）：只在有伤员或已开启时出现，平时不占地方
            if (unhealthyCount > 0 || unhealthyOnly) {
                item(key = "unhealthy-filter", contentType = "filter") {
                    FilterChip(
                        selected = unhealthyOnly,
                        onClick = { viewModel.onIntent(SubscriptionsIntent.ToggleUnhealthyFilter) },
                        label = {
                            Text(
                                text = if (unhealthyOnly) "失效源 $unhealthyCount（点击取消筛选）" else "失效源 ($unhealthyCount)",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                }
            }

            if (searchQuery.isNotBlank()) {
                val hits = groups
                    .asSequence()
                    .flatMap { it.feeds.asSequence() }
                    .filter { it.feed.title.contains(searchQuery.trim(), ignoreCase = true) }
                    .toList()
                if (hits.isEmpty()) {
                    item(key = "no-hit", contentType = "no-hit") {
                        Text(
                            text = "没有匹配「${searchQuery.trim()}」的订阅源",
                            color = radarColors().textTertiary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else {
                    items(hits, key = { "hit-${it.feed.id}" }, contentType = { "feed" }) { feedItem ->
                        FeedRow(
                            item = feedItem,
                            selectionMode = selectionMode,
                            selected = feedItem.feed.id in selectedIds,
                            onClick = {
                                if (selectionMode) {
                                    viewModel.onIntent(SubscriptionsIntent.ToggleFeedSelected(feedItem.feed.id))
                                } else {
                                    onOpenFeed(feedItem.feed.id)
                                }
                            },
                            onMore = { feedActionTarget = feedItem.feed.id },
                        )
                    }
                }
            } else if (unhealthyOnly) {
                // 只看失效源（#82）：拍平展示，与搜索命中同一形态
                if (unhealthyFeeds.isEmpty()) {
                    item(key = "no-unhealthy", contentType = "no-hit") {
                        Text(
                            text = "没有失效的订阅源",
                            color = radarColors().textTertiary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else {
                    // 一键删除失效源：入口就在失效列表顶部，紧邻目标，不用逐个进操作菜单
                    item(key = "delete-unhealthy", contentType = "action") {
                        TextButton(
                            onClick = { showDeleteUnhealthyConfirm = true },
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            Icon(Lucide.Trash2, contentDescription = null, tint = Danger, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "删除全部失效源（${unhealthyFeeds.size}）",
                                color = Danger,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    items(unhealthyFeeds, key = { "unhealthy-${it.feed.id}" }, contentType = { "feed" }) { feedItem ->
                        FeedRow(
                            item = feedItem,
                            selectionMode = selectionMode,
                            selected = feedItem.feed.id in selectedIds,
                            onClick = {
                                if (selectionMode) {
                                    viewModel.onIntent(SubscriptionsIntent.ToggleFeedSelected(feedItem.feed.id))
                                } else {
                                    onOpenFeed(feedItem.feed.id)
                                }
                            },
                            onMore = { feedActionTarget = feedItem.feed.id },
                        )
                    }
                }
                if (!selectionMode) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        CreateGroupRow(onClick = { createGroupDialog = true })
                    }
                }
            } else {
            // 分组列表拍平（#48）：分组头与 FeedRow 都是 LazyColumn 的 item，
            // 展开大分组只组合可见行——原 AnimatedVisibility { forEach } 会把
            // 几百行一次性同步组合在主线程上，点击分组卡顿的根因。
            groups.forEach { group ->
                // 分组头吸顶（stickyHeader 是 LazyListScope 成员，foundation 1.10+ 无需 import）；
                // 贴顶时后续内容会从背后滚过，必须铺 bgRoot 底色遮住
                stickyHeader(key = "header-${group.group}", contentType = "header") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(radarColors().bgRoot)
                            .animateItem(itemFadeSpec, itemPlacementSpec, itemFadeSpec),
                    ) {
                        GroupHeader(
                            title = group.group,
                            feedCount = group.feeds.size,
                            expanded = group.group in expandedIds,
                            onToggle = { viewModel.onIntent(SubscriptionsIntent.ToggleGroup(group.group)) },
                            // 长按 → 分组操作底栏（重命名/清空文章/删除分组，issue #8）；
                            // 行尾铅笔已移除——每个分组都挂一支铅笔是噪音，长按是不可发现性
                            // 与低频的合理交换（操作底栏也会在误触时有明确出口）
                            onEdit = { groupActionTarget = group.group },
                        )
                    }
                }
                if (group.group in expandedIds) {
                    group.feeds.forEach { feedItem ->
                        item(key = "feed-${feedItem.feed.id}", contentType = "feed") {
                            // animateItem 全量（docs/motion.md #4）：订阅列表量级小，
                            // 增删 + 位移都开；reduce-motion 见上面的 spec 置 null
                            Box(
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .animateItem(itemFadeSpec, itemPlacementSpec, itemFadeSpec),
                            ) {
                                FeedRow(
                                    item = feedItem,
                                    selectionMode = selectionMode,
                                    selected = feedItem.feed.id in selectedIds,
                                    // 多选态整行点击 = 勾选；常规态 = 进订阅源文章列表
                                    onClick = {
                                        if (selectionMode) {
                                            viewModel.onIntent(
                                                SubscriptionsIntent.ToggleFeedSelected(feedItem.feed.id),
                                            )
                                        } else {
                                            onOpenFeed(feedItem.feed.id)
                                        }
                                    },
                                    onMore = { feedActionTarget = feedItem.feed.id },
                                )
                            }
                        }
                    }
                }
            }

            // 多选态下新建分组没有意义
            if (!selectionMode) {
                item {
                    Spacer(Modifier.height(4.dp))
                    CreateGroupRow(onClick = { createGroupDialog = true })
                }
            }
            }
        }
    }

    // 「全部标记为已读」二次确认：把 N 篇未读一口气写死前，给一次反悔的机会
    if (showMarkAllReadConfirm) {
        AlertDialog(
            onDismissRequest = { showMarkAllReadConfirm = false },
            containerColor = radarColors().surface1,
            titleContentColor = radarColors().textPrimary,
            textContentColor = radarColors().textSecondary,
            title = { Text("全部标记为已读", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            text = { Text("将把全部 $totalUnread 篇未读文章标为已读，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showMarkAllReadConfirm = false
                    viewModel.onIntent(SubscriptionsIntent.MarkAllRead)
                }) {
                    Text("标记已读", color = radarColors().accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMarkAllReadConfirm = false }) {
                    Text("取消", color = radarColors().textTertiary)
                }
            },
        )
    }

    if (createGroupDialog) {
        TextInputDialog(
            title = "新建分组",
            placeholder = "分组名称",
            confirmText = "创建",
            onDismiss = { createGroupDialog = false },
            onConfirm = { name ->
                viewModel.onIntent(SubscriptionsIntent.CreateGroup(name))
                createGroupDialog = false
            },
        )
    }

    // 批量移动：选好目标分组后一次性移动所有勾选项（issue #7）
    if (batchMoveDialog) {
        BatchMoveToGroupDialog(
            groups = groupOptions,
            selectedCount = selectedIds.size,
            onDismiss = { batchMoveDialog = false },
            onConfirm = { group ->
                viewModel.onIntent(SubscriptionsIntent.MoveSelectedFeeds(group))
                batchMoveDialog = false
            },
        )
    }

    // 批量删除二次确认：级联删文章不可逆，明确告知影响范围后再动手
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            containerColor = radarColors().surface1,
            titleContentColor = radarColors().textPrimary,
            textContentColor = radarColors().textSecondary,
            title = { Text("删除订阅源", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            text = { Text("将删除 ${selectedIds.size} 个订阅源及其全部文章，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showBatchDeleteConfirm = false
                    viewModel.onIntent(SubscriptionsIntent.DeleteSelectedFeeds)
                }) {
                    Text("删除", color = Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("取消", color = radarColors().textTertiary)
                }
            },
        )
    }

    // 一键删除失效源确认：失效不等于用户确认不要（可能只是暂时故障），必须让用户看见数量再动手
    if (showDeleteUnhealthyConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteUnhealthyConfirm = false },
            containerColor = radarColors().surface1,
            titleContentColor = radarColors().textPrimary,
            textContentColor = radarColors().textSecondary,
            title = { Text("删除失效订阅源", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            text = { Text("将删除 ${unhealthyFeeds.size} 个失效订阅源及其全部文章，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteUnhealthyConfirm = false
                    viewModel.onIntent(SubscriptionsIntent.DeleteUnhealthyFeeds)
                }) {
                    Text("删除", color = Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteUnhealthyConfirm = false }) {
                    Text("取消", color = radarColors().textTertiary)
                }
            },
        )
    }

    // 分组操作底栏：重命名 / 清空分组文章 / 删除分组（issue #8）
    groupActionTarget?.let { group ->
        GroupActionSheet(
            group = group,
            viewModel = viewModel,
            onDismiss = { groupActionTarget = null },
        )
    }

    // 订阅源操作底栏：重命名 / 移动分组 / 删除等（原 nav 目的地，收回内联弹层）
    feedActionTarget?.let { feedId ->
        FeedActionScreen(
            feedId = feedId,
            viewModel = viewModel,
            onDismiss = { feedActionTarget = null },
        )
    }

    // 订阅列表排序（按名称/最近更新/未读数）：选择即生效并持久化
    if (showSortSheet) {
        OptionPickerSheet(
            title = "订阅列表排序",
            options = FeedSortMode.entries.toList(),
            selected = sortMode,
            label = { it.label },
            subtitle = { mode ->
                when (mode) {
                    FeedSortMode.BY_NAME -> "订阅源按标题排列"
                    FeedSortMode.BY_RECENT -> "最近有新文章的源排前面"
                    FeedSortMode.BY_UNREAD -> "未读文章多的源排前面"
                }
            },
            onSelect = { mode -> viewModel.onIntent(SubscriptionsIntent.SelectSort(mode)) },
            onDismiss = { showSortSheet = false },
        )
    }
}

@Composable
private fun SubscriptionsTopBar(
    onImport: () -> Unit,
    onExport: () -> Unit,
    onSort: () -> Unit,
    onBatchMove: () -> Unit,
    onAdd: () -> Unit,
    totalUnread: Int,
    onMarkAllRead: () -> Unit,
) {
    // 顶栏只留高频的「添加」，低频操作（导入/导出/批量移动/排序/全部已读）收进溢出菜单：
    // 5 个无标签图标并排，新用户不可能猜出哪个是导入哪个是导出
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "订阅管理",
            color = radarColors().textPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onAdd) {
            Icon(Lucide.Plus, contentDescription = "添加订阅", tint = radarColors().textPrimary)
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Lucide.EllipsisVertical, contentDescription = "更多操作", tint = radarColors().textPrimary)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("导入 OPML") },
                    leadingIcon = { Icon(Lucide.FileUp, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onImport()
                    },
                )
                // OPML 导出（#4）：导入的逆操作，订阅清单不被本应用绑架
                DropdownMenuItem(
                    text = { Text("导出 OPML") },
                    leadingIcon = { Icon(Lucide.FileDown, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onExport()
                    },
                )
                // 批量移动入口（issue #7）：进入多选态，勾选后一次移动到目标分组
                DropdownMenuItem(
                    text = { Text("批量移动") },
                    leadingIcon = { Icon(Lucide.FolderInput, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onBatchMove()
                    },
                )
                DropdownMenuItem(
                    text = { Text("排序") },
                    leadingIcon = { Icon(Lucide.ArrowDownUp, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onSort()
                    },
                )
                // 全部标记已读是批量不可逆操作：收进菜单（不裸露在列表里）+ 二次确认
                if (totalUnread > 0) {
                    DropdownMenuItem(
                        text = { Text("全部标记为已读（$totalUnread）") },
                        leadingIcon = { Icon(Lucide.CheckCheck, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onMarkAllRead()
                        },
                    )
                }
            }
        }
    }
}

internal fun String.withoutScheme(): String = removePrefix("https://").removePrefix("http://")

/** 导出文件名日期后缀：多次导出不互相覆盖。 */
private fun todayStamp(): String =
    java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
