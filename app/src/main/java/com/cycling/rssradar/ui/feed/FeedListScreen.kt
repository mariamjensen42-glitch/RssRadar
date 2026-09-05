package com.cycling.rssradar.ui.feed

import android.text.format.DateUtils
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.cycling.rssradar.core.ui.components.RadarImage
import com.cycling.rssradar.core.ui.components.pressScale
import com.cycling.rssradar.core.ui.theme.LocalReducedMotion
import com.cycling.rssradar.core.ui.theme.MotionTokens
import com.cycling.rssradar.core.data.db.ArticleEntity
import com.cycling.rssradar.core.data.db.ArticleWithFeed
import com.cycling.rssradar.core.data.store.ListDescMode
import com.cycling.rssradar.core.data.store.ListDisplayState
import com.cycling.rssradar.core.data.store.ListViewMode
import com.cycling.rssradar.core.model.MarkAsReadCondition
import com.cycling.rssradar.ui.theme.LocalListDisplay
import com.cycling.rssradar.ui.components.ArticleContextMenu
import com.cycling.rssradar.core.ui.components.AppSnackbarHost
import com.cycling.rssradar.ui.components.ArticleMenuActions
import com.cycling.rssradar.ui.components.articleMenuOffset
import com.cycling.rssradar.core.ui.components.FeedIcon
import com.cycling.rssradar.core.ui.components.FeedLetterTile
import com.cycling.rssradar.core.ui.components.OptionPickerSheet
import com.cycling.rssradar.core.ui.components.tabBarBottomClearance
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CheckCheck
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.FileUp
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.LayoutList
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Newspaper
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Rows3
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.SlidersHorizontal
import com.cycling.rssradar.core.ui.theme.radarColors
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt


