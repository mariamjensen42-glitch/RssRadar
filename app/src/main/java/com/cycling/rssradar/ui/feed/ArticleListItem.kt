package com.cycling.rssradar.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.core.data.store.model.ListDisplayState
import com.cycling.rssradar.core.data.store.model.ListViewMode
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.components.ArticleMenuActions

/**
 * 单篇文章项按视图模式分发：列表/卡片走 [SwipeableArticleCard]（带滑动手势），
 * 杂志走图文混排卡（首篇 hero 大图）。长按上下文菜单在杂志卡里保持一致。
 */
@Composable

internal fun ArticleListItem(
    item: ArticleWithFeed,
    display: ListDisplayState,
    hero: Boolean,
    onArticleClick: (ArticleWithFeed) -> Unit,
    onToggleRead: (Long, Boolean) -> Unit,
    onToggleStarred: (Long) -> Unit,
    onToggleBookmarked: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onReduceSuch: ((Long) -> Unit)?,
) {
    if (display.viewMode != ListViewMode.MAGAZINE) {
        SwipeableArticleCard(
            item = item,
            display = display,
            onClick = { onArticleClick(item) },
            onToggleRead = { onToggleRead(item.article.id, !item.article.isRead) },
            onToggleStarred = { onToggleStarred(item.article.id) },
            onToggleBookmarked = { onToggleBookmarked(item.article.id) },
            onDelete = { onDelete(item.article.id) },
            onReduceSuch = onReduceSuch?.let { reduce -> { reduce(item.article.id) } },
        )
        return
    }
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
        if (hero) {
            MagazineHeroCard(item = item, onClick = { onArticleClick(item) }, onLongClick = onLongClick)
        } else {
            MagazineCard(item = item, onClick = { onArticleClick(item) }, onLongClick = onLongClick)
        }
    }
}

/** 粘性日期头：不透明底色（页面底色）保证滚动时干净压住下方卡片。 */
@Composable

internal fun StickyDateHeader(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(radarColors().bgRoot)
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            color = radarColors().textTertiary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable

internal fun UnreadDot(visible: Boolean) {
    if (!visible) {
        // 占位，保证对齐
        Spacer(Modifier.size(6.dp))
        return
    }
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(RoundedCornerShape(50))
            .background(radarColors().accent),
    )
}
