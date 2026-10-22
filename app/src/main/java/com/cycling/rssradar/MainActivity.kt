package com.cycling.rssradar

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cycling.rssradar.ui.addsubscription.AddSubscriptionDestination
import com.cycling.rssradar.ui.addsubscription.SharedText
import com.cycling.rssradar.ui.addsubscription.sharedText
import com.cycling.rssradar.ui.ai.AiArtifactsDestination
import com.cycling.rssradar.ui.ai.AiFeaturesDestination
import com.cycling.rssradar.ui.ai.PromptTemplatesDestination
import com.cycling.rssradar.ui.article.ArticleDetailDestination
import com.cycling.rssradar.ui.annotations.AnnotationsDestination
import com.cycling.rssradar.ui.library.LibraryDestination
import com.cycling.rssradar.ui.player.AudioPlayerDestination
import com.cycling.rssradar.ui.feed.FeedArticlesDestination
import com.cycling.rssradar.ui.feed.FeedListDestination
import com.cycling.rssradar.ui.me.CrashLogDestination
import com.cycling.rssradar.ui.me.FetchDiagnosticsDestination
import com.cycling.rssradar.ui.ai.InterestProfileDestination
import com.cycling.rssradar.ui.settings.RssHubSettingsDestination
import com.cycling.rssradar.ui.me.ReadingStatsDestination
import com.cycling.rssradar.ui.search.SearchDestination
import com.cycling.rssradar.ui.subscriptions.SubscriptionsDestination
import com.cycling.rssradar.core.ui.components.FloatingBottomBar
import com.cycling.rssradar.core.data.platform.openUrl
import com.cycling.rssradar.core.navigation.AddSubscriptionRoute
import com.cycling.rssradar.core.navigation.AiArtifactsRoute
import com.cycling.rssradar.core.navigation.AnnotationsRoute
import com.cycling.rssradar.core.navigation.LibraryRoute
import com.cycling.rssradar.core.navigation.PromptTemplatesRoute
import com.cycling.rssradar.core.navigation.AiFeaturesRoute
import com.cycling.rssradar.core.navigation.ArticleDetailRoute
import com.cycling.rssradar.core.navigation.AudioPlayerRoute
import com.cycling.rssradar.core.navigation.CrashLogRoute
import com.cycling.rssradar.core.navigation.FeedArticlesRoute
import com.cycling.rssradar.core.navigation.FeedRoute
import com.cycling.rssradar.core.navigation.FetchDiagnosticsRoute
import com.cycling.rssradar.core.navigation.InterestProfileRoute
import com.cycling.rssradar.ui.settings.SettingsAiDiagDestination
import com.cycling.rssradar.ui.settings.SettingsGeneralDestination
import com.cycling.rssradar.ui.settings.SettingsRssHubDestination
import com.cycling.rssradar.ui.settings.SettingsSyncDestination
import com.cycling.rssradar.core.navigation.MeRoute
import com.cycling.rssradar.core.navigation.ReadingStatsRoute
import com.cycling.rssradar.core.navigation.SearchRoute
import com.cycling.rssradar.core.navigation.SettingsAiDiagRoute
import com.cycling.rssradar.core.navigation.SettingsGeneralRoute
import com.cycling.rssradar.core.navigation.SettingsRssHubRoute
import com.cycling.rssradar.core.navigation.SettingsSyncRoute
import com.cycling.rssradar.core.navigation.SubscriptionsRoute
import com.cycling.rssradar.core.navigation.BackupRoute
import com.cycling.rssradar.core.navigation.FilterRulesRoute
import com.cycling.rssradar.core.navigation.SettingsNotificationRoute
import com.cycling.rssradar.ui.me.BackupDestination
import com.cycling.rssradar.ui.settings.FilterRulesDestination
import com.cycling.rssradar.ui.settings.NotificationSettingsDestination
import com.cycling.rssradar.ui.theme.CompositionLocalRoot
import dagger.hilt.android.AndroidEntryPoint
import com.cycling.rssradar.core.ui.theme.LocalReducedMotion
import com.cycling.rssradar.core.ui.theme.fastEffectsSpec
import com.cycling.rssradar.core.ui.theme.fastSpatialSpec
import com.cycling.rssradar.core.ui.theme.spatialSpec

