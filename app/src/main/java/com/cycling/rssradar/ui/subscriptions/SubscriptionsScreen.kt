package com.cycling.rssradar.ui.subscriptions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.data.db.DEFAULT_GROUP
import com.cycling.rssradar.ui.components.AppSnackbarHost
import com.cycling.rssradar.ui.components.FeedIcon
import com.cycling.rssradar.ui.components.FloatingTabBarFabOffset
import com.cycling.rssradar.ui.components.tabBarBottomClearance
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.BgRoot
import com.cycling.rssradar.ui.theme.Link
import com.cycling.rssradar.ui.theme.OnAccent
import com.cycling.rssradar.ui.theme.Surface1
import com.cycling.rssradar.ui.theme.Surface2
import com.cycling.rssradar.ui.theme.TextPrimary
import com.cycling.rssradar.ui.theme.TextSecondary
import com.cycling.rssradar.ui.theme.TextTertiary
import com.composables.icons.lucide.ArrowDownUp
import com.composables.icons.lucide.BookMarked
import com.composables.icons.lucide.CheckCheck
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.FileDown
import com.composables.icons.lucide.FileUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CornerUpRight
import com.composables.icons.lucide.FolderInput
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.SquareCheckBig
import com.composables.icons.lucide.X
import com.cycling.rssradar.data.db.FeedEntity


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
        containerColor = BgRoot,
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            if (selectionMode) {
                // 多选态顶栏：计数 + 执行移动 + 退出
                SelectionTopBar(
                    selectedCount = selectedIds.size,
                    canMove = selectedIds.isNotEmpty(),
                    onMove = { batchMoveDialog = true },
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
                    onSort = { viewModel.onIntent(SubscriptionsIntent.ToggleSort) },
                    onBatchMove = { viewModel.onIntent(SubscriptionsIntent.ToggleSelectionMode) },
                    onAdd = onAddSubscription,
                )
            }
        },
        floatingActionButton = {
            // 多选态隐藏 FAB：它与「选完再移动」的操作流冲突
            if (!selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = onAddSubscription,
                    containerColor = Accent,
                    contentColor = OnAccent,
                    icon = { Icon(Lucide.Plus, contentDescription = null) },
                    text = { Text("添加") },
                    shape = RoundedCornerShape(20.dp),
                    // 抬升让开底部悬浮 TabBar（与 FeedListScreen 的 FAB 同一规则）
                    modifier = Modifier.padding(bottom = FloatingTabBarFabOffset),
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
            // 分组列表拍平（#48）：分组头与 FeedRow 都是 LazyColumn 的 item，
            // 展开大分组只组合可见行——原 AnimatedVisibility { forEach } 会把
            // 几百行一次性同步组合在主线程上，点击分组卡顿的根因。
            groups.forEach { group ->
                item(key = "header-${group.group}", contentType = "header") {
                    GroupHeader(
                        title = group.group,
                        feedCount = group.feeds.size,
                        expanded = group.group in expandedIds,
                        onToggle = { viewModel.onIntent(SubscriptionsIntent.ToggleGroup(group.group)) },
                        // 铅笔 → 分组操作底栏（重命名/清空文章/删除分组，issue #8）
                        onEdit = { groupActionTarget = group.group },
                    )
                }
                if (group.group in expandedIds) {
                    group.feeds.forEach { feedItem ->
                        item(key = "feed-${feedItem.feed.id}", contentType = "feed") {
                            Box(Modifier.padding(start = 12.dp)) {
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

            // 多选态下这两行没有意义：新建分组与「全部已读」都打断勾选流程
            if (!selectionMode) {
                item {
                    Spacer(Modifier.height(4.dp))
                    CreateGroupRow(onClick = { createGroupDialog = true })
                }

                if (totalUnread > 0) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        MarkAllReadRow(onClick = { viewModel.onIntent(SubscriptionsIntent.MarkAllRead) })
                    }
                }
            }

            item { Spacer(Modifier.height(96.dp)) } // 避让 FAB
        }
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

    // 分组操作底栏：重命名 / 清空分组文章 / 删除分组（issue #8）
    groupActionTarget?.let { group ->
        GroupActionSheet(
            group = group,
            viewModel = viewModel,
            onDismiss = { groupActionTarget = null },
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "订阅管理",
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onImport) {
            Icon(Lucide.FileUp, contentDescription = "导入 OPML", tint = TextPrimary)
        }
        // OPML 导出（#4）：导入的逆操作，订阅清单不被本应用绑架
        IconButton(onClick = onExport) {
            Icon(Lucide.FileDown, contentDescription = "导出 OPML", tint = TextPrimary)
        }
        // 批量移动入口（issue #7）：进入多选态，勾选后一次移动到目标分组
        IconButton(onClick = onBatchMove) {
            Icon(Lucide.FolderInput, contentDescription = "批量移动", tint = TextPrimary)
        }
        IconButton(onClick = onSort) {
            Icon(Lucide.ArrowDownUp, contentDescription = "排序", tint = TextPrimary)
        }
        IconButton(onClick = onAdd) {
            Icon(Lucide.Plus, contentDescription = "添加订阅", tint = TextPrimary)
        }
    }
}

/** 多选态顶栏：已选计数 + 执行移动 + 退出。 */
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    canMove: Boolean,
    onMove: () -> Unit,
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
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onMove, enabled = canMove) {
            Text("移动到", color = if (canMove) Accent else TextTertiary, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = onCancel) {
            Icon(Lucide.X, contentDescription = "退出多选", tint = TextPrimary)
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
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) Lucide.ChevronDown else Lucide.ChevronRight,
            contentDescription = if (expanded) "折叠" else "展开",
            tint = TextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$feedCount 个订阅",
            color = TextTertiary,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onEdit) {
            Icon(
                Lucide.Pencil,
                contentDescription = "编辑分组",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
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
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Surface1,
        modifier = Modifier
            .fillMaxWidth()
            // 整行点击进「订阅源文章列表」（issue #51）；管理入口仍是行尾"⋯"
            .clickable(onClick = onClick),
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
                    tint = if (selected) Accent else TextTertiary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            FeedIcon(title = item.feed.title, iconUrl = item.feed.iconUrl, size = 32.dp, cornerRadius = 8.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.feed.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.feed.url.withoutScheme(),
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 类型标记：RSSHub 路由和常规 RSS 一眼区分
            if (item.feed.sourceType == FeedEntity.SOURCE_TYPE_RSSHUB) {
                Spacer(Modifier.width(6.dp))
                Surface(shape = RoundedCornerShape(50), color = Surface2) {
                    Text(
                        text = "RSSHub",
                        color = TextTertiary,
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
                        tint = TextSecondary,
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
        Surface(shape = RoundedCornerShape(50), color = Surface2) {
            Text(
                text = "已读",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        return
    }
    Surface(shape = RoundedCornerShape(50), color = Accent) {
        Text(
            text = count.coerceAtMost(999).toString(),
            color = OnAccent,
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
        color = Surface1,
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
                tint = Link,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "新建分组",
                color = Link,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MarkAllReadRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Lucide.CheckCheck,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "全部标记为已读",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
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
        containerColor = Surface1,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
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
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (group == target) {
                            Icon(
                                Lucide.Check,
                                contentDescription = null,
                                tint = Accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(target) }, enabled = target.isNotBlank()) {
                Text("移动", color = Accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextTertiary) }
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
        containerColor = Surface1,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text(placeholder, color = TextTertiary, style = MaterialTheme.typography.bodyMedium) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Surface2,
                    unfocusedContainerColor = Surface2,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(confirmText, color = Accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextTertiary)
            }
        },
    )
}

