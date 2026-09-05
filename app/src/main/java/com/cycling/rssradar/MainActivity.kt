package com.cycling.rssradar

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cycling.rssradar.ui.addsubscription.AddSubscriptionSheet
import com.cycling.rssradar.ui.addsubscription.AddSubscriptionViewModel
import com.cycling.rssradar.ui.me.AiArtifactsScreen
import com.cycling.rssradar.ui.me.AiFeaturesScreen
import com.cycling.rssradar.ui.me.AiFeaturesViewModel
import com.cycling.rssradar.ui.me.PromptTemplatesScreen
import com.cycling.rssradar.ui.article.ArticleDetailScreen
import com.cycling.rssradar.ui.article.ArticleDetailViewModel
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
import com.cycling.rssradar.ui.me.ReadingStatsScreen
import com.cycling.rssradar.ui.me.ReadingStatsViewModel
import com.cycling.rssradar.ui.search.SearchScreen
import com.cycling.rssradar.ui.search.SearchViewModel
import com.cycling.rssradar.ui.subscriptions.SubscriptionsScreen
import com.cycling.rssradar.ui.subscriptions.SubscriptionsViewModel
import com.cycling.rssradar.core.ui.components.FloatingBottomBar
import com.cycling.rssradar.ui.components.openUrl
import com.cycling.rssradar.ui.navigation.AiArtifactsRoute
import com.cycling.rssradar.ui.navigation.PromptTemplatesRoute
import com.cycling.rssradar.ui.navigation.AiFeaturesRoute
import com.cycling.rssradar.ui.navigation.ArticleDetailRoute
import com.cycling.rssradar.ui.navigation.CrashLogRoute
import com.cycling.rssradar.ui.navigation.FeedArticlesRoute
import com.cycling.rssradar.ui.navigation.FeedRoute
import com.cycling.rssradar.ui.navigation.FetchDiagnosticsRoute
import com.cycling.rssradar.ui.navigation.InterestProfileRoute
import com.cycling.rssradar.ui.me.SettingsAiDiagScreen
import com.cycling.rssradar.ui.me.SettingsGeneralScreen
import com.cycling.rssradar.ui.me.SettingsRssHubScreen
import com.cycling.rssradar.ui.me.SettingsSyncScreen
import com.cycling.rssradar.ui.navigation.MeRoute
import com.cycling.rssradar.ui.navigation.ReadingStatsRoute
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
        // 页面转场（docs/motion.md #1，issue #72）分两层：
        // - 层级导航（列表→详情这类）：前进「新页右滑入 1/12 + fade」（280ms emphasized），
        //   返回镜像；退场用 200ms——退场比进场快，转场才跟手，双向同速必然显拖。
        // - 顶层 tab 互切（Feed/订阅/搜索/我的）：同级没有方向语义，滑左右是假动作，
        //   统一 200ms crossfade。
        // - reduce-motion：None = 瞬时切换。
        val reducedMotion = LocalReducedMotion.current
        val enterSlide = tween<IntOffset>(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasized)
        val enterFade = tween<Float>(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasized)
        val exitSlide = tween<IntOffset>(MotionTokens.DurationShort, easing = MotionTokens.EasingEmphasized)
        val exitFade = tween<Float>(MotionTokens.DurationShort, easing = MotionTokens.EasingEmphasized)
        val topLevelRoutes = listOf(
            FeedRoute::class,
            SubscriptionsRoute::class,
            SearchRoute::class,
            MeRoute::class,
        )
        val isTabSwitch: AnimatedContentTransitionScope<NavBackStackEntry>.() -> Boolean = {
            topLevelRoutes.any { initialState.destination.hasRoute(it) } &&
                topLevelRoutes.any { targetState.destination.hasRoute(it) }
        }
        NavHost(
            navController = navController,
            startDestination = FeedRoute,
            enterTransition = {
                when {
                    reducedMotion -> EnterTransition.None
                    isTabSwitch() -> fadeIn(enterFade)
                    else -> slideInHorizontally(enterSlide) { it / 12 } + fadeIn(enterFade)
                }
            },
            exitTransition = {
                when {
                    reducedMotion -> ExitTransition.None
                    isTabSwitch() -> fadeOut(exitFade)
                    else -> slideOutHorizontally(exitSlide) { -it / 12 } + fadeOut(exitFade)
                }
            },
            popEnterTransition = {
                when {
                    reducedMotion -> EnterTransition.None
                    isTabSwitch() -> fadeIn(enterFade)
                    else -> slideInHorizontally(enterSlide) { -it / 12 } + fadeIn(enterFade)
                }
            },
            popExitTransition = {
                when {
                    reducedMotion -> ExitTransition.None
                    isTabSwitch() -> fadeOut(exitFade)
                    else -> slideOutHorizontally(exitSlide) { it / 12 } + fadeOut(exitFade)
                }
            },
        ) {
            composable<FeedRoute> {
                val vm = hiltViewModel<FeedListViewModel>()
                FeedListScreen(
                    viewModel = vm,
                    onOpenSearch = { navController.navigate(SearchRoute) },
                    onOpenArticle = { navController.navigate(ArticleDetailRoute(it.article.id)) },
                    onAddFeed = { showAddSheet = true },
                    // 新用户空态第二入口：OPML 导入的 SAF 入口在订阅页顶栏菜单里
                    onOpenSubscriptions = { navController.navigate(SubscriptionsRoute) },
                )
            }
            composable<SubscriptionsRoute> {
                val vm = hiltViewModel<SubscriptionsViewModel>()
                SubscriptionsScreen(
                    viewModel = vm,
                    onAddSubscription = { showAddSheet = true },
                    onCreateGroup = { /* TODO */ },
                    onOpenFeed = { navController.navigate(FeedArticlesRoute(it)) },
                )
            }
            composable<SearchRoute> {
                val vm = hiltViewModel<SearchViewModel>()
                SearchScreen(
                    viewModel = vm,
                    onOpenArticle = { navController.navigate(ArticleDetailRoute(it.article.id)) },
                    onOpenSubscriptions = { navController.navigate(SubscriptionsRoute) },
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
                    onOpenReadingStats = { navController.navigate(ReadingStatsRoute) },
                )
            }
            // 阅读统计仪表盘（issue #83）：近 7 天阅读行为的真实数字
            composable<ReadingStatsRoute> {
                ReadingStatsScreen(onBack = { navController.popBackStack() })
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
                    onOpenAiFeatures = { navController.navigate(AiFeaturesRoute) },
                    onOpenAiArtifacts = { navController.navigate(AiArtifactsRoute()) },
                    onOpenPromptTemplates = { navController.navigate(PromptTemplatesRoute) },
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
            // AI 智能功能总览：35 项独立开关、用量看板、任务队列
            composable<AiFeaturesRoute> {
                AiFeaturesScreen(
                    viewModel = hiltViewModel<AiFeaturesViewModel>(),
                    onBack = { navController.popBackStack() },
                    onOpenArtifacts = { featureDbValue ->
                        navController.navigate(AiArtifactsRoute(featureDbValue = featureDbValue))
                    },
                )
            }
            // AI 产物中心：全部功能的生成结果，按功能筛选后查看
            composable<AiArtifactsRoute> { backStackEntry ->
                val artifactsRoute = backStackEntry.toRoute<AiArtifactsRoute>()
                AiArtifactsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenArticle = { navController.navigate(ArticleDetailRoute(it)) },
                    onOpenFeed = { navController.navigate(FeedArticlesRoute(it)) },
                    initialFeatureDbValue = artifactsRoute.featureDbValue,
                )
            }
            // 提示词模板管理（AiFeature.PROMPT_TEMPLATE）：内置模板预览 + 单源覆盖集中管理
            composable<PromptTemplatesRoute> {
                PromptTemplatesScreen(
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
                val navArticleId = backStackEntry.toRoute<ArticleDetailRoute>().articleId
                ArticleDetailScreen(
                    viewModel = hiltViewModel<ArticleDetailViewModel>(),
                    articleId = navArticleId,
                    onBack = { navController.popBackStack() },
                    onOpenOriginal = { url -> context.openUrl(url) },
                    onOpenArticle = { navController.navigate(ArticleDetailRoute(it)) },
                )
            }
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
                    // 目标 tab 已在返回栈里（如顶栏搜索图标把 SearchRoute 裸 push 在
                    // Feed 之上）时，必须直接 pop 回去。走 navigate + popUpTo(saveState)
                    // + restoreState 会把刚弹出的栈存档又原样还原，表现为「点了 tab
                    // 还停在旧页面」。只有目标 tab 不在栈里才走标准 tab 导航。
                    val (route, routeClass) = when (key) {
                        "feed" -> FeedRoute to FeedRoute::class
                        "subs" -> SubscriptionsRoute to SubscriptionsRoute::class
                        "search" -> SearchRoute to SearchRoute::class
                        "me" -> MeRoute to MeRoute::class
                        else -> return@FloatingBottomBar
                    }
                    val inBackStack = navController.currentBackStack.value
                        .any { it.destination.hasRoute(routeClass) }
                    if (inBackStack && navController.popBackStack(route, inclusive = false)) {
                        return@FloatingBottomBar
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
