package com.cycling.rssradar.ui.feed

import com.cycling.rssradar.R
import androidx.compose.ui.platform.LocalContext
import com.cycling.rssradar.i18n.resolve
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.cycling.rssradar.core.data.db.FeedEntity
import com.cycling.rssradar.core.data.store.ListViewMode
import com.cycling.rssradar.core.ui.components.AppSnackbarHost
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.ui.theme.LocalListDisplay

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
    val context = LocalContext.current
    val message = viewModel.uiMessage

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it.resolve(context))
            viewModel.onIntent(FeedArticlesIntent.ConsumeMessage)
        }
    }

    Scaffold(
        containerColor = radarColors().bgRoot,
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = viewModel.feed?.title ?: stringResource(R.string.feed_articles_title),
                        color = radarColors().textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Lucide.ArrowLeft, contentDescription = stringResource(R.string.back), tint = radarColors().textPrimary)
                    }
                },
                actions = {
                    // 单源刷新秒级完成；进行中把图标换成转圈
                    IconButton(onClick = { viewModel.onIntent(FeedArticlesIntent.Refresh) }) {
                        if (viewModel.isRefreshing) {
                            CircularProgressIndicator(
                                color = radarColors().textSecondary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Icon(Lucide.RefreshCw, contentDescription = stringResource(R.string.feed_articles_refresh), tint = radarColors().textPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = radarColors().bgRoot),
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
                // 空态带 CTA（UI 审计 F3）：用户最快的下一步就是刷新
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.feed_articles_empty), color = radarColors().textSecondary)
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = { viewModel.onIntent(FeedArticlesIntent.Refresh) },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.feed_articles_refresh_now))
                    }
                }
            }
        } else if (viewModel.feed?.contentType == FeedEntity.CONTENT_TYPE_IMAGE) {
            // 图片类源（ADR-0014）：两列画廊网格，点击仍走详情
            ImageGalleryGrid(
                articles = viewModel.articles,
                onArticleClick = { item ->
                    viewModel.onIntent(FeedArticlesIntent.MarkRead(item.article.id))
                    onOpenArticle(item.article.id)
                },
                onScrolledToEnd = { viewModel.onIntent(FeedArticlesIntent.LoadMore) },
                bottomPadding = 16.dp,
                modifier = Modifier.padding(padding),
            )
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
                // 单源文章量少（个位数常见），全局网格模式在这里只有大片留白——
                // 仅当全局选了网格时降级为单列列表，其余模式尊重用户偏好
                viewModeOverride = if (LocalListDisplay.current.viewMode == ListViewMode.GRID) {
                    ListViewMode.LIST
                } else {
                    null
                },
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
                        color = radarColors().accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
