package com.cycling.rssradar.ui.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cycling.rssradar.ui.components.AppSnackbarHost
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.BgRoot
import com.cycling.rssradar.ui.theme.TextPrimary
import com.cycling.rssradar.ui.theme.TextSecondary
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw

/**
 * 订阅源文章列表（CONTEXT.md「Feed article list」，issue #51）：
 * 单源浏览页，顶栏 = 返回 + 源名 + 单源刷新；列表复用 [ArticleCardList]。
 * 状态直读 VM 的 Compose mutableState（与 FeedListScreen 同款），无需 collect。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedArticlesScreen(
    viewModel: FeedArticlesViewModel,
    onBack: () -> Unit,
    onOpenArticle: (Long) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = viewModel.uiMessage

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onIntent(FeedArticlesIntent.ConsumeMessage)
        }
    }

    Scaffold(
        containerColor = BgRoot,
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = viewModel.feed?.title ?: "订阅源",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Lucide.ArrowLeft, contentDescription = "返回", tint = TextPrimary)
                    }
                },
                actions = {
                    // 单源刷新秒级完成；进行中把图标换成转圈
                    IconButton(onClick = { viewModel.onIntent(FeedArticlesIntent.Refresh) }) {
                        if (viewModel.isRefreshing) {
                            CircularProgressIndicator(
                                color = TextSecondary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Icon(Lucide.RefreshCw, contentDescription = "刷新此源", tint = TextPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgRoot),
            )
        },
    ) { padding ->
        if (viewModel.articles.isEmpty() && !viewModel.isRefreshing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("此订阅源还没有文章", color = TextSecondary)
            }
        } else {
            ArticleCardList(
                articles = viewModel.articles,
                onArticleClick = { item ->
                    viewModel.onIntent(FeedArticlesIntent.MarkRead(item.article.id))
                    onOpenArticle(item.article.id)
                },
                onToggleRead = { id, read ->
                    viewModel.onIntent(FeedArticlesIntent.SetRead(id, read))
                },
                onToggleStarred = { id ->
                    viewModel.onIntent(FeedArticlesIntent.ToggleStarred(id))
                },
                onToggleBookmarked = { id ->
                    viewModel.onIntent(FeedArticlesIntent.ToggleBookmarked(id))
                },
                onDelete = { id ->
                    viewModel.onIntent(FeedArticlesIntent.DeleteArticle(id))
                },
                onScrolledToEnd = { viewModel.onIntent(FeedArticlesIntent.LoadMore) },
                // 单源页强制隐藏订阅源名称（issue #56）：同源卡片重复源名是纯噪音
                showFeedName = false,
                // 本页无悬浮 TabBar，普通间距即可
                bottomPadding = 16.dp,
                modifier = Modifier.padding(padding),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (viewModel.isLoadingMore) {
                    CircularProgressIndicator(
                        color = Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
