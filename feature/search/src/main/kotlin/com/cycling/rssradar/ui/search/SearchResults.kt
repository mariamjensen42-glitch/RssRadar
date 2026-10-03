package com.cycling.rssradar.ui.search

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.core.data.db.entity.FeedEntity
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.core.ui.components.ArticleContextMenu
import com.cycling.rssradar.core.ui.components.ArticleMenuActions
import com.cycling.rssradar.core.ui.components.FeedIcon
import com.cycling.rssradar.core.ui.components.articleMenuOffset
import com.cycling.rssradar.core.ui.components.pressScale
import com.cycling.rssradar.core.ui.components.tabBarBottomClearance
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.ui.search.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun SearchResults(
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
