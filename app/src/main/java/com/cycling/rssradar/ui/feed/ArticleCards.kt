package com.cycling.rssradar.ui.feed

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Play
import com.cycling.rssradar.R
import com.cycling.rssradar.core.data.db.entity.ArticleEntity
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.core.model.ListDescMode
import com.cycling.rssradar.core.model.ListDisplayState
import com.cycling.rssradar.core.ui.components.FeedIcon
import com.cycling.rssradar.core.ui.components.FeedLetterTile
import com.cycling.rssradar.core.ui.components.RadarImage
import com.cycling.rssradar.core.ui.components.pressScale
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.components.ArticleContextMenu
import com.cycling.rssradar.core.ui.components.ArticleMenuActions
import com.cycling.rssradar.core.ui.components.articleMenuOffset

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
            color = radarColors().articleCard,
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
                    contentDescription = stringResource(R.string.cd_cover),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // 有媒体角标但无封面图：字母色块占位，与网格口径一致
                FeedLetterTile(title = feedTitle, modifier = Modifier.fillMaxSize())
            }
        }
        when (mediaKind) {
            ArticleEntity.MEDIA_KIND_VIDEO ->
                MediaBadge(Lucide.Play, stringResource(R.string.ctype_video), Modifier.align(Alignment.BottomEnd).padding(4.dp))
            ArticleEntity.MEDIA_KIND_AUDIO ->
                MediaBadge(Lucide.Music, stringResource(R.string.ctype_audio), Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
    }
}

/** 缩略图角上的媒体种类角标：小圆片 + 图标。align 作用域由调用方的 Box 提供。 */
@Composable

internal fun MediaBadge(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
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
        ArticleEntity.MEDIA_KIND_VIDEO -> Lucide.Play to stringResource(R.string.ctype_video)
        else -> Lucide.Music to stringResource(R.string.ctype_audio)
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
