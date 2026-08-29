package com.cycling.rssradar.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.data.ArticleWithFeed
import com.cycling.rssradar.ui.components.FeedIcon
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.BgRoot
import com.cycling.rssradar.ui.theme.OnAccent
import com.cycling.rssradar.ui.theme.Surface1
import com.cycling.rssradar.ui.theme.TextPrimary
import com.cycling.rssradar.ui.theme.TextSecondary
import com.cycling.rssradar.ui.theme.TextTertiary
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.SlidersHorizontal

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
    val unreadCount by viewModel.unreadCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val message = viewModel.uiMessage

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    val currentList = when (selectedTab) {
        FeedTab.All -> allArticles
        FeedTab.Unread -> unreadArticles
        FeedTab.Starred -> starredArticles
    }

    Scaffold(
        containerColor = BgRoot,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FeedListTopBar(onOpenSearch = onOpenSearch)
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
                onSelect = viewModel::selectTab,
            )
            Spacer(Modifier.height(8.dp))
            if (currentList.isEmpty()) {
                EmptyState(selectedTab = selectedTab, modifier = Modifier.fillMaxSize())
            } else {
                ArticleCardList(
                    articles = currentList,
                    onArticleClick = { item ->
                        viewModel.markRead(item.article.id)
                        onOpenArticle(item)
                    },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadMoreHint()
                }
            }
        }
    }
}

@Composable
private fun FeedListTopBar(onOpenSearch: () -> Unit) {
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
        IconButton(onClick = { /* TODO: 打开筛选/排序 */ }) {
            Icon(Lucide.SlidersHorizontal, contentDescription = "排序", tint = TextPrimary)
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

@Composable
private fun ArticleCardList(
    articles: List<ArticleWithFeed>,
    onArticleClick: (ArticleWithFeed) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(articles, key = { it.article.id }) { item ->
            ArticleCard(item, onClick = { onArticleClick(item) })
        }
    }
}

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
    }
    Column(
        modifier = modifier.padding(32.dp),
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
