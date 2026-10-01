package com.cycling.rssradar.ui.search

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.R
import com.cycling.rssradar.core.data.db.ArticleWithFeed
import com.cycling.rssradar.core.data.db.FeedEntity
import com.cycling.rssradar.core.model.library.LibraryRange
import com.cycling.rssradar.i18n.labelRes
import com.cycling.rssradar.ui.components.ArticleContextMenu
import com.cycling.rssradar.core.ui.components.AppSnackbarHost
import com.cycling.rssradar.core.ui.components.pressScale
import com.cycling.rssradar.ui.components.ArticleMenuActions
import com.cycling.rssradar.ui.components.articleMenuOffset
import com.cycling.rssradar.ui.me.SegmentedChips
import com.cycling.rssradar.core.ui.components.FeedIcon
import com.cycling.rssradar.core.ui.components.tabBarBottomClearance
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import com.cycling.rssradar.core.ui.theme.radarColors


@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpenArticle: (ArticleWithFeed) -> Unit = {},
    /** 空态托底入口：跳订阅管理页（订阅源多的时候按源浏览比关键词更顺手）。 */
    onOpenSubscriptions: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val feeds by viewModel.feeds.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    // LaunchedEffect 不是组合作用域，文案只能经 context.getString 取（ADR-0017）
    val context = LocalContext.current

    // 删除撤销（issue #46），与信息流一致
    LaunchedEffect(state.pendingUndoDelete) {
        state.pendingUndoDelete?.let { deleted ->
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.search_deleted, deleted.title),
                actionLabel = context.getString(R.string.search_undo),
                duration = SnackbarDuration.Short,
            )
            when (result) {
                SnackbarResult.ActionPerformed -> viewModel.onIntent(SearchIntent.UndoDeleteArticle)
                SnackbarResult.Dismissed -> viewModel.onIntent(SearchIntent.DiscardUndo)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(radarColors().bgRoot)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchBar(
                query = state.query,
                onQueryChange = { viewModel.onIntent(SearchIntent.QueryChange(it)) },
                onClear = { viewModel.onIntent(SearchIntent.QueryChange("")) },
                onSubmit = { viewModel.onIntent(SearchIntent.Submit) },
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .focusRequester(focusRequester),
            )

            if (state.query.isBlank()) {
                RecentSearches(
                    history = state.history,
                    onPick = { viewModel.onIntent(SearchIntent.QueryChange(it)) },
                    onClear = { viewModel.onIntent(SearchIntent.ClearHistory) },
                    onDeleteItem = { viewModel.onIntent(SearchIntent.DeleteHistoryItem(it)) },
                )
                // 无历史时的托底内容：整页只剩一句"暂无搜索记录"太空洞
                if (state.history.isEmpty()) {
                    IdleSuggestions(onOpenSubscriptions = onOpenSubscriptions)
                }
            } else {
                SearchResults(
                    state = state,
                    feeds = feeds,
                    onOpenArticle = onOpenArticle,
                    onToggleRead = { id, read -> viewModel.onIntent(SearchIntent.SetRead(id, read)) },
                    onToggleStarred = { id -> viewModel.onIntent(SearchIntent.ToggleStarred(id)) },
                    onToggleBookmarked = { id -> viewModel.onIntent(SearchIntent.ToggleBookmarked(id)) },
                    onDelete = { id -> viewModel.onIntent(SearchIntent.DeleteArticle(id)) },
                    onIntent = viewModel::onIntent,
                    onLoadMore = viewModel::loadMore,
                )
            }
        }
        AppSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 键盘 action 触发搜索（UI 审计 S1）：移动端惯例，去掉右上角文字按钮
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                stringResource(R.string.search_placeholder),
                color = radarColors().textTertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(50),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        leadingIcon = {
            Icon(Lucide.Search, contentDescription = null, tint = radarColors().textTertiary, modifier = Modifier.size(18.dp))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Lucide.X, contentDescription = stringResource(R.string.search_clear), tint = radarColors().textTertiary, modifier = Modifier.size(18.dp))
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = radarColors().surface1,
            unfocusedContainerColor = radarColors().surface1,
            focusedBorderColor = radarColors().accent,
            unfocusedBorderColor = radarColors().surface2,
            focusedTextColor = radarColors().textPrimary,
            unfocusedTextColor = radarColors().textPrimary,
            cursorColor = radarColors().accent,
        ),
    )
}

