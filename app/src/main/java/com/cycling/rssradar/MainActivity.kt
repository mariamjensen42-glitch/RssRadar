package com.cycling.rssradar

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.navigation
import androidx.navigation.toRoute
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cycling.rssradar.data.ThemeMode
import com.cycling.rssradar.di.AppEntryPoint
import com.cycling.rssradar.ui.AddSubscriptionCatalogScreen
import com.cycling.rssradar.ui.AddSubscriptionParamsScreen
import com.cycling.rssradar.ui.AddSubscriptionViewModel
import com.cycling.rssradar.ui.ArticleDetailScreen
import com.cycling.rssradar.ui.ArticleDetailViewModel
import com.cycling.rssradar.ui.FeedActionScreen
import com.cycling.rssradar.ui.FeedListScreen
import com.cycling.rssradar.ui.FeedListViewModel
import com.cycling.rssradar.ui.RssHubSettingsScreen
import com.cycling.rssradar.ui.RssHubSettingsViewModel
import com.cycling.rssradar.ui.SearchScreen
import com.cycling.rssradar.ui.SearchViewModel
import com.cycling.rssradar.ui.SubscriptionsScreen
import com.cycling.rssradar.ui.SubscriptionsViewModel
import com.cycling.rssradar.ui.components.FloatingBottomBar
import com.cycling.rssradar.ui.navigation.AddSubscriptionCatalogRoute
import com.cycling.rssradar.ui.navigation.AddSubscriptionParamsRoute
import com.cycling.rssradar.ui.navigation.AddSubscriptionRoute
import com.cycling.rssradar.ui.navigation.ArticleDetailRoute
import com.cycling.rssradar.ui.navigation.FeedActionRoute
import com.cycling.rssradar.ui.navigation.FeedRoute
import com.cycling.rssradar.ui.navigation.MeRoute
import com.cycling.rssradar.ui.navigation.SearchRoute
import com.cycling.rssradar.ui.navigation.SubscriptionsRoute
import com.cycling.rssradar.ui.theme.BgRoot
import com.cycling.rssradar.ui.theme.RssRadarTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RssRadarThemeHost {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RssRadarAppContent()
                }
            }
        }
    }
}

/**
 * 主题宿主：读持久化的主题偏好（ThemeStore flow），跟随系统时用
 * isSystemInDarkTheme 实时感知，把 darkTheme 交给 RssRadarTheme。
 * 设置页通过同一个 store 改模式，flow 更新后这里自动重组。
 * 同时把当前 darkTheme 注入 CompositionLocal，供 WebView 正文模板等
 * 需要感知主题的非 Material 组件使用。
 */
@Composable
private fun RssRadarThemeHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val themeStore = remember { EntryPointAccessors.fromApplication(app, AppEntryPoint::class.java).themeStore() }
    val themeMode by themeStore.mode.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        RssRadarTheme(darkTheme = darkTheme) {
            content()
        }
    }
}

/** 当前应用的实际深色状态（跟随系统或用户强制）。 */
val LocalDarkTheme = staticCompositionLocalOf { true }

@SuppressLint("RestrictedApi")
@Composable
private fun RssRadarAppContent() {
    val navController = rememberNavController()
    val context = LocalContext.current

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
                    onAddSubscription = { navController.navigate(AddSubscriptionRoute) },
                )
            }
            composable<SubscriptionsRoute> {
                val vm = hiltViewModel<SubscriptionsViewModel>()
                SubscriptionsScreen(
                    viewModel = vm,
                    onAddSubscription = { navController.navigate(AddSubscriptionRoute) },
                    onCreateGroup = { /* TODO */ },
                    onFeedAction = { navController.navigate(FeedActionRoute(it)) },
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
                RssHubSettingsScreen(viewModel = vm)
            }
            composable<ArticleDetailRoute> { backStackEntry ->
                val articleId = backStackEntry.toRoute<ArticleDetailRoute>().articleId
                ArticleDetailScreen(
                    viewModel = hiltViewModel<ArticleDetailViewModel>(),
                    articleId = articleId,
                    onBack = { navController.popBackStack() },
                    onOpenOriginal = { url -> context.openUrl(url) },
                )
            }
            // 加订阅两步流：嵌套 nav graph（issue #33）。graph route 即 AddSubscriptionRoute，
            // AddSubscriptionViewModel 作用域在 graph 上，两步共享、graph 出栈销毁。
            navigation<AddSubscriptionRoute>(startDestination = AddSubscriptionCatalogRoute) {
                composable<AddSubscriptionCatalogRoute> {
                    // navGraph 作用域共享：跨目的地取同一 VM 实例
                    val parentEntry = remember { navController.getBackStackEntry<AddSubscriptionRoute>() }
                    val vm = hiltViewModel<AddSubscriptionViewModel>(parentEntry)
                    AddSubscriptionCatalogScreen(
                        viewModel = vm,
                        onDismiss = { navController.popBackStack() },
                        onOpenParams = { navController.navigate(AddSubscriptionParamsRoute) },
                    )
                }
                composable<AddSubscriptionParamsRoute> {
                    val parentEntry = remember { navController.getBackStackEntry<AddSubscriptionRoute>() }
                    val vm = hiltViewModel<AddSubscriptionViewModel>(parentEntry)
                    AddSubscriptionParamsScreen(
                        viewModel = vm,
                        onDismiss = { navController.popBackStack() },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable<FeedActionRoute> { backStackEntry ->
                val feedId = backStackEntry.toRoute<FeedActionRoute>().feedId
                FeedActionScreen(
                    feedId = feedId,
                    viewModel = hiltViewModel<SubscriptionsViewModel>(),
                    onDismiss = { navController.popBackStack() },
                )
            }
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
