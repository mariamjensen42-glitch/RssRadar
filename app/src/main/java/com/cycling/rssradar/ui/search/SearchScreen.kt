package com.cycling.rssradar.ui.search

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.core.data.db.ArticleWithFeed
import com.cycling.rssradar.ui.components.ArticleContextMenu
import com.cycling.rssradar.core.ui.components.AppSnackbarHost
import com.cycling.rssradar.ui.components.ArticleMenuActions
import com.cycling.rssradar.ui.components.articleMenuOffset
import com.cycling.rssradar.core.ui.components.FeedIcon
import com.cycling.rssradar.core.ui.components.tabBarBottomClearance
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import com.cycling.rssradar.core.ui.theme.radarColors


@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpenArticle: (ArticleWithFeed) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }

    // 删除撤销（issue #46），与信息流一致
    LaunchedEffect(state.pendingUndoDelete) {
        state.pendingUndoDelete?.let { deleted ->
            val result = snackbarHostState.showSnackbar(
                message = "已删除「${deleted.title}」",
                actionLabel = "撤销",
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
                )
            } else {
                SearchResults(
                    state = state,
                    onOpenArticle = onOpenArticle,
                    onToggleRead = { id, read -> viewModel.onIntent(SearchIntent.SetRead(id, read)) },
                    onToggleStarred = { id -> viewModel.onIntent(SearchIntent.ToggleStarred(id)) },
                    onToggleBookmarked = { id -> viewModel.onIntent(SearchIntent.ToggleBookmarked(id)) },
                    onDelete = { id -> viewModel.onIntent(SearchIntent.DeleteArticle(id)) },
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    "搜索文章、来源或关键词",
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            leadingIcon = {
                Icon(Lucide.Search, contentDescription = null, tint = radarColors().textTertiary, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Lucide.X, contentDescription = "清空", tint = radarColors().textTertiary, modifier = Modifier.size(18.dp))
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
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onSubmit) {
            Text(
                text = "搜索",
                color = radarColors().link,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun RecentSearches(
    history: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "最近搜索",
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (history.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text(text = "清空历史", color = radarColors().textTertiary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (history.isEmpty()) {
            Text(
                text = "暂无搜索记录",
                color = radarColors().textTertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            // 用 FlowRow 效果的最简实现：3 个一行手写（如果 chips 太多可换 FlowRow）
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                history.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { term ->
                            HistoryChip(term = term, onClick = { onPick(term) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryChip(term: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = radarColors().surface1,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = term,
            color = radarColors().textPrimary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SearchResults(
    state: SearchUiState,
    onOpenArticle: (ArticleWithFeed) -> Unit,
    onToggleRead: (Long, Boolean) -> Unit,
    onToggleStarred: (Long) -> Unit,
    onToggleBookmarked: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    LazyColumn(
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
        item {
            Text(
                text = "找到 ${state.results.size} 条与「${state.query}」相关的结果",
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
        if (state.results.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无结果，试试其他关键词", color = radarColors().textTertiary)
                }
            }
        }
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
    Box {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = radarColors().surface1,
            modifier = Modifier
                .fillMaxWidth()
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
