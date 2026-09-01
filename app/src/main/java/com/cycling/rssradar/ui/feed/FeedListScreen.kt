package com.cycling.rssradar.ui.feed

import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.cycling.rssradar.data.db.ArticleWithFeed
import com.cycling.rssradar.data.store.ListDescMode
import com.cycling.rssradar.data.store.ListDisplayState
import com.cycling.rssradar.data.store.MarkAsReadCondition
import com.cycling.rssradar.ui.theme.LocalListDisplay
import com.cycling.rssradar.ui.components.ArticleContextMenu
import com.cycling.rssradar.ui.components.AppSnackbarHost
import com.cycling.rssradar.ui.components.ArticleMenuActions
import com.cycling.rssradar.ui.components.FeedIcon
import com.cycling.rssradar.ui.components.OptionPickerSheet
import com.cycling.rssradar.ui.components.tabBarBottomClearance
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.BgRoot
import com.cycling.rssradar.ui.theme.Surface1
import com.cycling.rssradar.ui.theme.TextPrimary
import com.cycling.rssradar.ui.theme.TextSecondary
import com.cycling.rssradar.ui.theme.TextTertiary
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CheckCheck
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.SlidersHorizontal
import com.cycling.rssradar.ui.theme.Surface2


@Composable
fun FeedListScreen(
    viewModel: FeedListViewModel,
    onOpenSearch: () -> Unit = {},
    onOpenArticle: (ArticleWithFeed) -> Unit = {},
) {
    // MVI 候选 C（ADR-0003）：单一 UiState 快照驱动渲染
    val uiState by viewModel.uiState.collectAsState()
    val groupOptions by viewModel.groupOptions.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
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
        containerColor = BgRoot,
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
                onSelect = { viewModel.onIntent(FeedListIntent.SelectTab(it)) },
            )
            Spacer(Modifier.height(8.dp))
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.onIntent(FeedListIntent.Refresh) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (currentList.isEmpty()) {
                    EmptyState(selectedTab = uiState.selectedTab, modifier = Modifier.fillMaxSize())
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
                        // 四个 tab 均分页；滚动到底自动加载下一页
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
                                color = Accent,
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
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenSearch) {
            Icon(Lucide.Search, contentDescription = "搜索", tint = TextPrimary)
        }
        // 批量标记已读（#10）：积累几天未读刷不完的信息流，一键按时间范围清空
        IconButton(onClick = onMarkAllRead) {
            Icon(Lucide.CheckCheck, contentDescription = "标记已读", tint = TextPrimary)
        }
        IconButton(onClick = onOpenFilter) {
            Box {
                Icon(Lucide.SlidersHorizontal, contentDescription = "分组筛选", tint = TextPrimary)
                if (filterActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Accent),
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
    onSelect: (FeedTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeedTab.entries.forEach { tab ->
            val label = when (tab) {
                FeedTab.All -> "全部"
                FeedTab.Unread -> "未读 $unreadCount"
                FeedTab.Starred -> "收藏"
                FeedTab.Bookmarked -> "稍后读"
            }
            FilterChip(
                label = label,
                selected = tab == selected,
                onClick = { onSelect(tab) },
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Accent else Surface1
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else TextPrimary
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "分组筛选",
                color = TextPrimary,
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
            color = if (selected) Accent else TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Lucide.Check,
                contentDescription = "已选",
                tint = Accent,
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
    // 滚动自动标记已读（#11）：把"列表槽位 → 文章 id"铺平（粘性日期头也占一个槽位，
    // 用 null 占位），滚过视口顶部的槽位即视为已读。槽位表与列表结构严格同构，
    // 否则粘性头开启时索引会错位。
    val slotIds: List<Long?> = if (display.stickyDateHeader) {
        remember(dayGroups) {
            buildList {
                dayGroups.forEach { group ->
                    if (group.label != null) add(null)
                    group.items.forEach { add(it.article.id) }
                }
            }
        }
    } else {
        remember(articles) { articles.map { it.article.id } }
    }
    val unreadIds = remember(articles) {
        articles.filter { !it.article.isRead }.map { it.article.id }.toSet()
    }
    LaunchedEffect(listState, display.markReadOnScroll, slotIds, unreadIds, markReadPassed) {
        if (!display.markReadOnScroll) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstVisible ->
                val passed = slotIds.subList(0, firstVisible.coerceAtMost(slotIds.size))
                    .filterNotNull()
                    .filter { it in unreadIds }
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
                group.label?.let { label ->
                    // stickyHeader 是 LazyListScope 接口成员（foundation 1.10+，真值表已核），
                    // DSL 内直接调用，不可 import
                    stickyHeader(key = "date-${group.key}") {
                        StickyDateHeader(label)
                    }
                }
                items(group.items, key = { it.article.id }) { item ->
                    ArticleCard(
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
        } else {
            items(articles, key = { it.article.id }) { item ->
                ArticleCard(
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
private const val LOAD_MORE_THRESHOLD = 5

/** 粘性日期头：不透明底色（页面底色）保证滚动时干净压住下方卡片。 */
@Composable
private fun StickyDateHeader(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgRoot)
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            color = TextTertiary,
            style = MaterialTheme.typography.labelMedium,
        )
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
) {
    var menuExpanded by remember { mutableStateOf(false) }
    // 已读弱化（issue #56）：开关开启时已读卡片降弱色；未读卡片永不因此改变
    val dimmed = display.dimRead && item.article.isRead
    val titleColor = if (dimmed) TextTertiary else TextPrimary
    val descColor = if (dimmed) TextTertiary else TextSecondary
    Box {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Surface1,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true },
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
                        color = TextPrimary,
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
                            color = TextTertiary,
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
                    CoverThumb(url = item.article.coverUrl?.takeIf { it.isNotBlank() })
                }
            }
        }
        }

        // 长按上下文菜单（issue #46），锚定卡片
        ArticleContextMenu(
            expanded = menuExpanded,
            actions = ArticleMenuActions(
                isRead = item.article.isRead,
                isStarred = item.article.isStarred,
                isBookmarked = item.article.isBookmarked,
                link = item.article.link,
                onToggleRead = onToggleRead,
                onToggleStarred = onToggleStarred,
                onToggleBookmarked = onToggleBookmarked,
                onDelete = onDelete,
            ),
            onDismiss = { menuExpanded = false },
        )
    }
}

/**
 * 列表封面缩略图：统一 96×72（4:3），ContentScale.Crop 居中裁剪不拉伸；
 * 无封面画 Surface2 + Image 图标占位。固定尺寸让 Coil 免读原图尺寸、按目标大小解码，
 * LazyColumn 滚动开销最小；AsyncImage 无子组合，比 SubcomposeAsyncImage 更轻。
 */
@Composable
private fun CoverThumb(url: String?) {
    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2),
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = "封面缩略图",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Lucide.Image,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(20.dp),
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
            .background(Accent),
    )
}

@Composable
private fun LoadMoreHint() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Lucide.ArrowUp,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "上滑加载更多",
            color = TextTertiary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun EmptyState(selectedTab: FeedTab, modifier: Modifier = Modifier) {
    val (title, hint) = when (selectedTab) {
        FeedTab.All -> "还没有订阅" to "去订阅页添加你的第一个 RSS / Atom 源"
        FeedTab.Unread -> "没有未读文章" to "所有文章都看完了，休息一下"
        FeedTab.Starred -> "还没有收藏" to "阅读时点击星标，把好文章留下来"
        FeedTab.Bookmarked -> "暂无稍后读" to "阅读时点击书签，稍后再看"
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
        Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            hint,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Suppress("unused")
@Composable
fun LoadingPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Accent)
    }
}
