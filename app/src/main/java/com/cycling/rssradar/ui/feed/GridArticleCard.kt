package com.cycling.rssradar.ui.feed

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Play
import com.cycling.rssradar.R
import com.cycling.rssradar.core.data.db.entity.ArticleEntity
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.core.ui.components.FeedLetterTile
import com.cycling.rssradar.core.ui.components.RadarImage
import com.cycling.rssradar.core.ui.components.pressScale
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.components.ArticleMenuActions

/**
 * 网格模式：GridCells.Adaptive 按可用宽度自动定列数（手机两列、平板/横屏更多），
 * 分页沿用滚近底部触发。粘性日期头/滚动标已读均为列表容器逻辑，网格不适用。
 */
@Composable

internal fun ArticleAdaptiveGrid(
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
        color = radarColors().articleCard,
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
                        MediaBadge(Lucide.Play, stringResource(R.string.ctype_video), Modifier.align(Alignment.BottomEnd).padding(6.dp))
                    ArticleEntity.MEDIA_KIND_AUDIO ->
                        MediaBadge(Lucide.Music, stringResource(R.string.ctype_audio), Modifier.align(Alignment.BottomEnd).padding(6.dp))
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
