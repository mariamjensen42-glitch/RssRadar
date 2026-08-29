package com.cycling.rssradar

import android.app.Application
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.hasRoute
import androidx.navigation.popUpTo
import com.cycling.rssradar.data.ThemeMode
import com.cycling.rssradar.di.AppEntryPoint
import com.cycling.rssradar.ui.AddSubscriptionSheet
import com.cycling.rssradar.ui.AddSubscriptionViewModel
import com.cycling.rssradar.ui.ArticleDetailScreen
import com.cycling.rssradar.ui.ArticleDetailViewModel
import com.cycling.rssradar.ui.FeedListScreen
import com.cycling.rssradar.ui.FeedListViewModel
import com.cycling.rssradar.ui.RssHubSettingsScreen
import com.cycling.rssradar.ui.RssHubSettingsViewModel
import com.cycling.rssradar.ui.SearchScreen
import com.cycling.rssradar.ui.SearchViewModel
import com.cycling.rssradar.ui.SubscriptionsScreen
import com.cycling.rssradar.ui.SubscriptionsViewModel
import com.cycling.rssradar.ui.components.FloatingBottomBar
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

    // 加订阅 / 详情浮层在其成为 Nav 目的地（#30 / #31）前，暂由 Activity 级 VM 驱动。
    private val addVm: AddSubscriptionViewModel by viewModels()
    private val articleVm: ArticleDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RssRadarThemeHost {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RssRadarApp(addVm = addVm, articleVm = articleVm)
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

@Composable
private fun RssRadarApp(addVm: AddSubscriptionViewModel, articleVm: ArticleDetailViewModel) {
    val navController = rememberNavController()
    // TODO(#30/#31): 详情 / 加订阅改为 Nav 目的地后删除这两个状态。
    var detailArticleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
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
                    onOpenArticle = { detailArticleId = it.article.id },
                    onAddSubscription = { showAddSheet = true },
                )
            }
            composable<SubscriptionsRoute> {
                val vm = hiltViewModel<SubscriptionsViewModel>()
                SubscriptionsScreen(
                    viewModel = vm,
                    onAddSubscription = { showAddSheet = true },
                    onCreateGroup = { /* TODO */ },
                )
            }
            composable<SearchRoute> {
                val vm = hiltViewModel<SearchViewModel>()
                SearchScreen(
                    viewModel = vm,
                    onOpenArticle = { detailArticleId = it.article.id },
                )
            }
            composable<MeRoute> {
                val vm = hiltViewModel<RssHubSettingsViewModel>()
                RssHubSettingsScreen(viewModel = vm)
            }
        }

        // 文章详情是全屏浮层，浮层期间隐藏底部 TabBar（#30 改为 Nav composable 目的地）。
        val articleId = detailArticleId
        if (articleId != null) {
            Box(modifier = Modifier.fillMaxSize().background(BgRoot)) {
                ArticleDetailScreen(
                    viewModel = articleVm,
                    articleId = articleId,
                    onBack = { detailArticleId = null },
                    onOpenOriginal = { url -> context.openUrl(url) },
                )
            }
        } else {
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

        if (showAddSheet) {
            AddSubscriptionSheet(
                viewModel = addVm,
                onDismiss = {
                    showAddSheet = false
                    addVm.reset()
                },
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
