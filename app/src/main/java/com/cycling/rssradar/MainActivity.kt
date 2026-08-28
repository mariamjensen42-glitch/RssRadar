package com.cycling.rssradar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.cycling.rssradar.ui.AddSubscriptionScreen
import com.cycling.rssradar.ui.AddSubscriptionViewModel
import com.cycling.rssradar.ui.ArticleDetailScreen
import com.cycling.rssradar.ui.ArticleDetailViewModel
import com.cycling.rssradar.ui.FeedListScreen
import com.cycling.rssradar.ui.FeedListViewModel
import com.cycling.rssradar.ui.SearchScreen
import com.cycling.rssradar.ui.SearchViewModel
import com.cycling.rssradar.ui.SubscriptionsScreen
import com.cycling.rssradar.ui.SubscriptionsViewModel
import com.cycling.rssradar.ui.components.FloatingBottomBar
import com.cycling.rssradar.ui.components.MainTab
import com.cycling.rssradar.ui.theme.BgRoot
import com.cycling.rssradar.ui.theme.RssRadarTheme
import com.cycling.rssradar.ui.theme.TextSecondary
import com.cycling.rssradar.ui.theme.TextTertiary

class MainActivity : ComponentActivity() {

    private val feedVm: FeedListViewModel by viewModels {
        FeedListViewModel.factory((application as RssRadarApp).container)
    }
    private val subsVm: SubscriptionsViewModel by viewModels {
        SubscriptionsViewModel.factory((application as RssRadarApp).container)
    }
    private val addVm: AddSubscriptionViewModel by viewModels {
        AddSubscriptionViewModel.factory((application as RssRadarApp).container)
    }
    private val searchVm: SearchViewModel by viewModels {
        SearchViewModel.factory((application as RssRadarApp).container)
    }
    private val articleVm: ArticleDetailViewModel by viewModels {
        ArticleDetailViewModel.factory((application as RssRadarApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RssRadarTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RssRadarApp(
                        feedVm = feedVm,
                        subsVm = subsVm,
                        addVm = addVm,
                        searchVm = searchVm,
                        articleVm = articleVm,
                    )
                }
            }
        }
    }
}

@Composable
private fun RssRadarApp(
    feedVm: FeedListViewModel,
    subsVm: SubscriptionsViewModel,
    addVm: AddSubscriptionViewModel,
    searchVm: SearchViewModel,
    articleVm: ArticleDetailViewModel,
) {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.Feed) }
    var overlay by rememberSaveable(stateSaver = OverlayRouteSaver) { mutableStateOf<OverlayRoute?>(null) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(BgRoot)) {
        when (currentTab) {
            MainTab.Feed -> FeedListScreen(
                viewModel = feedVm,
                onOpenSearch = { currentTab = MainTab.Search },
                onOpenArticle = { overlay = OverlayRoute.ArticleDetail(it.article.id) },
            )
            MainTab.Subscriptions -> SubscriptionsScreen(
                viewModel = subsVm,
                onAddSubscription = { overlay = OverlayRoute.AddSubscription },
                onCreateGroup = { /* TODO */ },
            )
            MainTab.Search -> SearchScreen(
                viewModel = searchVm,
                onOpenArticle = { overlay = OverlayRoute.ArticleDetail(it.article.id) },
            )
            MainTab.Me -> MeTabPlaceholder()
        }

        // 顶层浮层：详情 / 添加订阅。底部 TabBar 在浮层时隐藏。
        if (overlay != null) {
            Box(modifier = Modifier.fillMaxSize().background(BgRoot)) {
                when (val o = overlay) {
                    is OverlayRoute.ArticleDetail -> ArticleDetailScreen(
                        viewModel = articleVm,
                        articleId = o.articleId,
                        onBack = { overlay = null },
                        onOpenOriginal = { url -> context.openUrl(url) },
                    )
                    OverlayRoute.AddSubscription -> AddSubscriptionScreen(
                        viewModel = addVm,
                        onBack = { overlay = null },
                    )

                    else -> {}
                }
            }
        } else {
            FloatingBottomBar(
                current = currentTab,
                onTabSelected = { currentTab = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun MeTabPlaceholder() {
    Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "「我的」页面正在搭建中",
            color = TextSecondary,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "后续会展示：设置、阅读统计、同步",
            color = TextTertiary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private sealed interface OverlayRoute {
    data class ArticleDetail(val articleId: Long) : OverlayRoute
    data object AddSubscription : OverlayRoute
}

/** 用系统浏览器打开外链。失败要让用户看见，不能静默吞掉。 */
private fun Context.openUrl(url: String) {
    if (url.isBlank()) {
        Toast.makeText(this, "该文章没有可用链接", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
        .onFailure { Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show() }
}

private val OverlayRouteSaver: androidx.compose.runtime.saveable.Saver<OverlayRoute?, Any> =
    androidx.compose.runtime.saveable.listSaver(
        save = { route ->
            when (route) {
                null -> emptyList()
                is OverlayRoute.ArticleDetail -> listOf("article", route.articleId)
                OverlayRoute.AddSubscription -> listOf("add")
            }
        },
        restore = { values ->
            when (values.firstOrNull()) {
                "article" -> OverlayRoute.ArticleDetail((values.getOrNull(1) as? Long) ?: 0L)
                "add" -> OverlayRoute.AddSubscription
                else -> null
            }
        },
    )
