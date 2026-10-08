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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Play
import com.cycling.rssradar.core.data.db.entity.ArticleEntity
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.core.ui.components.FeedLetterTile
import com.cycling.rssradar.core.ui.components.RadarImage
import com.cycling.rssradar.core.ui.components.pressScale
import com.cycling.rssradar.core.ui.components.ArticleMenuActions

/** 封面比例测出之前的占位值（沿用旧网格的 4:3），让未加载时的高度先落在常见横图上。 */
private const val DefaultCoverRatio = 4f / 3f

/** 封面比例限幅：超长图会把一列拉成一条、超宽图会把卡片压扁，两头都收住。 */
private const val MinCoverRatio = 0.6f
private const val MaxCoverRatio = 1.6f

/**
 * 解码尺寸 → 封面显示比例（宽 / 高）。
 *
 * 非正尺寸回落到 [DefaultCoverRatio]：0 会算出 Infinity/NaN，而 `coerceIn` 对 NaN 是放行的
 * （两侧比较都为 false），它会一路穿到 `aspectRatio` 把卡片画成一条线 —— 那种坏法不报错、
 * 只在某个源上看得见，所以这里挡死。
 *
 * public 而非 internal：CI 只跑 app 的单元测试、不跑 feature 模块的，
 * 守门测试要放 app/src/test 就得让这个纯函数可见（同 `fitMediaSize` 的做法）。
 */
fun coverAspectRatio(width: Int, height: Int): Float =
    if (width <= 0 || height <= 0) {
        DefaultCoverRatio
    } else {
        (width.toFloat() / height).coerceIn(MinCoverRatio, MaxCoverRatio)
    }

/**
 * 网格模式 = 瀑布流（LazyVerticalStaggeredGrid）。列数按可用宽度自适应（手机两列、平板/横屏更多），
 * 卡片高度随封面**原始比例**变化，不再被裁成统一的 4:3 方格。
 *
 * 比例是从解码结果反推的：RSS 文章不带图片尺寸，只能等 [RadarImage] 的尺寸回调，
 * 按 url 缓存在此处。未测到之前一律按 4:3 占位，所以首次滚动会有一次轻微的高度修正 ——
 * 之后同一条封面不再跳（Coil 的内存/磁盘缓存负责图片本身，这里只额外记一个 float）。
 *
 * 分页沿用滚近底部触发；粘性日期头/滚动标已读均为列表容器逻辑，网格不适用。
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
    val listState = rememberLazyStaggeredGridState()
    val coverRatios = remember { mutableStateMapOf<String, Float>() }
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) onScrolledToEnd()
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 160.dp),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp,
        modifier = modifier.fillMaxSize(),
    ) {
        items(items = articles, key = { it.article.id }) { item ->
            val coverUrl = item.article.coverUrl
            ArticleMenuBox(
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
                    coverAspectRatio = coverUrl?.let { coverRatios[it] } ?: DefaultCoverRatio,
                    onCoverSize = { width, height ->
                        if (!coverUrl.isNullOrBlank()) {
                            val ratio = coverAspectRatio(width, height)
                            // 值没变就别写：state map 的写入会触发重组，无条件写会退化成重组循环
                            if (coverRatios[coverUrl] != ratio) coverRatios[coverUrl] = ratio
                        }
                    },
                    onClick = { onArticleClick(item) },
                    onLongClick = onLongClick,
                )
            }
        }
    }
}

/**
 * 瀑布流单元格：封面铺满整卡，标题与来源压在底部的渐变 scrim 上（与单源页画廊同一套做法）。
 *
 * 文字压底而不是排在封面下方，是为了让卡片高度**完全等于**封面高度 —— 否则每张卡还要加上
 * 一段高度相同的文字区，瀑布流各列的高度差会被这段常数稀释掉一截。
 * 代价是文字可读性依赖 scrim，很亮的封面靠渐变压住；这也是画廊既有的取舍。
 */
@Composable

private fun GridArticleCard(
    item: ArticleWithFeed,
    coverAspectRatio: Float,
    onCoverSize: (Int, Int) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(coverAspectRatio)
            .pressScale(interactionSource)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(
                interactionSource = interactionSource,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        RadarImage(
            url = item.article.coverUrl,
            contentDescription = item.article.title,
            contentScale = ContentScale.Crop,
            onImageSize = onCoverSize,
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
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
            )
        }
        // 媒体角标放右上：底部整条让给标题与来源，放右下会被 scrim 里的文字压住
        when (item.article.mediaKind) {
            ArticleEntity.MEDIA_KIND_VIDEO ->
                MediaBadge(Lucide.Play, stringResource(R.string.ctype_video), Modifier.align(Alignment.TopEnd).padding(6.dp))
            ArticleEntity.MEDIA_KIND_AUDIO ->
                MediaBadge(Lucide.Music, stringResource(R.string.ctype_audio), Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Column {
                Text(
                    text = item.article.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append(item.feedTitle)
                        item.article.publishedAt?.let { ts ->
                            append(" · ")
                            append(DateUtils.getRelativeTimeSpanString(ts).toString())
                        }
                    },
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