@Composable
fun FeedListScreen(
    viewModel: FeedListViewModel,
    onOpenSearch: () -> Unit = {},
    onOpenArticle: (ArticleWithFeed) -> Unit = {},
    /** 空态「添加订阅源」直达入口（新用户第一分钟不该被卡在找入口上）。 */
    onAddFeed: () -> Unit = {},
    /** 新用户空态「导入 OPML」入口：跳订阅页（SAF 入口在订阅页顶栏菜单）。 */
    onOpenSubscriptions: () -> Unit = {},
) {
    // MVI 候选 C（ADR-0003）：单一 UiState 快照驱动渲染
    val uiState by viewModel.uiState.collectAsState()
    val groupOptions by viewModel.groupOptions.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val recommendationEnabled by viewModel.recommendationEnabled.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showGroupSheet by remember { mutableStateOf(false) }
    /** 批量标记已读条件弹层（#10）。 */
    var showMarkReadSheet by remember { mutableStateOf(false) }
    /** 列表视图模式选择弹层（列表/卡片/杂志/网格）。 */
    var showViewModeSheet by remember { mutableStateOf(false) }
    /** 视图模式是全局显示偏好（ListDisplayStore → CompositionLocal），这里读，VM 写。 */
    val viewMode = LocalListDisplay.current.viewMode
    val message = uiState.uiMessage

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onIntent(FeedListIntent.ConsumeMessage)
        }
    }

    // 「减少此类」撤销（ADR-0013）：Snackbar 期内可撤销，超时自动丢弃（降权保留）
    val pendingUndoReduce = uiState.pendingUndoReduceFeedId
    LaunchedEffect(pendingUndoReduce) {
        pendingUndoReduce?.let {
            val result = snackbarHostState.showSnackbar(
                message = "已减少此订阅源的推荐",
                actionLabel = "撤销",
                duration = SnackbarDuration.Short,
            )
            when (result) {
                SnackbarResult.ActionPerformed -> viewModel.onIntent(FeedListIntent.UndoReduceSuch)
                SnackbarResult.Dismissed -> viewModel.onIntent(FeedListIntent.DiscardUndoReduce)
            }
        }
    }

    // 删除撤销（issue #46）：Snackbar 期内可撤销，超时自动丢弃
    val pendingUndo = uiState.pendingUndoDelete
    LaunchedEffect(pendingUndo) {
        pendingUndo?.let { deleted ->
            val result = snackbarHostState.showSnackbar(
                message = "已删除「${deleted.title}」",
                actionLabel = "撤销",
                duration = SnackbarDuration.Short,
            )
            when (result) {
                SnackbarResult.ActionPerformed -> viewModel.onIntent(FeedListIntent.UndoDeleteArticle)
                SnackbarResult.Dismissed -> viewModel.onIntent(FeedListIntent.DiscardUndo)
            }
        }
    }

    // 分组筛选已下沉 DB 查询（issue #74）：ViewModel 返回的页本身就是按分组过滤后的
    // 分页结果，不再对已加载页做内存过滤（旧做法会让首屏大量留白、hasMore 语义错乱）
    val currentList = uiState.articles

    Scaffold(
        containerColor = radarColors().bgRoot,
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            FeedListTopBar(
                onOpenSearch = onOpenSearch,
                onOpenFilter = { showGroupSheet = true },
                onMarkAllRead = { showMarkReadSheet = true },
                onOpenViewMode = { showViewModeSheet = true },
                viewMode = viewMode,
                // 分组或内容类型任一生效即亮点（内容类型已收进筛选弹层，首页不再常驻一行 chip）
                filterActive = uiState.selectedGroup != null || uiState.selectedContentType != ContentTypeFilter.All,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            FeedListTabRow(
                selected = uiState.selectedTab,
                unreadCount = unreadCount,
                // 推荐流开关（ADR-0013）：关掉就不渲染「推荐」tab
                tabs = if (recommendationEnabled) FeedTab.entries else FeedTab.entries.filter { it != FeedTab.Recommended },
                onSelect = { viewModel.onIntent(FeedListIntent.SelectTab(it)) },
            )
            // 刷新进度（真机反馈缺口）：708 源全量刷新可达数十分钟，
            // 一个孤零零的转圈分不清「在跑」还是「卡死」——细进度条 + 计数，不抢一整行
            if (uiState.isRefreshing && uiState.refreshTotal > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { uiState.refreshDone.toFloat() / uiState.refreshTotal },
                        trackColor = radarColors().surface2,
                        modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${uiState.refreshDone}/${uiState.refreshTotal}",
                        style = MaterialTheme.typography.labelSmall,
                        color = radarColors().accent,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.onIntent(FeedListIntent.Refresh) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (uiState.isRanking) {
                    RecommendationLoading(modifier = Modifier.fillMaxSize())
                } else if (currentList.isEmpty()) {
                    EmptyState(
                        selectedTab = uiState.selectedTab,
                        selectedContentType = uiState.selectedContentType,
                        partitionEmpty = uiState.partitionEmpty,
                        onAddFeed = onAddFeed,
                        onOpenSubscriptions = onOpenSubscriptions,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ArticleCardList(
                        articles = currentList,
                        onArticleClick = { item ->
                            viewModel.onIntent(FeedListIntent.MarkRead(item.article.id))
                            onOpenArticle(item)
                        },
                        onToggleRead = { id, read ->
                            viewModel.onIntent(FeedListIntent.SetRead(id, read))
                        },
                        onToggleStarred = { id ->
                            viewModel.onIntent(FeedListIntent.ToggleStarred(id))
                        },
                        onToggleBookmarked = { id ->
                            viewModel.onIntent(FeedListIntent.ToggleBookmarked(id))
                        },
                        onDelete = { id ->
                            viewModel.onIntent(FeedListIntent.DeleteArticle(id))
                        },
                        // 推荐 tab 才有「减少此类」：只有这里的排序由画像决定
                        onReduceSuch = if (uiState.selectedTab == FeedTab.Recommended) {
                            { id -> viewModel.onIntent(FeedListIntent.ReduceSuch(id)) }
                        } else {
                            null
                        },
                        // 各 tab 均分页；滚动到底自动加载下一页
                        onScrolledToEnd = { viewModel.onIntent(FeedListIntent.LoadMore) },
                        markReadPassed = { ids ->
                            viewModel.onIntent(FeedListIntent.MarkReadPassed(ids))
                        },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (uiState.isLoadingMore) {
                            CircularProgressIndicator(
                                color = radarColors().accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showGroupSheet) {
        GroupFilterSheet(
            groups = groupOptions,
            selected = uiState.selectedGroup,
            contentType = uiState.selectedContentType,
            onSelectContentType = { viewModel.onIntent(FeedListIntent.SelectContentType(it)) },
            onSelect = { group ->
                viewModel.onIntent(FeedListIntent.SelectGroup(group))
                showGroupSheet = false
            },
            onDismiss = { showGroupSheet = false },
        )
    }

    // 列表视图模式（列表/卡片/杂志/网格）：全局偏好，切换后所有文章列表即改即见
    if (showViewModeSheet) {
        OptionPickerSheet(
            title = "视图模式",
            options = ListViewMode.entries.toList(),
            selected = viewMode,
            label = { it.label },
            subtitle = { mode ->
                when (mode) {
                    ListViewMode.LIST -> "单列紧凑，标题 + 摘要"
                    ListViewMode.CARD -> "卡片排版，右侧缩略图"
                    ListViewMode.MAGAZINE -> "图文混排，首篇大图突出"
                    ListViewMode.GRID -> "多列网格，按屏幕宽度自适应"
                }
            },
            onSelect = { mode -> viewModel.onIntent(FeedListIntent.SetViewMode(mode)) },
            onDismiss = { showViewModeSheet = false },
        )
    }

    // 批量标记已读（#10）：选条件后一次性写库，数字由 DAO 的真实影响行数汇报
    if (showMarkReadSheet) {
        OptionPickerSheet(
            title = "标记已读",
            options = MarkAsReadCondition.entries.toList(),
            selected = null,
            label = { it.label },
            subtitle = { condition ->
                when (condition) {
                    MarkAsReadCondition.ALL -> "把全部文章标为已读"
                    else -> "把 ${condition.label}发布的未读文章标为已读"
                }
            },
            onSelect = { condition -> viewModel.onIntent(FeedListIntent.MarkAllRead(condition)) },
            onDismiss = { showMarkReadSheet = false },
        )
    }
}

@Composable
private fun FeedListTopBar(
    onOpenSearch: () -> Unit,
    onOpenFilter: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenViewMode: () -> Unit,
    viewMode: ListViewMode,
    filterActive: Boolean,
) {
    // 顶栏只留高频的搜索，其余低频操作（标记已读/视图模式/分组筛选）收进溢出菜单：
    // 4 个无标签图标并排的可发现性差，新用户不可能逐个试
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "RssRadar",
            color = radarColors().textPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenSearch) {
            Icon(Lucide.Search, contentDescription = "搜索", tint = radarColors().textPrimary)
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
                    text = { Text("标记已读") },
                    leadingIcon = { Icon(Lucide.CheckCheck, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onMarkAllRead()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            buildString {
                                append("视图模式 · ")
                                append(
                                    when (viewMode) {
                                        ListViewMode.LIST -> "列表"
                                        ListViewMode.CARD -> "卡片"
                                        ListViewMode.MAGAZINE -> "杂志"
                                        ListViewMode.GRID -> "网格"
                                    },
                                )
                            },
                        )
                    },
                    leadingIcon = { Icon(Lucide.LayoutGrid, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onOpenViewMode()
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (filterActive) "分组筛选 · 已启用" else "分组筛选") },
                    leadingIcon = { Icon(Lucide.SlidersHorizontal, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onOpenFilter()
                    },
                )
            }
        }
    }
}

@Composable
private fun FeedListTabRow(
    selected: FeedTab,
    unreadCount: Int,
    /** 实际渲染的 tab（推荐流可关，ADR-0013）。 */
    tabs: List<FeedTab> = FeedTab.entries,
    onSelect: (FeedTab) -> Unit,
) {
    // 5 个 tab 在 360dp 窄屏上约需 380dp，固定 Row 会把末尾 chip 裁掉（看着像少了一个 tab）。
    // 改成横向滚动，只在还能往右滚时叠一层右侧渐隐，提示后面还有内容。
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEach { tab ->
                val label = when (tab) {
                    FeedTab.All -> "全部"
                    FeedTab.Unread -> "未读 $unreadCount"
                    FeedTab.Starred -> "收藏"
                    FeedTab.Bookmarked -> "稍后读"
                    FeedTab.Recommended -> "推荐"
                }
                FilterChip(
                    label = label,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                )
            }
        }
        if (scrollState.canScrollForward) {
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(28.dp)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, radarColors().bgRoot))),
                )
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    // 轻量化选中样式：选中 = 低透明度 accent 底 + accent 文字（不再整块实色填充），
    // 未选中 = 透明底 + 次级文字，仅留可点区域。整体视觉重量比旧胶囊低一档。
    val bg = if (selected) radarColors().accent.copy(alpha = 0.16f) else Color.Transparent
    val fg = if (selected) radarColors().accent else radarColors().textSecondary
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/**
 * 顶栏筛选底部弹层：分组 + 内容类型（图片/视频/音频）。
 * 内容分区原本常驻首页一行 chip（issue #75 PRD 方案 C），低频操作不值得占一行，
 * 现收进本弹层；分组或内容类型非默认时顶栏 SlidersHorizontal 亮小红点。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupFilterSheet(
    groups: List<String>,
    selected: String?,
    contentType: ContentTypeFilter,
    onSelectContentType: (ContentTypeFilter) -> Unit,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = radarColors().surface1) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "筛选",
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            // 内容类型：一排轻量 chip，即时生效且不关弹层（与分组列表「选中即关」区分：
            // 这里是多选前的快速试切，关弹层交给用户下滑手势）
            Text(
                text = "内容类型",
                color = radarColors().textSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ContentTypeFilter.entries.forEach { type ->
                    FilterChip(
                        label = type.label,
                        selected = type == contentType,
                        onClick = { onSelectContentType(type) },
                    )
                }
            }
            Text(
                text = "分组",
                color = radarColors().textSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                item {
                    GroupOption(
                        label = "全部",
                        selected = selected == null,
                        onClick = { onSelect(null) },
                    )
                }
                items(groups) { group ->
                    GroupOption(
                        label = group,
                        selected = selected == group,
                        onClick = { onSelect(group) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) radarColors().accent else radarColors().textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Lucide.Check,
                contentDescription = "已选",
                tint = radarColors().accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleCardList(
    articles: List<ArticleWithFeed>,
    onArticleClick: (ArticleWithFeed) -> Unit,
    onToggleRead: (Long, Boolean) -> Unit,
    onToggleStarred: (Long) -> Unit,
    onToggleBookmarked: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onScrolledToEnd: () -> Unit,
    /**
     * 「减少此类」（ADR-0013）：非空时卡片的上下文菜单出现该动作。
     * 只有推荐流传——其余列表的排序与画像无关，负反馈无处落地。
     */
    onReduceSuch: ((Long) -> Unit)? = null,
    // 底部让位：tab 屏传悬浮 TabBar 让位；无 TabBar 的页面传普通间距
    bottomPadding: Dp = tabBarBottomClearance(),
    // 单源页强制隐藏订阅源名称（同源重复是噪音）；null = 跟随全局配置
    showFeedName: Boolean? = null,
    /** 视图模式覆盖（单源页用）：null = 跟随全局；非 null 时无视全局模式。 */
    viewModeOverride: ListViewMode? = null,
    /**
     * 滚动自动标记已读（#11）：上报"已滚出视口顶部"的文章 id 批次。
     * 由 [LocalListDisplay] 的开关决定是否启用，关闭时本回调不会被调用。
     */
    markReadPassed: (List<Long>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val display = LocalListDisplay.current.let {
        it.copy(
            showFeedName = showFeedName ?: it.showFeedName,
            viewMode = viewModeOverride ?: it.viewMode,
        )
    }
    // 网格模式是独立容器（LazyVerticalGrid），走自己的渲染分支；粘性日期头与
    // 滚动标已读都是 LazyColumn 槽位逻辑，网格里不适用（与图片画廊同规则）。
    if (display.viewMode == ListViewMode.GRID) {
        ArticleAdaptiveGrid(
            articles = articles,
            onArticleClick = onArticleClick,
            onScrolledToEnd = onScrolledToEnd,
            bottomPadding = bottomPadding,
            onReduceSuch = onReduceSuch,
            onToggleRead = onToggleRead,
            onToggleStarred = onToggleStarred,
            onToggleBookmarked = onToggleBookmarked,
            onDelete = onDelete,
            modifier = modifier,
        )
        return
    }
    // 列表模式 = 单列紧凑：固定无缩略图（摘要保留），其余显示项沿用用户设置
    val effective = when (display.viewMode) {
        ListViewMode.LIST -> display.copy(showThumbnail = false)
        else -> display
    }
    val listState = rememberLazyListState()
    // 删除淡出（docs/motion.md #4）：数万条列表只做 fadeOut，placement / fadeIn 关闭
    // ——低端机上 placement 是帧率杀手。reduce-motion 时 fadeOut 也关（红线）。
    val reducedMotion = LocalReducedMotion.current
    val removeFadeSpec: FiniteAnimationSpec<Float>? =
        if (reducedMotion) null else tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard)
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) onScrolledToEnd()
    }
    // 分组必须在 LazyColumn builder 外算：builder lambda 不是 composable 上下文，
    // remember 放里面编不过。不开启粘性头时不算，零开销。
    val dayGroups = if (display.stickyDateHeader) {
        remember(articles) { dayGroups(articles) }
    } else {
        emptyList()
    }
    // 滚动自动标记已读（#11）：槽位表构建与「滚过视口顶 = 已读」判定是纯函数
    // （scrollSlots/passedUnreadIds，见 PagedSnapshot.kt），此处只做接线。
    val slotIds: List<Long?> = if (display.stickyDateHeader) {
        remember(dayGroups) { scrollSlots(articles, stickyDateHeader = true, groups = dayGroups) }
    } else {
        remember(articles) { scrollSlots(articles, stickyDateHeader = false) }
    }
    val unreadIds = remember(articles) {
        articles.filter { !it.article.isRead }.map { it.article.id }.toSet()
    }
    LaunchedEffect(listState, display.markReadOnScroll, slotIds, unreadIds, markReadPassed) {
        if (!display.markReadOnScroll) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstVisible ->
                val passed = passedUnreadIds(slotIds, firstVisible, unreadIds)
                if (passed.isNotEmpty()) markReadPassed(passed)
            }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        // 底部让位：tab 屏让开悬浮 TabBar，最后一条文章能完整滚出胶囊
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 杂志模式：首篇大图突出（hero 跟随列表首项，翻页后仍是当前加载段的第一篇）
        val heroId = if (effective.viewMode == ListViewMode.MAGAZINE) articles.firstOrNull()?.article?.id else null
        if (display.stickyDateHeader) {
            // 粘性日期头（issue #56）：按自然日分组，吸附在顶部。
            // 只做视觉分组，不改变排序与分页；列表本身已按 publishedAt DESC 排序。
            dayGroups.forEach { group ->
                // stickyHeader 是 LazyListScope 接口成员（foundation 1.10+，真值表已核），
                // DSL 内直接调用，不可 import
                stickyHeader(key = "date-${group.key}") {
                    StickyDateHeader(group.label)
                }
                items(group.items, key = { it.article.id }) { item ->
                    Box(
                        modifier = Modifier.animateItem(
                            fadeInSpec = null,
                            placementSpec = null,
                            fadeOutSpec = removeFadeSpec,
                        ),
                    ) {
                        ArticleListItem(
                            item = item,
                            display = effective,
                            hero = item.article.id == heroId,
                            onArticleClick = onArticleClick,
                            onToggleRead = onToggleRead,
                            onToggleStarred = onToggleStarred,
                            onToggleBookmarked = onToggleBookmarked,
                            onDelete = onDelete,
                            onReduceSuch = onReduceSuch,
                        )
                    }
                }
            }
        } else {
            items(articles, key = { it.article.id }) { item ->
                Box(
                    modifier = Modifier.animateItem(
                        fadeInSpec = null,
                        placementSpec = null,
                        fadeOutSpec = removeFadeSpec,
                    ),
                ) {
                    ArticleListItem(
                        item = item,
                        display = effective,
                        hero = item.article.id == heroId,
                        onArticleClick = onArticleClick,
                        onToggleRead = onToggleRead,
                        onToggleStarred = onToggleStarred,
                        onToggleBookmarked = onToggleBookmarked,
                        onDelete = onDelete,
                        onReduceSuch = onReduceSuch,
                    )
                }
            }
        }
    }
}