/**
 * 纯壳 Activity：edge-to-edge + 组合根。启动副作用在 [RssRadarApp]，
 * 全局 CompositionLocal 注入在 ui.theme.CompositionLocalRoot，导航图在此。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * 外部送进来的链接（系统分享 / 选中文字，#34）。Compose 状态放在 Activity：
     * 已在后台时系统走 [onNewIntent] 而不是重建，只有状态驱动才能让已在前台的
     * Compose 树收到它——写进 savedInstanceState 的话用户在界面上看不到任何反应。
     */
    private var sharedUrl by mutableStateOf<String?>(null)

    /** 界面语言覆盖：API 31/32 无系统 per-app locale，attach 时手动包一层。 */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(
            com.cycling.rssradar.core.data.platform.AppLocales.wrapContext(
                newBase,
                com.cycling.rssradar.core.data.store.prefs.SettingsPrefs.of(newBase),
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeIncomingIntent(intent)
        enableEdgeToEdge()
        setContent {
            CompositionLocalRoot {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RssRadarAppContent(
                        sharedUrl = sharedUrl,
                        onSharedUrlConsumed = { sharedUrl = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeIncomingIntent(intent)
    }

    /**
     * 只认链接：有文本但挑不出 URL 时如实告诉用户，而不是把整段话塞进地址栏
     * （那只会换来一个必失败的探测和一句看不懂的错误）。
     */
    private fun consumeIncomingIntent(incoming: Intent?) {
        val text = incoming?.sharedText() ?: return
        val url = SharedText.extractUrl(text)
        if (url == null) {
            Toast.makeText(this, getString(R.string.share_no_link), Toast.LENGTH_LONG).show()
            return
        }
        sharedUrl = url
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun RssRadarAppContent(
    /** 系统分享/选中文字送进来的链接（#34），消费后由 [onSharedUrlConsumed] 清空。 */
    sharedUrl: String? = null,
    onSharedUrlConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // 外部分享链接的一次性预填地址：加订阅页拿不到外部 intent，由这里暂存、进页面消费掉即清空。
    // 不走路由参数是因为导航对 String 参数需要编码处理（全仓无先例），为一条预填不值当。
    var prefillUrl by remember { mutableStateOf<String?>(null) }
    // 同一条思路：AI 结果卡里的词要带去搜索页。同样一次性、进页面即消费
    // （留在状态里会让旋转屏幕或任何一次重组把同一个词再搜一遍）。
    var searchPrefill by remember { mutableStateOf<String?>(null) }
    // 外部链接 → 直接进加订阅页并预填。清空 sharedUrl 是必须的：否则旋转屏幕
    // 或任何一次重组都会把同一个地址再填一遍（还会打断用户已经改过的输入）。
    LaunchedEffect(sharedUrl) {
        val url = sharedUrl ?: return@LaunchedEffect
        prefillUrl = url
        navController.navigate(AddSubscriptionRoute)
        onSharedUrlConsumed()
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val selectedTab: String? = when {
        currentDestination?.hasRoute<FeedRoute>() == true -> "feed"
        currentDestination?.hasRoute<SubscriptionsRoute>() == true -> "subs"
        currentDestination?.hasRoute<MeRoute>() == true -> "me"
        else -> null
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // 页面转场（docs/motion.md #1，issue #72）分两层：
        // - 层级导航（列表→详情这类）：前进「新页右滑入 1/12 + fade」，返回镜像；
        //   退场用官方 fast 档——退场比进场快，转场才跟手，双向同速必然显拖。
        // - 顶层 tab 互切（Feed/订阅/我的）：同级没有方向语义，滑左右是假动作，
        //   统一淡入淡出。
        // - reduce-motion：None = 瞬时切换。
        // 规格来自 M3 Expressive 官方 MotionScheme：空间位移走 spatial 族，淡入淡出走 effects 族。
        val reducedMotion = LocalReducedMotion.current
        val enterSlide = spatialSpec<IntOffset>()
        val enterFade = spatialSpec<Float>()
        val exitSlide = fastSpatialSpec<IntOffset>()
        val exitFade = fastEffectsSpec()
        val topLevelRoutes = listOf(
            FeedRoute::class,
            SubscriptionsRoute::class,
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
                FeedListDestination(
                    onOpenSearch = { navController.navigate(SearchRoute) },
                    onOpenArticle = { navController.navigate(ArticleDetailRoute(it.article.id)) },
                    onAddFeed = { navController.navigate(AddSubscriptionRoute) },
                    // 新用户空态第二入口：OPML 导入的 SAF 入口在订阅页顶栏菜单里
                    onOpenSubscriptions = { navController.navigate(SubscriptionsRoute) },
                )
            }
            composable<SubscriptionsRoute> {
                SubscriptionsDestination(
                    onAddSubscription = { navController.navigate(AddSubscriptionRoute) },
                    onOpenFeed = { navController.navigate(FeedArticlesRoute(it)) },
                )
            }
            composable<SearchRoute> {
                SearchDestination(
                    onBack = { navController.popBackStack() },
                    onOpenArticle = { navController.navigate(ArticleDetailRoute(it.article.id)) },
                    onOpenSubscriptions = { navController.navigate(SubscriptionsRoute) },
                    initialQuery = searchPrefill,
                    onInitialQueryConsumed = { searchPrefill = null },
                )
            }
            composable<AddSubscriptionRoute> {
                AddSubscriptionDestination(
                    onDone = { navController.popBackStack() },
                    prefillUrl = prefillUrl,
                    onPrefillConsumed = { prefillUrl = null },
                )
            }
            composable<MeRoute> {
                RssHubSettingsDestination(
                    onOpenGeneral = { navController.navigate(SettingsGeneralRoute) },
                    onOpenSync = { navController.navigate(SettingsSyncRoute) },
                    onOpenRssHub = { navController.navigate(SettingsRssHubRoute) },
                    onOpenAiDiag = { navController.navigate(SettingsAiDiagRoute) },
                    onOpenReadingStats = { navController.navigate(ReadingStatsRoute) },
                    onOpenFilterRules = { navController.navigate(FilterRulesRoute) },
                    onOpenBackup = { navController.navigate(BackupRoute) },
                    onOpenNotification = { navController.navigate(SettingsNotificationRoute) },
                    onOpenAnnotations = { navController.navigate(AnnotationsRoute) },
                    onOpenLibrary = { navController.navigate(LibraryRoute) },
                )
            }
            // 阅读统计仪表盘（issue #83）：近 7 天阅读行为的真实数字
            composable<ReadingStatsRoute> {
                ReadingStatsDestination(onBack = { navController.popBackStack() })
            }
            // 设置二级页（主页只留分组入口）：通用 / 同步与清理 / RSSHub / AI 与诊断
            composable<SettingsGeneralRoute> {
                SettingsGeneralDestination(
                    onBack = { navController.popBackStack() },
                    onOpenInterestProfile = { navController.navigate(InterestProfileRoute) },
                )
            }
            composable<SettingsSyncRoute> {
                SettingsSyncDestination(onBack = { navController.popBackStack() })
            }
            composable<SettingsRssHubRoute> {
                SettingsRssHubDestination(onBack = { navController.popBackStack() })
            }
            composable<SettingsAiDiagRoute> {
                SettingsAiDiagDestination(
                    onBack = { navController.popBackStack() },
                    onOpenAiFeatures = { navController.navigate(AiFeaturesRoute) },
                    onOpenAiArtifacts = { navController.navigate(AiArtifactsRoute()) },
                    onOpenPromptTemplates = { navController.navigate(PromptTemplatesRoute) },
                    // 兴趣画像在「通用 → 推荐流」下也有入口（那里是"配置推荐"的上下文）；
                    // 这里再给一个是因为它是 AI 产物的展示面，从 AI 的角度看不能缺席。
                    onOpenInterestProfile = { navController.navigate(InterestProfileRoute) },
                    onOpenFetchDiagnostics = { navController.navigate(FetchDiagnosticsRoute) },
                    onOpenCrashLog = { navController.navigate(CrashLogRoute) },
                )
            }
            // 八项功能新增的二级页：过滤规则 / 备份与恢复 / 通知细粒度
            composable<FilterRulesRoute> {
                FilterRulesDestination(
                    onBack = { navController.popBackStack() },
                    // 同一个 feature 模块内的兄弟页：AI 生成的开关在 AI 与诊断页，
                    // 未开启时从规则页直达，省掉"自己找回去"的一段路。
                    onOpenAiSettings = { navController.navigate(SettingsAiDiagRoute) },
                )
            }
            composable<BackupRoute> {
                BackupDestination(onBack = { navController.popBackStack() })
            }
            composable<SettingsNotificationRoute> {
                NotificationSettingsDestination(onBack = { navController.popBackStack() })
            }
            // 我的标注（全库高亮与笔记）：阅读页溢出菜单与「我的」页都能进
            composable<AnnotationsRoute> {
                AnnotationsDestination(
                    onBack = { navController.popBackStack() },
                    onOpenArticle = { navController.navigate(ArticleDetailRoute(it)) },
                )
            }
            // 收藏整理：收藏与稍后读共用一套排序/筛选/批量操作
            composable<LibraryRoute> {
                LibraryDestination(
                    onBack = { navController.popBackStack() },
                    onOpenArticle = { navController.navigate(ArticleDetailRoute(it)) },
                )
            }
            // 兴趣画像：推荐流的可解释性出口
            composable<InterestProfileRoute> {
                InterestProfileDestination(onBack = { navController.popBackStack() })
            }
            composable<FetchDiagnosticsRoute> {
                FetchDiagnosticsDestination(
                    onBack = { navController.popBackStack() },
                )
            }
            // AI 智能功能总览：12 项独立开关、用量看板、任务队列
            composable<AiFeaturesRoute> {
                AiFeaturesDestination(
                    onBack = { navController.popBackStack() },
                    onOpenArtifacts = { featureDbValue ->
                        navController.navigate(AiArtifactsRoute(featureDbValue = featureDbValue))
                    },
                )
            }
            // AI 产物中心：全部功能的生成结果，按功能筛选后查看
            composable<AiArtifactsRoute> { backStackEntry ->
                val artifactsRoute = backStackEntry.toRoute<AiArtifactsRoute>()
                AiArtifactsDestination(
                    onBack = { navController.popBackStack() },
                    onOpenArticle = { navController.navigate(ArticleDetailRoute(it)) },
                    onOpenFeed = { navController.navigate(FeedArticlesRoute(it)) },
                    initialFeatureDbValue = artifactsRoute.featureDbValue,
                )
            }
            // 提示词模板管理（AiFeature.PROMPT_TEMPLATE）：内置模板预览 + 单源覆盖集中管理
            composable<PromptTemplatesRoute> {
                PromptTemplatesDestination(
                    onBack = { navController.popBackStack() },
                )
            }
            // 崩溃日志（issue #61）
            composable<CrashLogRoute> {
                CrashLogDestination(
                    onBack = { navController.popBackStack() },
                )
            }
            // deepLink rssradar://article/{id}（issue #32）：manifest intent-filter 把
            // 外部 intent 送进本 Activity，NavHost 自动解析 initial intent 落到此目的地。
            composable<ArticleDetailRoute>(
                deepLinks = listOf(navDeepLink { uriPattern = "rssradar://article/{articleId}" }),
            ) { backStackEntry ->
                val navArticleId = backStackEntry.toRoute<ArticleDetailRoute>().articleId
                ArticleDetailDestination(
                    articleId = navArticleId,
                    onBack = { navController.popBackStack() },
                    onOpenOriginal = { url -> context.openUrl(url) },
                    onOpenArticle = { navController.navigate(ArticleDetailRoute(it)) },
                    onOpenAnnotations = { navController.navigate(AnnotationsRoute) },
                    onOpenAudio = { navController.navigate(AudioPlayerRoute(articleId = it)) },
                    // 先写预填状态再导航：SearchRoute 首次组合时就要读到它
                    onSearch = { query ->
                        searchPrefill = query
                        navController.navigate(SearchRoute)
                    },
                )
            }
            // 音频/播客播放页：播放器活在 PlaybackService 里，本页只是它的视图
            composable<AudioPlayerRoute> { backStackEntry ->
                AudioPlayerDestination(
                    articleId = backStackEntry.toRoute<AudioPlayerRoute>().articleId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<FeedArticlesRoute> { backStackEntry ->
                FeedArticlesDestination(
                    onBack = { navController.popBackStack() },
                    onOpenArticle = { navController.navigate(ArticleDetailRoute(it)) },
                )
            }
        }

        if (selectedTab != null) {
            FloatingBottomBar(
                currentRoute = selectedTab,
                onTabSelected = { key ->
                    // 目标 tab 已在返回栈里时，必须直接 pop 回去。走 navigate + popUpTo(saveState)
                    // + restoreState 会把刚弹出的栈存档又原样还原，表现为「点了 tab
                    // 还停在旧页面」。只有目标 tab 不在栈里才走标准 tab 导航。
                    val (route, routeClass) = when (key) {
                        "feed" -> FeedRoute to FeedRoute::class
                        "subs" -> SubscriptionsRoute to SubscriptionsRoute::class
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
