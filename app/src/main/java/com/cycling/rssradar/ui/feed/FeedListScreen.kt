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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.cycling.rssradar.data.db.ArticleWithFeed
import com.cycling.rssradar.ui.components.FeedIcon
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.BgRoot
import com.cycling.rssradar.ui.theme.OnAccent
import com.cycling.rssradar.ui.theme.Surface1
import com.cycling.rssradar.ui.theme.TextPrimary
import com.cycling.rssradar.ui.theme.TextSecondary
import com.cycling.rssradar.ui.theme.TextTertiary
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.SlidersHorizontal
import com.cycling.rssradar.ui.theme.Surface2


@Composable
fun FeedListScreen(
    viewModel: FeedListViewModel,
    onOpenSearch: () -> Unit = {},
    onOpenArticle: (ArticleWithFeed) -> Unit = {},
    onAddSubscription: () -> Unit = {},
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val allArticles by viewModel.allArticles.collectAsState()
    val unreadArticles by viewModel.unreadArticles.collectAsState()
    val starredArticles by viewModel.starredArticles.collectAsState()
    val bookmarkedArticles by viewModel.bookmarkedArticles.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val groupOptions by viewModel.groupOptions.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showGroupSheet by remember { mutableStateOf(false) }
    val message = viewModel.uiMessage
    val isRefreshing = viewModel.isRefreshing
    val isLoadingMore = viewModel.isLoadingMore
    val hasMore = viewModel.hasMore

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onIntent(FeedListIntent.ConsumeMessage)
        }
    }

    val currentList = viewModel.filterByGroup(
        when (selectedTab) {
            FeedTab.All -> allArticles
            FeedTab.Unread -> unreadArticles
            FeedTab.Starred -> starredArticles
            FeedTab.Bookmarked -> bookmarkedArticles
        },
    )

    Scaffold(
        containerColor = BgRoot,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FeedListTopBar(
                onOpenSearch = onOpenSearch,
                onOpenFilter = { showGroupSheet = true },
                filterActive = selectedGroup != null,
            )
        },
        // 加源是低频动作，收进 FAB，不占主屏。抬高是为了让开底部胶囊 TabBar。
        floatingActionButton = {
            AddSubscriptionFab(
                onClick = onAddSubscription,
                modifier = Modifier.padding(bottom = FAB_BOTTOM_OFFSET),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            FeedListTabRow(
                selected = selectedTab,
                unreadCount = unreadCount,
                onSelect = { viewModel.onIntent(FeedListIntent.SelectTab(it)) },
            )
            Spacer(Modifier.height(8.dp))
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.onIntent(FeedListIntent.Refresh) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (currentList.isEmpty()) {
                    EmptyState(selectedTab = selectedTab, modifier = Modifier.fillMaxSize())
                } else {
                    ArticleCardList(
                        articles = currentList,
                        onArticleClick = { item ->
                            viewModel.onIntent(FeedListIntent.MarkRead(item.article.id))
                            onOpenArticle(item)
                        },
                        // 只有 All tab 分页；滚动到底自动加载下一页
                        onScrolledToEnd = {
                            if (selectedTab == FeedTab.All) viewModel.onIntent(FeedListIntent.LoadMore)
                        },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isLoadingMore) {
                            CircularProgressIndicator(
                                color = Accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else if (hasMore) {
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
            selected = selectedGroup,
            onSelect = { group ->
                viewModel.onIntent(FeedListIntent.SelectGroup(group))
                showGroupSheet = false
            },
            onDismiss = { showGroupSheet = false },
        )
    }
}

@Composable
private fun FeedListTopBar(
    onOpenSearch: () -> Unit,
    onOpenFilter: () -> Unit,
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

/** 让开底部胶囊 TabBar 的抬升量：TabBar 高约 56 + 外边距 12 + 间距 20。 */
private val FAB_BOTTOM_OFFSET = 88.dp

@Composable
private fun AddSubscriptionFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Accent,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .size(56.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Lucide.Plus,
                contentDescription = "添加订阅",
                tint = OnAccent,
                modifier = Modifier.size(26.dp),
            )
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

@Composable
private fun ArticleCardList(
    articles: List<ArticleWithFeed>,
    onArticleClick: (ArticleWithFeed) -> Unit,
    onScrolledToEnd: () -> Unit,
) {
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
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(articles, key = { it.article.id }) { item ->
            ArticleCard(item, onClick = { onArticleClick(item) })
        }
    }
}

/** 距列表尾部还剩这么多项时预加载下一页。 */
private const val LOAD_MORE_THRESHOLD = 5

@Composable
fun ArticleCard(item: ArticleWithFeed, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Surface1,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UnreadDot(visible = !item.article.isRead)
                Spacer(Modifier.width(6.dp))
                FeedIcon(title = item.feedTitle, size = 18.dp, cornerRadius = 5.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = item.feedTitle,
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.article.publishedAt?.let { ts ->
                    Text(
                        text = DateUtils.getRelativeTimeSpanString(ts).toString(),
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.article.title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.article.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = summary,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                CoverThumb(url = item.article.coverUrl?.takeIf { it.isNotBlank() })
            }
        }
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
            .padding(32.dp),
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