@Composable
private fun RecentSearches(
    history: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    /** 删除单条历史（UI 审计 S2）。 */
    onDeleteItem: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.search_recent),
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (history.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text(
                        text = stringResource(R.string.search_clear_history),
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (history.isEmpty()) {
            Text(
                text = stringResource(R.string.search_no_history),
                color = radarColors().textTertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            // 用 FlowRow 效果的最简实现：3 个一行手写（如果 chips 太多可换 FlowRow）
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                history.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { term ->
                            HistoryChip(
                                term = term,
                                onClick = { onPick(term) },
                                onDelete = { onDeleteItem(term) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 无搜索历史时的托底：给「按订阅源浏览」一条出路，页面不再是空洞的一片。 */
@Composable
private fun IdleSuggestions(onOpenSubscriptions: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = radarColors().surface1,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .fillMaxWidth()
            .clickable(onClick = onOpenSubscriptions),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Lucide.FolderOpen,
                contentDescription = null,
                tint = radarColors().accent,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.search_browse_feeds),
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.search_browse_feeds_hint),
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                Lucide.ChevronRight,
                contentDescription = null,
                tint = radarColors().textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun HistoryChip(term: String, onClick: () -> Unit, onDelete: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = radarColors().surface1,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = term,
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 14.dp),
            )
            // 尾随 × 删除单条（UI 审计 S2）
            IconButton(onClick = { onDelete(term) }, modifier = Modifier.size(24.dp)) {
                Icon(
                    Lucide.X,
                    contentDescription = stringResource(R.string.search_delete_history_item, term),
                    tint = radarColors().textTertiary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    state: SearchUiState,
    feeds: List<FeedEntity>,
    onOpenArticle: (ArticleWithFeed) -> Unit,
    onToggleRead: (Long, Boolean) -> Unit,
    onToggleStarred: (Long) -> Unit,
    onToggleBookmarked: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onIntent: (SearchIntent) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    // 触底加载下一页。ViewModel 内部自己判 loading / 到底，重复调用无害。
    val reachedEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { reachedEnd }.collect { if (it) onLoadMore() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchFilterRow(state = state, feeds = feeds, onIntent = onIntent)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // 底部让位悬浮 TabBar（含导航栏 inset）
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = tabBarBottomClearance(),
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "count") {
                Text(
                    // 报**总命中数**而不是已载入条数：分页下后者会随着滚动一直涨，不是结果规模
                    text = stringResource(R.string.search_result_count, state.hits, state.query),
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(state.results, key = { it.article.id }) { article ->
                SearchResultRow(
                    article = article,
                    query = state.query,
                    onClick = { onOpenArticle(article) },
                    onToggleRead = { onToggleRead(article.article.id, !article.article.isRead) },
                    onToggleStarred = { onToggleStarred(article.article.id) },
                    onToggleBookmarked = { onToggleBookmarked(article.article.id) },
                    onDelete = { onDelete(article.article.id) },
                )
            }
            if (state.results.isEmpty() && state.searched && !state.loading) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.search_no_result), color = radarColors().textTertiary)
                    }
                }
            }
            if (state.results.size < state.hits) {
                item(key = "more") {
                    Text(
                        text = stringResource(R.string.search_loading_more),
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/**
 * 二次筛选条：时间范围 / 未读·收藏·稍后读 / 来源。
 *
 * 为什么必须有：FTS 只负责"哪几篇里有这个词"，而数万篇的库里这个词往往命中几百篇，
 * 用户真正要的是"最近一周我还没读的那几篇"。没有二次筛选，搜索结果的可用性就只有一半。
 */
@Composable
private fun SearchFilterRow(
    state: SearchUiState,
    feeds: List<FeedEntity>,
    onIntent: (SearchIntent) -> Unit,
) {
    val rangeLabels = LibraryRange.entries.associateWith { stringResource(it.labelRes()) }
    val feedLabels = buildMap {
        put(null, stringResource(R.string.search_filter_all_feeds))
        feeds.forEach { feed -> put(feed.id, feed.title) }
    }
    val feedOptions = remember(feeds) { listOf<Long?>(null) + feeds.map { it.id } }
    // SegmentedChips / 自绘 chip 的文案都得先在组合作用域取好（label 是普通 lambda）
    val unreadLabel = stringResource(R.string.search_filter_unread)
    val starredLabel = stringResource(R.string.search_filter_starred)
    val bookmarkedLabel = stringResource(R.string.search_filter_bookmarked)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SegmentedChips(
                options = LibraryRange.entries.toList(),
                selected = state.range,
                label = { range -> rangeLabels[range].orEmpty() },
                onSelect = { onIntent(SearchIntent.SetRange(it)) },
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterToggle(
                label = unreadLabel,
                active = state.filters.unreadOnly,
                onClick = { onIntent(SearchIntent.ToggleUnreadOnly) },
            )
            FilterToggle(
                label = starredLabel,
                active = state.filters.starredOnly,
                onClick = { onIntent(SearchIntent.ToggleStarredOnly) },
            )
            FilterToggle(
                label = bookmarkedLabel,
                active = state.filters.bookmarkedOnly,
                onClick = { onIntent(SearchIntent.ToggleBookmarkedOnly) },
            )
            if (!state.filters.isDefault) {
                TextButton(onClick = { onIntent(SearchIntent.ClearFilters) }) {
                    Text(
                        text = stringResource(R.string.search_filter_clear),
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SegmentedChips(
                options = feedOptions,
                selected = state.filters.feedId,
                label = { id -> feedLabels[id].orEmpty() },
                onSelect = { onIntent(SearchIntent.SetFeedFilter(it)) },
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun FilterToggle(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) radarColors().accent else radarColors().surface2,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = if (active) radarColors().onAccent else radarColors().textPrimary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultRow(
    article: ArticleWithFeed,
    query: String,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleStarred: () -> Unit,
    onToggleBookmarked: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    // 菜单偏移：贴着长按手指出现（与信息流 ArticleCard 一致，逻辑在 articleMenuOffset）
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var cardTopInWindowPx by remember { mutableStateOf(0f) }
    var cardHeightPx by remember { mutableStateOf(0) }
    var pressPos by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val windowHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    // 按压缩放（docs/motion.md #2）：source 与 combinedClickable 共用同一实例
    val interactionSource = remember { MutableInteractionSource() }
    Box {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = radarColors().articleCard,
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interactionSource)
                .onGloballyPositioned {
                    cardTopInWindowPx = it.localToWindow(Offset.Zero).y
                    cardHeightPx = it.size.height
                }
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
                            menuItemCount = 7,
                            windowHeightPx = windowHeightPx,
                            density = density,
                        )
                        menuExpanded = true
                    },
                ),
        ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = article.article.title.highlight(query),
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            article.article.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = summary.highlight(query),
                    color = radarColors().textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FeedIcon(title = article.feedTitle, iconUrl = article.feedIconUrl, size = 14.dp, cornerRadius = 4.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = article.feedTitle,
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(6.dp))
                Text("·", color = radarColors().textTertiary)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = article.article.publishedAt?.let {
                        DateUtils.getRelativeTimeSpanString(it).toString()
                    } ?: "",
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        }

        // 长按上下文菜单（issue #46），与信息流一致，出现在长按手指处
        ArticleContextMenu(
            expanded = menuExpanded,
            offset = menuOffset,
            actions = ArticleMenuActions(
                isRead = article.article.isRead,
                isStarred = article.article.isStarred,
                isBookmarked = article.article.isBookmarked,
                link = article.article.link,
                onToggleRead = onToggleRead,
                onToggleStarred = onToggleStarred,
                onToggleBookmarked = onToggleBookmarked,
                onDelete = onDelete,
            ),
            onDismiss = { menuExpanded = false },
        )
    }
}

/** 简单的高亮：把 query 在原文中出现的部分用 ● 标记（设计稿用紫色高亮，我们用更易实现的全角点）。 */
private fun String.highlight(needle: String): String {
    if (needle.isBlank()) return this
    val idx = indexOf(needle, ignoreCase = true)
    if (idx < 0) return this
    return substring(0, idx) + needle + "  •  " + substring(idx + needle.length)
}