/**
 * 单篇文章项按视图模式分发：列表/卡片走 [SwipeableArticleCard]（带滑动手势），
 * 杂志走图文混排卡（首篇 hero 大图）。长按上下文菜单在杂志卡里保持一致。
 */
@Composable
private fun ArticleListItem(
    item: ArticleWithFeed,
    display: ListDisplayState,
    hero: Boolean,
    onArticleClick: (ArticleWithFeed) -> Unit,
    onToggleRead: (Long, Boolean) -> Unit,
    onToggleStarred: (Long) -> Unit,
    onToggleBookmarked: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onReduceSuch: ((Long) -> Unit)?,
) {
    if (display.viewMode != ListViewMode.MAGAZINE) {
        SwipeableArticleCard(
            item = item,
            display = display,
            onClick = { onArticleClick(item) },
            onToggleRead = { onToggleRead(item.article.id, !item.article.isRead) },
            onToggleStarred = { onToggleStarred(item.article.id) },
            onToggleBookmarked = { onToggleBookmarked(item.article.id) },
            onDelete = { onDelete(item.article.id) },
            onReduceSuch = onReduceSuch?.let { reduce -> { reduce(item.article.id) } },
        )
        return
    }
    ArticleMenuBox(
        itemCount = if (onReduceSuch != null) 8 else 7,
        actions = ArticleMenuActions(
            isRead = item.article.isRead,
            isStarred = item.article.isStarred,
            isBookmarked = item.article.isBookmarked,
            link = item.article.link,
            onToggleRead = { onToggleRead(item.article.id, !item.article.isRead) },
            onToggleStarred = { onToggleStarred(item.article.id) },
            onToggleBookmarked = { onToggleBookmarked(item.article.id) },
            onDelete = { onDelete(item.article.id) },
            onReduceSuch = onReduceSuch?.let { reduce -> { reduce(item.article.id) } },
        ),
    ) { onLongClick ->
        if (hero) {
            MagazineHeroCard(item = item, onClick = { onArticleClick(item) }, onLongClick = onLongClick)
        } else {
            MagazineCard(item = item, onClick = { onArticleClick(item) }, onLongClick = onLongClick)
        }
    }
}

