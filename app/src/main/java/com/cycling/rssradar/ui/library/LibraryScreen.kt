package com.cycling.rssradar.ui.library

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.cycling.rssradar.R
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.core.model.library.LibraryRange
import com.cycling.rssradar.core.model.library.LibrarySort
import com.cycling.rssradar.core.ui.components.EmptyState
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.i18n.labelRes
import com.cycling.rssradar.ui.settings.SegmentedChips
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.X
import com.cycling.rssradar.ui.settings.SegmentedChips

/**
 * 收藏整理页：收藏与稍后读共用一个页面，页内切换。
 *
 * 与信息流里的「收藏」tab 的分工：tab 是**边读边收**的顺路出口，本页是**专门整理**的
 * 工作台——可以按收藏时间/发布时间/来源排序、按时间范围与来源筛选、多选批量移出。
 * 因此列表刻意做成紧凑的整理视图（一行一篇、带选择框），而不是信息流那套大卡片：
 * 整理时要的是一屏看到更多、能勾选，不是阅读。
 */
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onOpenArticle: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val feeds by viewModel.feeds.collectAsState()
    val colors = radarColors()
    val listState = rememberLazyListState()

    val sortLabels = LibrarySort.entries.associateWith { stringResource(it.labelRes()) }
    val rangeLabels = LibraryRange.entries.associateWith { stringResource(it.labelRes()) }
    // SegmentedChips 的 label 是普通 lambda（非组合作用域），文案必须先在这里取好
    val starredLabel = stringResource(R.string.library_tab_starred)
    val bookmarkedLabel = stringResource(R.string.library_tab_bookmarked)
    val feedLabels = buildMap {
        put(null, stringResource(R.string.library_filter_all_feeds))
        feeds.forEach { feed -> put(feed.id, feed.title) }
    }
    val feedOptions = remember(feeds) { listOf<Long?>(null) + feeds.map { it.id } }

    // 触底加载下一页。ViewModel 内部自己判 loading / 到底，重复调用是无害的。
    val reachedEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { reachedEnd }.collect { if (it) viewModel.loadMore() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgRoot)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, contentDescription = stringResource(R.string.back), tint = colors.textPrimary)
            }
            Text(
                text = stringResource(R.string.library_title),
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (state.selecting) {
                TextButton(onClick = viewModel::selectAll) {
                    Text(stringResource(R.string.library_select_all))
                }
            }
        }

        // 收藏 / 稍后读：两个 boolean 状态共用一套整理能力，切换只换谓词
        SegmentedChips(
            options = listOf(false, true),
            selected = state.showBookmarked,
            label = { bookmarked -> if (bookmarked) bookmarkedLabel else starredLabel },
            onSelect = viewModel::setShowBookmarked,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SegmentedChips(
                options = LibrarySort.entries,
                selected = state.sort,
                label = { sort -> sortLabels[sort].orEmpty() },
                onSelect = viewModel::setSort,
            )
            SegmentedChips(
                options = LibraryRange.entries,
                selected = state.range,
                label = { range -> rangeLabels[range].orEmpty() },
                onSelect = viewModel::setRange,
            )
        }
        Spacer(Modifier.height(8.dp))

        // 源筛选：源可能上百个，横向滚动一根长条比下拉更好按——整理时常常连着切几个源
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SegmentedChips(
                options = feedOptions,
                selected = state.feedId,
                label = { id -> feedLabels[id].orEmpty() },
                onSelect = viewModel::setFeedFilter,
            )
        }
        Spacer(Modifier.height(8.dp))

        if (state.selecting) {
            SelectionBar(
                count = state.selection.size,
                unstarLabel = stringResource(
                    if (state.showBookmarked) R.string.library_unbookmark else R.string.library_unstar,
                ),
                onClear = viewModel::clearSelection,
                onRemove = viewModel::removeSelected,
            )
        }

        if (state.articles.isEmpty()) {
            if (!state.loading) {
                EmptyState(
                    icon = if (state.showBookmarked) Lucide.Bookmark else Lucide.Star,
                    message = stringResource(R.string.library_empty),
                    hint = stringResource(R.string.library_empty_hint),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.articles, key = { it.article.id }) { item ->
                    LibraryRow(
                        item = item,
                        selecting = state.selecting,
                        selected = item.article.id in state.selection,
                        showStarredAt = state.sort == LibrarySort.STARRED_AT,
                        onClick = {
                            if (state.selecting) {
                                viewModel.toggleSelection(item.article.id)
                            } else {
                                onOpenArticle(item.article.id)
                            }
                        },
                        onLongClick = { viewModel.toggleSelection(item.article.id) },
                    )
                }
                if (state.articles.size < state.total) {
                    item(key = "loading-more") {
                        Text(
                            text = stringResource(R.string.library_loading_more),
                            color = colors.textTertiary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 选择态下的批量操作条：已选几篇 + 移出 + 取消选择。 */
@Composable
private fun SelectionBar(
    count: Int,
    unstarLabel: String,
    onClear: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = radarColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface2)
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.library_selected_count, count),
            color = colors.textPrimary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRemove) {
            Text(unstarLabel, color = colors.accent)
        }
        IconButton(onClick = onClear) {
            Icon(Lucide.X, contentDescription = stringResource(R.string.library_clear_selection), tint = colors.textSecondary)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LibraryRow(
    item: ArticleWithFeed,
    selecting: Boolean,
    selected: Boolean,
    showStarredAt: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = radarColors()
    val shape = RoundedCornerShape(12.dp)
    val timestamp = if (showStarredAt) {
        item.article.starredAt ?: item.article.fetchedAt
    } else {
        item.article.publishedAt ?: item.article.fetchedAt
    }
    Surface(
        color = if (selected) colors.surface2 else colors.surface1,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            // 长按进选择态：整理页批量操作比逐篇点开更常用
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.article.title,
                    color = if (item.article.isRead) colors.textSecondary else colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (item.article.isRead) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.feedTitle,
                        color = colors.textTertiary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(3.dp)
                            .background(colors.textTertiary, RoundedCornerShape(50)),
                    )
                    Text(
                        text = relativeTime(timestamp),
                        color = colors.textTertiary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

private fun relativeTime(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
