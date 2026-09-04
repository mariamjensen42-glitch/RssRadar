package com.cycling.rssradar.ui.feed

import android.text.format.DateUtils
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalDensity
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
import com.cycling.rssradar.core.ui.components.RadarImage
import com.cycling.rssradar.core.data.db.ArticleEntity
import com.cycling.rssradar.core.data.db.ArticleWithFeed
import com.cycling.rssradar.core.data.store.ListDescMode
import com.cycling.rssradar.core.data.store.ListDisplayState
import com.cycling.rssradar.core.model.MarkAsReadCondition
import com.cycling.rssradar.ui.theme.LocalListDisplay
import com.cycling.rssradar.ui.components.ArticleContextMenu
import com.cycling.rssradar.core.ui.components.AppSnackbarHost
import com.cycling.rssradar.ui.components.ArticleMenuActions
import com.cycling.rssradar.ui.components.articleMenuOffset
import com.cycling.rssradar.core.ui.components.FeedIcon
import com.cycling.rssradar.core.ui.components.OptionPickerSheet
import com.cycling.rssradar.core.ui.components.tabBarBottomClearance
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CheckCheck
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.SlidersHorizontal
import com.cycling.rssradar.core.ui.theme.radarColors


@Composable
fun FeedListScreen(
    viewModel: FeedListViewModel,
    onOpenSearch: () -> Unit = {},
    onOpenArticle: (ArticleWithFeed) -> Unit = {},
    /** 空态「添加订阅源」直达入口（新用户第一分钟不该被卡在找入口上）。 */
    onAddFeed: () -> Unit = {},
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

    // 四个 tab 统一分页快照，分组筛选仍是对已加载页的内存过滤
    val currentList = viewModel.filterByGroup(uiState.articles)

    Scaffold(
        containerColor = radarColors().bgRoot,
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            FeedListTopBar(
                onOpenSearch = onOpenSearch,
                onOpenFilter = { showGroupSheet = true },
                onMarkAllRead = { showMarkReadSheet = true },
                filterActive = uiState.selectedGroup != null,
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
            Spacer(Modifier.height(8.dp))
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
                        onAddFeed = onAddFeed,
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
                        } else if (uiState.hasMore) {
                            LoadMoreHint()
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
            onSelect = { group ->
                viewModel.onIntent(FeedListIntent.SelectGroup(group))
                showGroupSheet = false
            },
            onDismiss = { showGroupSheet = false },
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
    filterActive: Boolean,
) {
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
        // 批量标记已读（#10）：积累几天未读刷不完的信息流，一键按时间范围清空
        IconButton(onClick = onMarkAllRead) {
            Icon(Lucide.CheckCheck, contentDescription = "标记已读", tint = radarColors().textPrimary)
        }
        IconButton(onClick = onOpenFilter) {
            Box {
                Icon(Lucide.SlidersHorizontal, contentDescription = "分组筛选", tint = radarColors().textPrimary)
                if (filterActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(radarColors().accent),
                    )
                }
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
    val bg = if (selected) radarColors().accent else radarColors().surface1
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else radarColors().textPrimary
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** 分组筛选底部弹层：「全部」+ 各分组。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupFilterSheet(
    groups: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = radarColors().surface1) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "分组筛选",
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
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
    /**
     * 滚动自动标记已读（#11）：上报"已滚出视口顶部"的文章 id 批次。
     * 由 [LocalListDisplay] 的开关决定是否启用，关闭时本回调不会被调用。
     */
    markReadPassed: (List<Long>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val display = LocalListDisplay.current.let {
        it.copy(showFeedName = showFeedName ?: it.showFeedName)
    }
    val listState = rememberLazyListState()
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
                }
            }
        } else {
            items(articles, key = { it.article.id }) { item ->
                SwipeableArticleCard(
                    item = item,
                    display = display,
                    onClick = { onArticleClick(item) },
                    onToggleRead = { onToggleRead(item.article.id, !item.article.isRead) },
                    onToggleStarred = { onToggleStarred(item.article.id) },
                    onToggleBookmarked = { onToggleBookmarked(item.article.id) },
                    onDelete = { onDelete(item.article.id) },
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
 * 所以走的是「落定即执行 + 立刻弹回」的路子：动作在 [LaunchedEffect] 里执行，
 * 随后 [SwipeToDismissBoxState.reset] 把卡片动画回原位。
 *
 * 动作刻意不挂在 `SwipeToDismissBox(onDismiss = …)` 上：onDismiss 与 reset 各在
 * 自己的协程里跑，先后顺序不受控，抢跑会把 settledValue 冲成 Settled 导致动作漏执行。
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
    val state = rememberSwipeToDismissBoxState()
    // 回调每次重组都是新 lambda，而本协程的 key 只有 settledValue（不能带回调，
    // 否则每次重组都会重启并重复触发动作）——用 UpdatedState 保证拿到最新回调。
    val currentToggleRead by rememberUpdatedState(onToggleRead)
    val currentToggleStarred by rememberUpdatedState(onToggleStarred)
    // 动作不走 onDismiss（理由见函数注释），但 SwipeToDismissBox 内部把它当作
    // 协程 key——用稳定实例，避免列表每次重组都白重启一次内部协程。
    val noopDismiss: (SwipeToDismissBoxValue) -> Unit = remember { {} }

    LaunchedEffect(state.settledValue) {
        when (state.settledValue) {
            SwipeToDismissBoxValue.StartToEnd -> currentToggleStarred() // 右滑：收藏
            SwipeToDismissBoxValue.EndToStart -> currentToggleRead() // 左滑：切换已读
            SwipeToDismissBoxValue.Settled -> return@LaunchedEffect
        }
        state.reset()
    }

    SwipeToDismissBox(
        state = state,
        onDismiss = noopDismiss,
        backgroundContent = {
            SwipeActionBackground(
                direction = state.dismissDirection,
                isRead = item.article.isRead,
                isStarred = item.article.isStarred,
            )
        },
        content = {
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
        },
    )
}

/** 滑动时露出的背景：图标 + 文案说明会发生什么（文案随当前状态变，避免猜）。 */
@Composable
private fun SwipeActionBackground(
    direction: SwipeToDismissBoxValue,
    isRead: Boolean,
    isStarred: Boolean,
) {
    val (label, icon, tint) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd ->
            Triple(
                if (isStarred) "取消收藏" else "收藏",
                Lucide.Star,
                radarColors().accent,
            )

        SwipeToDismissBoxValue.EndToStart ->
            Triple(
                if (isRead) "标未读" else "标已读",
                Lucide.Check,
                radarColors().textSecondary,
            )

        SwipeToDismissBoxValue.Settled -> return
    }
    // 卡片往左移 → 背景右侧露出 → 内容靠右；反之靠左
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart) {
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
    Box {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = radarColors().surface1,
            modifier = Modifier
                .fillMaxWidth()
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
                if (display.showThumbnail) {
                    Spacer(Modifier.width(10.dp))
                    CoverThumb(url = item.article.coverUrl?.takeIf { it.isNotBlank() }, mediaKind = item.article.mediaKind)
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
private fun CoverThumb(url: String?, mediaKind: Int = ArticleEntity.MEDIA_KIND_NONE) {
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
                Icon(
                    imageVector = Lucide.Image,
                    contentDescription = null,
                    tint = radarColors().textTertiary,
                    modifier = Modifier.align(Alignment.Center).size(18.dp),
                )
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(radarColors().surface2)
            .clickable(onClick = onClick),
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
private fun LoadMoreHint() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Lucide.ArrowUp,
            contentDescription = null,
            tint = radarColors().textTertiary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "上滑加载更多",
            color = radarColors().textTertiary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun EmptyState(
    selectedTab: FeedTab,
    onAddFeed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val (title, hint) = when (selectedTab) {
        FeedTab.All -> "还没有订阅" to "去订阅页添加你的第一个 RSS / Atom 源"
        FeedTab.Unread -> "没有未读文章" to "所有文章都看完了，休息一下"
        FeedTab.Starred -> "还没有收藏" to "阅读时点击星标，把好文章留下来"
        FeedTab.Bookmarked -> "暂无稍后读" to "阅读时点击书签，稍后再看"
        // 推荐流空态（ADR-0013）：候选池 = 未读 + 14 天窗，读完就没了——如实说，不编内容
        FeedTab.Recommended -> "暂无推荐" to "最近未读都读完了，或还没有订阅源"
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
        // 「全部」为空 = 一篇文章都没有，必然是还没订阅。这里给直达入口：
        // 让用户自己去找添加订阅的按钮，是新用户流失最快的一步。
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