/**
 * 杂志/网格卡的通用长按菜单容器：负责按压缩点定位与菜单弹出，
 * 内容卡通过 [content] 拿到 onLongClick 挂进自己的 combinedClickable。
 */
@Composable
private fun ArticleMenuBox(
    itemCount: Int,
    actions: ArticleMenuActions,
    content: @Composable (() -> Unit) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    // 菜单偏移：贴着长按手指出现（与 ArticleCard 同一套预判逻辑）
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var pressPos by remember { mutableStateOf(Offset.Zero) }
    var cardTopInWindowPx by remember { mutableStateOf(0f) }
    var cardHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val windowHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    Box {
        Box(
            modifier = Modifier
                .onGloballyPositioned {
                    cardTopInWindowPx = it.localToWindow(Offset.Zero).y
                    cardHeightPx = it.size.height
                }
                // 旁观手势：只记录按下坐标，不消费事件，长按仍由内容卡的 clickable 触发
                .pointerInput(Unit) {
                    awaitEachGesture {
                        pressPos = awaitFirstDown(requireUnconsumed = false).position
                    }
                },
        ) {
            content {
                menuOffset = articleMenuOffset(
                    pressPos = pressPos,
                    cardTopInWindowPx = cardTopInWindowPx,
                    cardHeightPx = cardHeightPx,
                    menuItemCount = itemCount,
                    windowHeightPx = windowHeightPx,
                    density = density,
                )
                menuExpanded = true
            }
        }
        ArticleContextMenu(
            expanded = menuExpanded,
            offset = menuOffset,
            actions = actions,
            onDismiss = { menuExpanded = false },
        )
    }
}

