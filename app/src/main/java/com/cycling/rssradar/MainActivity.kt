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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.cycling.rssradar.data.ThemeMode
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
    private val settingsVm: RssHubSettingsViewModel by viewModels {
        RssHubSettingsViewModel.factory((application as RssRadarApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RssRadarThemeHost {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RssRadarApp(
                        feedVm = feedVm,
                        subsVm = subsVm,
                        addVm = addVm,
                        searchVm = searchVm,
                        articleVm = articleVm,
                        settingsVm = settingsVm,
                    )
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
    val themeStore = remember { (context.applicationContext as RssRadarApp).container.themeStore }
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
private fun RssRadarApp(
    feedVm: FeedListViewModel,
    subsVm: SubscriptionsViewModel,
    addVm: AddSubscriptionViewModel,
    searchVm: SearchViewModel,
    articleVm: ArticleDetailViewModel,
    settingsVm: RssHubSettingsViewModel,
) {
    // 加订阅是低频动作：不占路由，也不占整页，从信息流 FAB / 订阅页入口唤起底部抽屉。
    var currentTab by rememberSaveable { mutableStateOf(MainTab.Feed) }
    var detailArticleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(BgRoot)) {
        when (currentTab) {
            MainTab.Feed -> FeedListScreen(
                viewModel = feedVm,
                onOpenSearch = { currentTab = MainTab.Search },
                onOpenArticle = { detailArticleId = it.article.id },
                onAddSubscription = { showAddSheet = true },
            )
            MainTab.Subscriptions -> SubscriptionsScreen(
                viewModel = subsVm,
                onAddSubscription = { showAddSheet = true },
                onCreateGroup = { /* TODO */ },
            )
            MainTab.Search -> SearchScreen(
                viewModel = searchVm,
                onOpenArticle = { detailArticleId = it.article.id },
            )
            MainTab.Me -> RssHubSettingsScreen(viewModel = settingsVm)
        }

        // 文章详情是全屏浮层，浮层期间隐藏底部 TabBar。
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
                current = currentTab,
                onTabSelected = { currentTab = it },
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
