package com.cycling.rssradar.ui.subscriptions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.core.data.db.DEFAULT_GROUP
import com.cycling.rssradar.core.data.store.FeedSortMode
import com.cycling.rssradar.core.ui.components.AppSnackbarHost
import com.cycling.rssradar.core.ui.components.FeedIcon
import com.cycling.rssradar.core.ui.components.FloatingTabBarFabOffset
import com.cycling.rssradar.core.ui.components.OptionPickerSheet
import com.cycling.rssradar.core.ui.components.tabBarBottomClearance
import com.composables.icons.lucide.ArrowDownUp
import com.composables.icons.lucide.BookMarked
import com.composables.icons.lucide.CheckCheck
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.FileDown
import com.composables.icons.lucide.FileUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CornerUpRight
import com.composables.icons.lucide.FolderInput
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.SquareCheckBig
import com.composables.icons.lucide.X
import com.cycling.rssradar.core.data.db.FeedEntity
import com.cycling.rssradar.core.ui.components.pressScale
import com.cycling.rssradar.core.ui.theme.Danger
import com.cycling.rssradar.core.ui.theme.LocalReducedMotion
import com.cycling.rssradar.core.ui.theme.MotionTokens
import com.cycling.rssradar.core.ui.theme.radarColors


@Composable
fun SubscriptionsScreen(
    viewModel: SubscriptionsViewModel,
    onAddSubscription: () -> Unit = {},
    onCreateGroup: () -> Unit = {},
    onFeedAction: (Long) -> Unit = {},
    /** 点击订阅源 → 进「订阅源文章列表」（issue #51）。 */
    onOpenFeed: (Long) -> Unit = {},
) {
    val groups by viewModel.groups.collectAsState()
    val expandedIds by viewModel.expandedGroupIds.collectAsState()
    val totalUnread by viewModel.totalUnread.collectAsState()
    val groupOptions by viewModel.groupsList.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    // 批量移动（issue #7）：多选模式与勾选集合在 ViewModel，弹层显隐是纯 UI 状态留在页面
    val selectionMode by viewModel.selectionMode.collectAsState()
    val selectedIds by viewModel.selectedFeedIds.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val message = viewModel.uiMessage

    // 对话框状态
    var createGroupDialog by remember { mutableStateOf(false) }
    /** 分组操作底栏（重命名/清空文章/删除分组，issue #8）。 */
    var groupActionTarget by remember { mutableStateOf<String?>(null) }
    var batchMoveDialog by remember { mutableStateOf(false) }
    /** 批量删除二次确认：级联删文章不可逆，不能一键直发。 */
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    /** 订阅列表排序选择弹层。 */
    var showSortSheet by remember { mutableStateOf(false) }
    /** 「全部标记为已读」二次确认（批量不可逆，不能一键直发）。 */
    var showMarkAllReadConfirm by remember { mutableStateOf(false) }
    /** 订阅源搜索：非空时拍平展示命中的订阅行，绕过分组结构直达。 */
    var searchQuery by remember { mutableStateOf("") }

    // 列表 item 动画（docs/motion.md #4）：订阅列表增删 + 位移全开；
    // reduce-motion 时全部置 null = 直接增删（红线：所有动画响应降级）
    val reducedMotion = LocalReducedMotion.current
    val itemFadeSpec: FiniteAnimationSpec<Float>? =
        if (reducedMotion) null else tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard)
    val itemPlacementSpec: FiniteAnimationSpec<IntOffset>? =
        if (reducedMotion) null else tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard)

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
        floatingActionButton = {
            // 多选态隐藏 FAB：它与「选完再移动」的操作流冲突
            if (!selectionMode) {
                // 主操作按钮按压缩放（docs/motion.md #2）
                val fabInteraction = remember { MutableInteractionSource() }
                ExtendedFloatingActionButton(
                    onClick = onAddSubscription,
                    interactionSource = fabInteraction,
                    containerColor = radarColors().accent,
                    contentColor = radarColors().onAccent,
                    icon = { Icon(Lucide.Plus, contentDescription = null) },
                    text = { Text("添加") },
                    shape = RoundedCornerShape(20.dp),
                    // 抬升让开底部悬浮 TabBar（与 FeedListScreen 的 FAB 同一规则）
                    modifier = Modifier
                        .padding(bottom = FloatingTabBarFabOffset)
                        .pressScale(fabInteraction),
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
                            onMore = { onFeedAction(feedItem.feed.id) },
                        )
                    }
                }
            } else {
            // 分组列表拍平（#48）：分组头与 FeedRow 都是 LazyColumn 的 item，
            // 展开大分组只组合可见行——原 AnimatedVisibility { forEach } 会把
            // 几百行一次性同步组合在主线程上，点击分组卡顿的根因。
            groups.forEach { group ->
                item(key = "header-${group.group}", contentType = "header") {
                    Box(modifier = Modifier.animateItem(itemFadeSpec, itemPlacementSpec, itemFadeSpec)) {
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
                                    onMore = { onFeedAction(feedItem.feed.id) },
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

            item { Spacer(Modifier.height(96.dp)) } // 避让 FAB
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

    // 分组操作底栏：重命名 / 清空分组文章 / 删除分组（issue #8）
    groupActionTarget?.let { group ->
        GroupActionSheet(
            group = group,
            viewModel = viewModel,
            onDismiss = { groupActionTarget = null },
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

/** 多选态顶栏：已选计数 + 执行移动/删除 + 退出。 */
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    canMove: Boolean,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "已选择 $selectedCount 个订阅",
            color = radarColors().textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onMove, enabled = canMove) {
            Text("移动到", color = if (canMove) radarColors().accent else radarColors().textTertiary, fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onDelete, enabled = canMove) {
            Text("删除", color = if (canMove) Danger else radarColors().textTertiary, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = onCancel) {
            Icon(Lucide.X, contentDescription = "退出多选", tint = radarColors().textPrimary)
        }
    }
}

@Composable
private fun GroupHeader(
    title: String,
    feedCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 长按分组行 = 编辑（重命名/清空/删除）；行尾铅笔图标已删，减少视觉噪音
            .combinedClickable(onClick = onToggle, onLongClick = onEdit)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) Lucide.ChevronDown else Lucide.ChevronRight,
            contentDescription = if (expanded) "折叠" else "展开",
            tint = radarColors().textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            color = radarColors().textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$feedCount 个订阅",
            color = radarColors().textTertiary,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun FeedRow(
    item: FeedWithUnread,
    onClick: () -> Unit,
    onMore: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
) {
    // 按压缩放（docs/motion.md #2）：source 与 clickable 共用同一实例
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = radarColors().surface1,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            // 整行点击进「订阅源文章列表」（issue #51）；管理入口仍是行尾"⋯"
            .clickable(interactionSource = interactionSource, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 多选态：行首勾选框，图标与 tint 直接反映选中态
            if (selectionMode) {
                Icon(
                    imageVector = if (selected) Lucide.SquareCheckBig else Lucide.Square,
                    contentDescription = if (selected) "取消选择" else "选择",
                    tint = if (selected) radarColors().accent else radarColors().textTertiary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            FeedIcon(title = item.feed.title, iconUrl = item.feed.iconUrl, size = 32.dp, cornerRadius = 8.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.feed.title,
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.feed.url.withoutScheme(),
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 类型标记：RSSHub 路由和常规 RSS 一眼区分
            if (item.feed.sourceType == FeedEntity.SOURCE_TYPE_RSSHUB) {
                Spacer(Modifier.width(6.dp))
                Surface(shape = RoundedCornerShape(50), color = radarColors().surface2) {
                    Text(
                        text = "RSSHub",
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            UnreadBadge(count = item.unreadCount)
            // 多选态隐藏"⋯"：勾选才是当前主要动作，避免点错进操作页
            if (!selectionMode) {
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onMore, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Lucide.Ellipsis,
                        contentDescription = "更多",
                        tint = radarColors().textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    if (count <= 0) {
        Surface(shape = RoundedCornerShape(50), color = radarColors().surface2) {
            Text(
                text = "已读",
                color = radarColors().textTertiary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        return
    }
    Surface(shape = RoundedCornerShape(50), color = radarColors().accent) {
        Text(
            text = count.coerceAtMost(999).toString(),
            color = radarColors().onAccent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun CreateGroupRow(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = radarColors().surface1,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Lucide.Plus,
                contentDescription = null,
                tint = radarColors().link,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "新建分组",
                color = radarColors().link,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * 批量移动的目标分组选择（issue #7）：单选一个已注册分组，确认后一次性移动全部勾选项。
 * 分组数量是用户自建的量级（几十个以内），直接竖排滚动，不做懒加载。
 */
@Composable
private fun BatchMoveToGroupDialog(
    groups: List<String>,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var target by remember { mutableStateOf(groups.firstOrNull().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = radarColors().surface1,
        titleContentColor = radarColors().textPrimary,
        textContentColor = radarColors().textSecondary,
        title = {
            Text(
                "移动 $selectedCount 个订阅到",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                groups.forEach { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { target = group }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = group,
                            color = radarColors().textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (group == target) {
                            Icon(
                                Lucide.Check,
                                contentDescription = null,
                                tint = radarColors().accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(target) }, enabled = target.isNotBlank()) {
                Text("移动", color = radarColors().accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = radarColors().textTertiary) }
        },
    )
}

private fun String.withoutScheme(): String = removePrefix("https://").removePrefix("http://")

/** 导出文件名日期后缀：多次导出不互相覆盖。 */
private fun todayStamp(): String =
    java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())

/** 通用单行输入对话框：新建/重命名分组、重命名订阅共用。 */
@Composable
private fun TextInputDialog(
    title: String,
    placeholder: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initialValue: String = "",
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = radarColors().surface1,
        titleContentColor = radarColors().textPrimary,
        textContentColor = radarColors().textSecondary,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text(placeholder, color = radarColors().textTertiary, style = MaterialTheme.typography.bodyMedium) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = radarColors().surface2,
                    unfocusedContainerColor = radarColors().surface2,
                    focusedBorderColor = radarColors().accent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = radarColors().textPrimary,
                    unfocusedTextColor = radarColors().textPrimary,
                    cursorColor = radarColors().accent,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(confirmText, color = radarColors().accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = radarColors().textTertiary)
            }
        },
    )
}

