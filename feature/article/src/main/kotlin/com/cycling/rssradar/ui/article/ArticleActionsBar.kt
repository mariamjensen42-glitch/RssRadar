package com.cycling.rssradar.ui.article

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Headphones
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Star
import com.cycling.rssradar.core.ui.theme.radarColors

@Composable
internal fun ArticleActionsBar(
    isStarred: Boolean,
    isBookmarked: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onStar: () -> Unit,
    onBookmark: () -> Unit,
    onOpenOriginal: () -> Unit,
    /** AI 智能功能面板（35 项里的文章级功能）。 */
    onOpenAi: () -> Unit = {},
    /** 打开播放页；null = 这篇没有可播的音频（入口整个不出现，不留一个点不动的按钮）。 */
    onPlayAudio: (() -> Unit)? = null,
) {
    val insets = WindowInsets.navigationBars.asPaddingValues()
    Surface(color = radarColors().bgRoot) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp + insets.calculateBottomPadding()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 图标分两组：左 = 文章间导航（上一篇/下一篇），右 = 阅读动作（收藏/稍后读/
            // AI/查看原文）。组内等间距，组间弹性 spacer 撑开，全部统一 40dp——
            // 之前间距 6/10/6/6 混排 + 外链独占 48dp，视觉上忽密忽疏。
            // 上一篇 = 发布更早（列表序更靠后），下一篇 = 更新一篇
            ActionIcon(
                icon = Lucide.ChevronLeft,
                checked = false,
                contentDescription = stringResource(R.string.prev_article),
                enabled = hasPrev,
                size = 40.dp,
                onClick = onPrev,
            )
            Spacer(Modifier.width(8.dp))
            ActionIcon(
                icon = Lucide.ChevronRight,
                checked = false,
                contentDescription = stringResource(R.string.next_article),
                enabled = hasNext,
                size = 40.dp,
                onClick = onNext,
            )
            Spacer(Modifier.weight(1f))
            onPlayAudio?.let { play ->
                ActionIcon(
                    icon = Lucide.Headphones,
                    checked = false,
                    contentDescription = stringResource(R.string.player_open),
                    size = 40.dp,
                    onClick = play,
                )
                Spacer(Modifier.width(8.dp))
            }
            ActionIcon(icon = Lucide.Star, checked = isStarred, contentDescription = stringResource(R.string.star), size = 40.dp, onClick = onStar)
            Spacer(Modifier.width(8.dp))
            ActionIcon(icon = Lucide.Bookmark, checked = isBookmarked, contentDescription = stringResource(R.string.read_later), size = 40.dp, onClick = onBookmark)
            Spacer(Modifier.width(8.dp))
            ActionIcon(
                icon = Lucide.Sparkles,
                checked = false,
                contentDescription = stringResource(R.string.ai_analysis),
                size = 40.dp,
                onClick = onOpenAi,
            )
            Spacer(Modifier.width(8.dp))
            ActionIcon(
                icon = Lucide.ExternalLink,
                checked = true,
                contentDescription = stringResource(R.string.view_original),
                size = 40.dp,
                onClick = onOpenOriginal,
            )
        }
    }
}

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 48.dp,
) {
    val bg = if (checked) radarColors().accent else radarColors().surface2
    val fg = when {
        checked -> radarColors().onAccent
        !enabled -> radarColors().textTertiary
        else -> radarColors().textPrimary
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg.copy(alpha = if (enabled || checked) 1f else 0.5f),
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick, enabled = enabled),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = fg,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ── 相关阅读（AiFeature.RELATED）───────────────────────────────────────────