/** 杂志模式首篇：16:9 大图 + 标题压图（黑渐变 scrim 保证可读）；无封面退化为大字排版。 */
@Composable
private fun MagazineHeroCard(
    item: ArticleWithFeed,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = radarColors().surface1,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(radarColors().surface2),
            ) {
                // RadarImage 对 null/加载失败自带图标占位兜底
                RadarImage(
                    url = item.article.coverUrl,
                    contentDescription = item.article.title,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!item.article.isRead) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .size(8.dp)
                            .background(radarColors().accent, RoundedCornerShape(50)),
                    )
                }
                // 标题压底：白字 + scrim，来源行让读者知道重点来自哪个源
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Column {
                        Text(
                            text = item.feedTitle,
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.article.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            item.article.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                Text(
                    text = summary,
                    color = radarColors().textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** 杂志模式常规项：封面横图置顶（有则显示），标题 + 摘要混排其下。 */
@Composable
private fun MagazineCard(
    item: ArticleWithFeed,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = radarColors().surface1,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            item.article.coverUrl?.takeIf { it.isNotBlank() }?.let { cover ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(radarColors().surface2),
                ) {
                    RadarImage(
                        url = cover,
                        contentDescription = item.article.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                    when (item.article.mediaKind) {
                        ArticleEntity.MEDIA_KIND_VIDEO ->
                            MediaBadge(Lucide.Play, "视频", Modifier.align(Alignment.BottomEnd).padding(6.dp))
                        ArticleEntity.MEDIA_KIND_AUDIO ->
                            MediaBadge(Lucide.Music, "音频", Modifier.align(Alignment.BottomEnd).padding(6.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                UnreadDot(visible = !item.article.isRead)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = item.feedTitle,
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                item.article.publishedAt?.let { ts ->
                    Text(
                        text = DateUtils.getRelativeTimeSpanString(ts).toString(),
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.article.title,
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.article.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = summary,
                    color = radarColors().textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 网格模式：GridCells.Adaptive 按可用宽度自动定列数（手机两列、平板/横屏更多），
 * 分页沿用滚近底部触发。粘性日期头/滚动标已读均为列表容器逻辑，网格不适用。
 */
@Composable
private fun ArticleAdaptiveGrid(
    articles: List<ArticleWithFeed>,
    onArticleClick: (ArticleWithFeed) -> Unit,
    onScrolledToEnd: () -> Unit,
    bottomPadding: Dp,
    onReduceSuch: ((Long) -> Unit)?,
    onToggleRead: (Long, Boolean) -> Unit,
    onToggleStarred: (Long) -> Unit,
    onToggleBookmarked: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyGridState()
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) onScrolledToEnd()
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(items = articles, key = { it.article.id }) { item ->
            ArticleMenuBox(
                itemCount = if (onReduceSuch != null) 8 else 7,
                actions = ArticleMenuActions(
                    isRead = item.article.isRead,
                    isStarred = item.article.isStarred,
                    isBookmarked = item.article.isBookmarked,
                    link = item.article.link,
                    onToggleRead = { onToggleRead(item.article.id, !item.article.isRead) },
                    onToggleStarred = { onToggleStarred(item.article.id) },
                    onToggleBookmarked = { onToggleBookmarked(item.article.id) },
                    onDelete = { onDelete(item.article.id) },
                    onReduceSuch = onReduceSuch?.let { reduce -> { reduce(item.article.id) } },
                ),
            ) { onLongClick ->
                GridArticleCard(
                    item = item,
                    onClick = { onArticleClick(item) },
                    onLongClick = onLongClick,
                )
            }
        }
    }
}

/** 网格模式单元格：4:3 封面 + 标题 + 来源/日期行。 */
@Composable
private fun GridArticleCard(
    item: ArticleWithFeed,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = radarColors().surface1,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(radarColors().surface2),
            ) {
                RadarImage(
                    url = item.article.coverUrl,
                    contentDescription = item.article.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // 无封面时用订阅源字母色块铺满，替代纯灰底（灰块观感像加载失败）
                if (item.article.coverUrl.isNullOrBlank()) {
                    FeedLetterTile(
                        title = item.feedTitle,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                if (!item.article.isRead) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(8.dp)
                            .background(radarColors().accent, RoundedCornerShape(50)),
                    )
                }
                when (item.article.mediaKind) {
                    ArticleEntity.MEDIA_KIND_VIDEO ->
                        MediaBadge(Lucide.Play, "视频", Modifier.align(Alignment.BottomEnd).padding(6.dp))
                    ArticleEntity.MEDIA_KIND_AUDIO ->
                        MediaBadge(Lucide.Music, "音频", Modifier.align(Alignment.BottomEnd).padding(6.dp))
                }
            }
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = item.article.title,
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(item.feedTitle)
                        item.article.publishedAt?.let { ts ->
                            append(" · ")
                            append(DateUtils.getRelativeTimeSpanString(ts).toString())
                        }
                    },
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 距列表尾部还剩这么多项时预加载下一页。 */
internal const val LOAD_MORE_THRESHOLD = 5

/** 粘性日期头：不透明底色（页面底色）保证滚动时干净压住下方卡片。 */
@Composable
private fun StickyDateHeader(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(radarColors().bgRoot)
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            color = radarColors().textTertiary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * 列表手势：右滑收藏 / 左滑切换已读——RSS 阅读器的肌肉记忆。
 *
 * 两个动作都不该让卡片从列表里消失（标已读后它只是变灰，收藏后只是多颗星），
 * 所以走「落定即执行 + 弹回」的路子。
 *
 * 刻意不用 M3 SwipeToDismissBox：它的手势仲裁是「哪个轴先过 touch slop 谁赢」，
 * 垂直滚动时手指的横向漂移经常抢到第一拍，卡片被误判成横滑并触发收藏/已读。
 * 这里改为自研手势（[Modifier.pointerInput]）：横向位移不仅要过 slop，还要
 * 显著大于纵向（1.5 倍）才接管；其余手势一律不消费，完整交还给纵向滚动。
 */
@Composable
private fun SwipeableArticleCard(
    item: ArticleWithFeed,
    display: ListDisplayState,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleStarred: () -> Unit,
    onToggleBookmarked: () -> Unit,
    onDelete: () -> Unit,
    onReduceSuch: (() -> Unit)? = null,
) {
    // 回调每次重组都是新 lambda，而手势协程是长生命周期（key=Unit）——
    // 用 UpdatedState 保证拿到最新回调。
    val currentToggleRead by rememberUpdatedState(onToggleRead)
    val currentToggleStarred by rememberUpdatedState(onToggleStarred)

    val density = LocalDensity.current
    val maxOffsetPx = with(density) { 120.dp.toPx() } // 滑动位移上限
    val triggerPx = with(density) { 72.dp.toPx() } // 松手触发动作的距离阈值
    val touchSlopPx = LocalViewConfiguration.current.touchSlop
    // 高速轻扫阈值（px/s）：位移不够但速度够快也算有意滑动（fling 手感）
    val flingVelocityPx = with(density) { 1200.dp.toPx() }

    // 卡片横向偏移：拖动中 snapTo 跟手，松手 animateTo(0) 弹回
    val offsetX = remember { Animatable(0f) }
    var swipeDir by remember { mutableStateOf<SwipeDirection?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxWidth()) {
        // 背景：滑动中按方向露出动作提示
        SwipeActionBackground(
            modifier = Modifier.matchParentSize(), // 背景不参与测量，跟随卡片尺寸
            direction = swipeDir,
            isRead = item.article.isRead,
            isStarred = item.article.isStarred,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(maxOffsetPx, triggerPx, touchSlopPx, flingVelocityPx) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        tracker.resetTracking()
                        var total = Offset.Zero
                        var engaged = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.firstOrNull { it.pressed }
                            if (pressed == null) break // 全部指针抬起/取消
                            // 手动差分而非 positionChange()：旁观阶段不消费事件，
                            // 需要的是「忽略消费状态」的位移（等价 positionChangeIgnoreConsumed）
                            val delta = pressed.position - pressed.previousPosition
                            total += delta
                            if (!engaged && total.getDistance() > touchSlopPx) {
                                // 方向仲裁（见函数注释）：横向需 1.5 倍优势
                                engaged = abs(total.x) > abs(total.y) * 1.5f
                            }
                            if (engaged) {
                                // 接管后消费全部事件：纵向滚动停止，clickable 也因
                                // move 被消费而取消，不会误触发点击
                                event.changes.forEach { it.consume() }
                                val next = (offsetX.value + delta.x)
                                    .coerceIn(-maxOffsetPx, maxOffsetPx)
                                swipeDir =
                                    if (next >= 0f) SwipeDirection.RIGHT else SwipeDirection.LEFT
                                tracker.addPosition(pressed.uptimeMillis, pressed.position)
                                scope.launch { offsetX.snapTo(next) }
                            }
                        }
                        if (engaged) {
                            val vx = tracker.calculateVelocity().x
                            val offset = offsetX.value
                            when {
                                offset > triggerPx || (offset > 0 && vx > flingVelocityPx) ->
                                    currentToggleStarred() // 右滑：收藏
                                offset < -triggerPx || (offset < 0 && vx < -flingVelocityPx) ->
                                    currentToggleRead() // 左滑：切换已读
                            }
                            swipeDir = null
                            scope.launch {
                                offsetX.animateTo(
                                    0f,
                                    spring(stiffness = Spring.StiffnessMediumLow),
                                )
                            }
                        }
                    }
                },
        ) {
            ArticleCard(
                item = item,
                display = display,
                onClick = onClick,
                onToggleRead = onToggleRead,
                onToggleStarred = onToggleStarred,
                onToggleBookmarked = onToggleBookmarked,
                onDelete = onDelete,
                onReduceSuch = onReduceSuch,
            )
        }
    }
}

/** 横向滑动方向：决定背景从哪侧露出。 */
private enum class SwipeDirection { LEFT, RIGHT }

/** 滑动时露出的背景：图标 + 文案说明会发生什么（文案随当前状态变，避免猜）。 */
@Composable
private fun SwipeActionBackground(
    modifier: Modifier = Modifier,
    direction: SwipeDirection?,
    isRead: Boolean,
    isStarred: Boolean,
) {
    val (label, icon, tint) = when (direction) {
        SwipeDirection.RIGHT ->
            Triple(
                if (isStarred) "取消收藏" else "收藏",
                Lucide.Star,
                radarColors().accent,
            )

        SwipeDirection.LEFT ->
            Triple(
                if (isRead) "标未读" else "标已读",
                Lucide.Check,
                radarColors().textSecondary,
            )

        null -> return
    }
    // 卡片往左移 → 背景右侧露出 → 内容靠右；反之靠左
    Box(
        modifier = modifier.padding(horizontal = 24.dp),
        contentAlignment = if (direction == SwipeDirection.LEFT) {
            Alignment.CenterEnd
        } else {
            Alignment.CenterStart
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = tint, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleCard(
    item: ArticleWithFeed,
    display: ListDisplayState,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleStarred: () -> Unit,
    onToggleBookmarked: () -> Unit,
    onDelete: () -> Unit,
    /** 「减少此类」（ADR-0013）：非空时上下文菜单出现该动作。 */
    onReduceSuch: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    // 菜单偏移：贴着长按手指出现（手指下方放不下时翻到上方、底边贴手指），
    // 方向预判逻辑在 articleMenuOffset（绕开 M3 翻转时偏移符号反转的坑）
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var cardTopInWindowPx by remember { mutableStateOf(0f) }
    var cardHeightPx by remember { mutableStateOf(0) }
    var pressPos by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val windowHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    // 已读弱化（issue #56）：开关开启时已读卡片降弱色；未读卡片永不因此改变
    val dimmed = display.dimRead && item.article.isRead
    val titleColor = if (dimmed) radarColors().textTertiary else radarColors().textPrimary
    val descColor = if (dimmed) radarColors().textTertiary else radarColors().textSecondary
    // 按压缩放（docs/motion.md #2）：source 与 combinedClickable 共用同一实例
    val interactionSource = remember { MutableInteractionSource() }
    Box {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = radarColors().surface1,
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interactionSource)
                .onGloballyPositioned {
                    cardTopInWindowPx = it.localToWindow(Offset.Zero).y
                    cardHeightPx = it.size.height
                }
                .clip(RoundedCornerShape(14.dp))
                // 旁观手势：只记录按下坐标，不消费事件，长按仍由 combinedClickable 触发
                .pointerInput(Unit) {
                    awaitEachGesture {
                        pressPos = awaitFirstDown(requireUnconsumed = false).position
                    }
                }
                .combinedClickable(
                    interactionSource = interactionSource,
                    onClick = onClick,
                    onLongClick = {
                        menuOffset = articleMenuOffset(
                            pressPos = pressPos,
                            cardTopInWindowPx = cardTopInWindowPx,
                            cardHeightPx = cardHeightPx,
                            menuItemCount = if (onReduceSuch != null) 8 else 7,
                            windowHeightPx = windowHeightPx,
                            density = density,
                        )
                        menuExpanded = true
                    },
                ),
        ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UnreadDot(visible = !item.article.isRead)
                if (display.showFeedIcon) {
                    Spacer(Modifier.width(6.dp))
                    FeedIcon(title = item.feedTitle, iconUrl = item.feedIconUrl, size = 18.dp, cornerRadius = 5.dp)
                }
                if (display.showFeedName) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = item.feedTitle,
                        color = radarColors().textPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (display.showDate) {
                    if (!display.showFeedName) Spacer(Modifier.weight(1f))
                    item.article.publishedAt?.let { ts ->
                        Text(
                            text = DateUtils.getRelativeTimeSpanString(ts).toString(),
                            color = radarColors().textTertiary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.article.title,
                        color = titleColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (display.descMode != ListDescMode.NONE) {
                        item.article.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = summary,
                                color = descColor,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = display.descMode.lines,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                // 无图无媒体不给灰占位：占位块无信息量，连续无图卡片整屏灰块纯视觉噪音
                if (display.showThumbnail &&
                    (item.article.mediaKind != ArticleEntity.MEDIA_KIND_NONE ||
                        !item.article.coverUrl.isNullOrBlank())
                ) {
                    Spacer(Modifier.width(10.dp))
                    CoverThumb(
                        url = item.article.coverUrl?.takeIf { it.isNotBlank() },
                        mediaKind = item.article.mediaKind,
                        feedTitle = item.feedTitle,
                    )
                } else if (item.article.mediaKind != ArticleEntity.MEDIA_KIND_NONE) {
                    // 无缩略图的音视频条目：给个明确的类型标识，别让用户猜点开是什么
                    Spacer(Modifier.width(10.dp))
                    MediaKindChip(kind = item.article.mediaKind)
                }
            }
        }
        }

        // 长按上下文菜单（issue #46），出现在长按手指处
        ArticleContextMenu(
            expanded = menuExpanded,
            offset = menuOffset,
            actions = ArticleMenuActions(
                isRead = item.article.isRead,
                isStarred = item.article.isStarred,
                isBookmarked = item.article.isBookmarked,
                link = item.article.link,
                onToggleRead = onToggleRead,
                onToggleStarred = onToggleStarred,
                onToggleBookmarked = onToggleBookmarked,
                onDelete = onDelete,
                onReduceSuch = onReduceSuch,
            ),
            onDismiss = { menuExpanded = false },
        )
    }
}

/**
 * 列表封面缩略图：统一 96×72（4:3），ContentScale.Crop 居中裁剪不拉伸；
 * 无封面画 radarColors().surface2 + Image 图标占位。固定尺寸让 Coil 免读原图尺寸、按目标大小解码，
 * LazyColumn 滚动开销最小；AsyncImage 无子组合，比 SubcomposeAsyncImage 更轻。
 * 音视频条目（ADR-0014）在角上加播放/音频角标。
 */
@Composable
private fun CoverThumb(url: String?, mediaKind: Int = ArticleEntity.MEDIA_KIND_NONE, feedTitle: String = "") {
    Box {
        Box(
            modifier = Modifier
                .size(width = 96.dp, height = 72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(radarColors().surface2),
        ) {
            if (url != null) {
                RadarImage(
                    url = url,
                    contentDescription = "封面缩略图",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // 有媒体角标但无封面图：字母色块占位，与网格口径一致
                FeedLetterTile(title = feedTitle, modifier = Modifier.fillMaxSize())
            }
        }
        when (mediaKind) {
            ArticleEntity.MEDIA_KIND_VIDEO ->
                MediaBadge(Lucide.Play, "视频", Modifier.align(Alignment.BottomEnd).padding(4.dp))
            ArticleEntity.MEDIA_KIND_AUDIO ->
                MediaBadge(Lucide.Music, "音频", Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
    }
}

/** 缩略图角上的媒体种类角标：小圆片 + 图标。align 作用域由调用方的 Box 提供。 */
@Composable
private fun MediaBadge(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.55f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = label, tint = radarColors().onAccent, modifier = Modifier.size(10.dp))
        }
    }
}

/** 无缩略图时的音视频类型标识：图标 + 文字，贴标题列右侧。 */
@Composable
private fun MediaKindChip(kind: Int) {
    val (icon, label) = when (kind) {
        ArticleEntity.MEDIA_KIND_VIDEO -> Lucide.Play to "视频"
        else -> Lucide.Music to "音频"
    }
    Surface(shape = RoundedCornerShape(50), color = radarColors().surface2) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = radarColors().accent, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = radarColors().accent, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * 图片类订阅源的画廊视图（ADR-0014）：两列方图网格，标题压在图上。
 * 只改列表形态，交互仍走文章详情（媒体/大图查看不内嵌，遵守媒体占位卡词条）。
 * 分页沿用 ArticleCardList 的滚近底部触发；无粘性日期头（网格里没有它的一席之地）。
 */
@Composable
internal fun ImageGalleryGrid(
    articles: List<ArticleWithFeed>,
    onArticleClick: (ArticleWithFeed) -> Unit,
    onScrolledToEnd: () -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyGridState()
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) onScrolledToEnd()
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(items = articles, key = { it.article.id }) { item ->
            ImageGalleryCard(item = item, onClick = { onArticleClick(item) })
        }
    }
}

@Composable
private fun ImageGalleryCard(item: ArticleWithFeed, onClick: () -> Unit) {
    // 按压缩放（docs/motion.md #2）
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pressScale(interactionSource)
            .clip(RoundedCornerShape(14.dp))
            .background(radarColors().surface2)
            .clickable(interactionSource = interactionSource, onClick = onClick),
    ) {
        RadarImage(
            url = item.article.coverUrl?.takeIf { it.isNotBlank() },
            contentDescription = item.article.title,
            modifier = Modifier.fillMaxSize(),
        )
        if (!item.article.isRead) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(8.dp)
                    .background(radarColors().accent, RoundedCornerShape(50)),
            )
        }
        // 标题压底：黑渐变 scrim 保证白字可读，最多两行
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = item.article.title,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UnreadDot(visible: Boolean) {
    if (!visible) {
        // 占位，保证对齐
        Spacer(Modifier.size(6.dp))
        return
    }
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(RoundedCornerShape(50))
            .background(radarColors().accent),
    )
}

@Composable
private fun EmptyState(
    selectedTab: FeedTab,
    /** 当前分区（issue #75）：空分区空态文案来源。 */
    selectedContentType: ContentTypeFilter,
    /** 空分区空态：选中分区且库里没有任何该类型订阅源（区别于「有源但没文章」）。 */
    partitionEmpty: Boolean,
    onAddFeed: () -> Unit = {},
    onOpenSubscriptions: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 分区空态（issue #75）优先：有源没文章走原 tab 空态，无源才走分区引导——
    // 如实区分两种空。chip 行仍在上方，用户随时可切回「全部」，不阻塞。
    val (title, hint) = if (partitionEmpty && selectedContentType != ContentTypeFilter.All) {
        selectedContentType.emptyCopy()
    } else {
        when (selectedTab) {
            FeedTab.All -> "还没有订阅" to "去订阅页添加你的第一个 RSS / Atom 源"
            FeedTab.Unread -> "没有未读文章" to "所有文章都看完了，休息一下"
            FeedTab.Starred -> "还没有收藏" to "阅读时点击星标，把好文章留下来"
            FeedTab.Bookmarked -> "暂无稍后读" to "阅读时点击书签，稍后再看"
            // 推荐流空态（ADR-0013）：候选池 = 未读 + 14 天窗，读完就没了——如实说，不编内容
            FeedTab.Recommended -> "暂无推荐" to "最近未读都读完了，或还没有订阅源"
        }
    }
    // verticalScroll 让空态页也能响应下拉刷新手势
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(
                start = 32.dp,
                end = 32.dp,
                top = 32.dp,
                // 底部让位 TabBar，空态提示文字不被胶囊压住
                bottom = 32.dp + tabBarBottomClearance(),
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = radarColors().textPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            hint,
            color = radarColors().textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        // 「全部」为空 = 一篇文章都没有，必然是还没订阅。给双入口：
        // 添加订阅源（直达添加抽屉） / 导入 OPML（老用户迁移最常见的第一个动作），
        // 让用户自己去找入口，是新用户流失最快的一步。
        if (selectedTab == FeedTab.All) {
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = onAddFeed) {
                Icon(
                    Lucide.Plus,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("添加订阅源")
            }
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = onOpenSubscriptions) {
                Icon(
                    Lucide.FileUp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("导入 OPML 订阅")
            }
        }
    }
}

/** 推荐流首屏打分中的占位（候选池加载 + 打分在 IO 线程，通常一闪而过）。 */
@Composable
private fun RecommendationLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(bottom = tabBarBottomClearance()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = radarColors().accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(10.dp))
        Text("正在按你的阅读偏好排序…", color = radarColors().textSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Suppress("unused")
@Composable
fun LoadingPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = radarColors().accent)
    }
}
