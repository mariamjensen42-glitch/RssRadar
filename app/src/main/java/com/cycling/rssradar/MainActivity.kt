package com.cycling.rssradar

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cycling.rssradar.ui.addsubscription.AddSubscriptionSheet
import com.cycling.rssradar.ui.addsubscription.AddSubscriptionViewModel
import com.cycling.rssradar.ui.article.ArticleDetailScreen
import com.cycling.rssradar.ui.article.ArticleDetailViewModel
import com.cycling.rssradar.ui.subscriptions.FeedActionScreen
import com.cycling.rssradar.ui.feed.FeedArticlesScreen
import com.cycling.rssradar.ui.feed.FeedArticlesViewModel
import com.cycling.rssradar.ui.feed.FeedListScreen
import com.cycling.rssradar.ui.feed.FeedListViewModel
import com.cycling.rssradar.ui.me.FetchDiagnosticsScreen
import com.cycling.rssradar.ui.me.FetchDiagnosticsViewModel
import com.cycling.rssradar.ui.me.RssHubSettingsScreen
import com.cycling.rssradar.ui.me.RssHubSettingsViewModel
import com.cycling.rssradar.ui.search.SearchScreen
import com.cycling.rssradar.ui.search.SearchViewModel
import com.cycling.rssradar.ui.subscriptions.SubscriptionsScreen
import com.cycling.rssradar.ui.subscriptions.SubscriptionsViewModel
import com.cycling.rssradar.ui.components.FloatingBottomBar
import com.cycling.rssradar.ui.components.openUrl
import com.cycling.rssradar.ui.navigation.ArticleDetailRoute
import com.cycling.rssradar.ui.navigation.FeedArticlesRoute
import com.cycling.rssradar.ui.navigation.FeedActionRoute
import com.cycling.rssradar.ui.navigation.FeedRoute
import com.cycling.rssradar.ui.navigation.FetchDiagnosticsRoute
import com.cycling.rssradar.ui.navigation.MeRoute
import com.cycling.rssradar.ui.navigation.SearchRoute
import com.cycling.rssradar.ui.navigation.SubscriptionsRoute
import com.cycling.rssradar.ui.theme.BgRoot
import com.cycling.rssradar.ui.theme.CompositionLocalRoot
import dagger.hilt.android.AndroidEntryPoint

/**
 * 纯壳 Activity：edge-to-edge + 组合根。启动副作用在 [RssRadarApp]，
 * 全局 CompositionLocal 注入在 ui.theme.CompositionLocalRoot，导航图在此。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalRoot {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RssRadarAppContent()
                }
            }
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun RssRadarAppContent() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // 加订阅抽屉显隐：纯弹层，不入导航栈（无路由语义、不参与返回栈）。
    var showAddSheet by remember { mutableStateOf(false) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val selectedTab: String? = when {
        currentDestination?.hasRoute<FeedRoute>() == true -> "feed"
        currentDestination?.hasRoute<SubscriptionsRoute>() == true -> "subs"
        currentDestination?.hasRoute<SearchRoute>() == true -> "search"
        currentDestination?.hasRoute<MeRoute>() == true -> "me"
        else -> null
    }

    Box(modifier = Modifier.fillMaxSize().background(BgRoot)) {
        NavHost(navController = navController, startDestination = FeedRoute) {
            composable<FeedRoute> {
                val vm = hiltViewModel<FeedListViewModel>()
                FeedListScreen(
                    viewModel = vm,
                    onOpenSearch = { navController.navigate(SearchRoute) },
                    onOpenArticle = { navController.navigate(ArticleDetailRoute(it.article.id)) },
                )
            }
            composable<SubscriptionsRoute> {
                val vm = hiltViewModel<SubscriptionsViewModel>()
                SubscriptionsScreen(
                    viewModel = vm,
                    onAddSubscription = { showAddSheet = true },
                    onCreateGroup = { /* TODO */ },
                    onFeedAction = { navController.navigate(FeedActionRoute(it)) },
                    onOpenFeed = { navController.navigate(FeedArticlesRoute(it)) },
                )
            }
            composable<SearchRoute> {
                val vm = hiltViewModel<SearchViewModel>()
                SearchScreen(
                    viewModel = vm,
                    onOpenArticle = { navController.navigate(ArticleDetailRoute(it.article.id)) },
                )
            }
            composable<MeRoute> {
                val vm = hiltViewModel<RssHubSettingsViewModel>()
                RssHubSettingsScreen(
                    viewModel = vm,
                    onOpenFetchDiagnostics = { navController.navigate(FetchDiagnosticsRoute) },
                )
            }
            composable<FetchDiagnosticsRoute> {
                FetchDiagnosticsScreen(
                    viewModel = hiltViewModel<FetchDiagnosticsViewModel>(),
                    onBack = { navController.popBackStack() },
                )
            }
            // deepLink rssradar://article/{id}（issue #32）：manifest intent-filter 把
            // 外部 intent 送进本 Activity，NavHost 自动解析 initial intent 落到此目的地。
            composable<ArticleDetailRoute>(
                deepLinks = listOf(navDeepLink { uriPattern = "rssradar://article/{articleId}" }),
            ) { backStackEntry ->
                val articleId = backStackEntry.toRoute<ArticleDetailRoute>().articleId
                ArticleDetailScreen(
                    viewModel = hiltViewModel<ArticleDetailViewModel>(),
                    articleId = articleId,
                    onBack = { navController.popBackStack() },
                    onOpenOriginal = { url -> context.openUrl(url) },
                )
            }
            composable<FeedActionRoute> { backStackEntry ->
                val feedId = backStackEntry.toRoute<FeedActionRoute>().feedId
                FeedActionScreen(
                    feedId = feedId,
                    viewModel = hiltViewModel<SubscriptionsViewModel>(),
                    onDismiss = { navController.popBackStack() },
                )
            }
            // 订阅源文章列表（issue #51）：订阅源清单整行点击进入
            composable<FeedArticlesRoute> { backStackEntry ->
                FeedArticlesScreen(
                    viewModel = hiltViewModel<FeedArticlesViewModel>(),
                    onBack = { navController.popBackStack() },
                    onOpenArticle = { navController.navigate(ArticleDetailRoute(it)) },
                )
            }
        }

        // 加订阅抽屉：ModalBottomSheet 自带窗口级弹层与返回拦截（BackHandler），
        // 不需要 NavHost 承载。VM 挂在 Activity 作用域，关闭时手动重置流程状态。
        if (showAddSheet) {
            val addVm: AddSubscriptionViewModel = hiltViewModel()
            AddSubscriptionSheet(
                viewModel = addVm,
                onDismiss = {
                    addVm.onDismissed()
                    showAddSheet = false
                },
            )
        }

        if (selectedTab != null) {
            FloatingBottomBar(
                currentRoute = selectedTab,
                onTabSelected = { key ->
                    val route = when (key) {
                        "feed" -> FeedRoute
                        "subs" -> SubscriptionsRoute
                        "search" -> SearchRoute
                        "me" -> MeRoute
                        else -> return@FloatingBottomBar
                    }
                    navController.navigate(route) {
                        popUpTo<FeedRoute> { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
