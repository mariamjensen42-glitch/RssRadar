package com.cycling.rssradar

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.unit.IntOffset
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
import com.cycling.rssradar.ui.me.CrashLogScreen
import com.cycling.rssradar.ui.me.CrashLogViewModel
import com.cycling.rssradar.ui.me.FetchDiagnosticsScreen
import com.cycling.rssradar.ui.me.FetchDiagnosticsViewModel
import com.cycling.rssradar.ui.me.InterestProfileScreen
import com.cycling.rssradar.ui.me.RssHubSettingsScreen
import com.cycling.rssradar.ui.me.RssHubSettingsViewModel
import com.cycling.rssradar.ui.search.SearchScreen
import com.cycling.rssradar.ui.search.SearchViewModel
import com.cycling.rssradar.ui.subscriptions.SubscriptionsScreen
import com.cycling.rssradar.ui.subscriptions.SubscriptionsViewModel
import com.cycling.rssradar.core.ui.components.FloatingBottomBar
import com.cycling.rssradar.ui.components.openUrl
import com.cycling.rssradar.ui.navigation.ArticleDetailRoute
import com.cycling.rssradar.ui.navigation.CrashLogRoute
import com.cycling.rssradar.ui.navigation.FeedArticlesRoute
import com.cycling.rssradar.ui.navigation.FeedActionRoute
import com.cycling.rssradar.ui.navigation.FeedRoute
import com.cycling.rssradar.ui.navigation.FetchDiagnosticsRoute
import com.cycling.rssradar.ui.navigation.InterestProfileRoute
import com.cycling.rssradar.ui.me.SettingsAiDiagScreen
import com.cycling.rssradar.ui.me.SettingsGeneralScreen
import com.cycling.rssradar.ui.me.SettingsRssHubScreen
import com.cycling.rssradar.ui.me.SettingsSyncScreen
import com.cycling.rssradar.ui.navigation.MeRoute
import com.cycling.rssradar.ui.navigation.SearchRoute
import com.cycling.rssradar.ui.navigation.SettingsAiDiagRoute
import com.cycling.rssradar.ui.navigation.SettingsGeneralRoute
import com.cycling.rssradar.ui.navigation.SettingsRssHubRoute
import com.cycling.rssradar.ui.navigation.SettingsSyncRoute
import com.cycling.rssradar.ui.navigation.SubscriptionsRoute
import com.cycling.rssradar.ui.theme.CompositionLocalRoot
import dagger.hilt.android.AndroidEntryPoint
import com.cycling.rssradar.core.ui.theme.LocalReducedMotion
import com.cycling.rssradar.core.ui.theme.MotionTokens
import com.cycling.rssradar.core.ui.theme.radarColors

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

    Box(modifier = Modifier.fillMaxSize().background(radarColors().bgRoot)) {
        // 页面转场（docs/motion.md #1，issue #72）：前进「新页右滑入 1/12 + fade」，
        // 返回取镜像；280ms emphasized。层级方向感来自横轴位移。
        // reduce-motion：None = 瞬时切换，无位移无淡入。
        val reducedMotion = LocalReducedMotion.current
        val slideSpec = tween<IntOffset>(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasized)
        val fadeSpec = tween<Float>(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasized)
        val enter: EnterTransition = if (reducedMotion) EnterTransition.None
        else slideInHorizontally(slideSpec) { it / 12 } + fadeIn(fadeSpec)
        val exit: ExitTransition = if (reducedMotion) ExitTransition.None
        else slideOutHorizontally(slideSpec) { -it / 12 } + fadeOut(fadeSpec)
        val popEnter: EnterTransition = if (reducedMotion) EnterTransition.None
        else slideInHorizontally(slideSpec) { -it / 12 } + fadeIn(fadeSpec)
        val popExit: ExitTransition = if (reducedMotion) ExitTransition.None
        else slideOutHorizontally(slideSpec) { it / 12 } + fadeOut(fadeSpec)
        NavHost(
            navController = navController,
            startDestination = FeedRoute,
            enterTransition = { enter },
            exitTransition = { exit },
            popEnterTransition = { popEnter },
            popExitTransition = { popExit },
        ) {
            composable<FeedRoute> {
                val vm = hiltViewModel<FeedListViewModel>()
                FeedListScreen(
                    viewModel = vm,
                    onOpenSearch = { navController.navigate(SearchRoute) },
                    onOpenArticle = { navController.navigate(ArticleDetailRoute(it.article.id)) },
                    onAddFeed = { showAddSheet = true },
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
                    onOpenGeneral = { navController.navigate(SettingsGeneralRoute) },
                    onOpenSync = { navController.navigate(SettingsSyncRoute) },
                    onOpenRssHub = { navController.navigate(SettingsRssHubRoute) },
                    onOpenAiDiag = { navController.navigate(SettingsAiDiagRoute) },
                )
            }
            // 设置二级页（主页只留分组入口）：通用 / 同步与清理 / RSSHub / AI 与诊断
            composable<SettingsGeneralRoute> {
                SettingsGeneralScreen(
                    onBack = { navController.popBackStack() },
                    onOpenInterestProfile = { navController.navigate(InterestProfileRoute) },
                )
            }
            composable<SettingsSyncRoute> {
                SettingsSyncScreen(onBack = { navController.popBackStack() })
            }
            composable<SettingsRssHubRoute> {
                SettingsRssHubScreen(onBack = { navController.popBackStack() })
            }
            composable<SettingsAiDiagRoute> {
                SettingsAiDiagScreen(
                    onBack = { navController.popBackStack() },
                    onOpenFetchDiagnostics = { navController.navigate(FetchDiagnosticsRoute) },
                    onOpenCrashLog = { navController.navigate(CrashLogRoute) },
                )
            }
            // 兴趣画像（ADR-0013）：推荐流的可解释性出口
            composable<InterestProfileRoute> {
                InterestProfileScreen(onBack = { navController.popBackStack() })
            }
            composable<FetchDiagnosticsRoute> {
                FetchDiagnosticsScreen(
                    viewModel = hiltViewModel<FetchDiagnosticsViewModel>(),
                    onBack = { navController.popBackStack() },
                )
            }
            // 崩溃日志（issue #61）
            composable<CrashLogRoute> {
                CrashLogScreen(
                    viewModel = hiltViewModel<CrashLogViewModel>(),
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
