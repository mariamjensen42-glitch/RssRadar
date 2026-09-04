package com.cycling.rssradar.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.cycling.rssradar.core.ui.theme.RssRadarTheme
import com.cycling.rssradar.core.ui.theme.LocalReducedMotion
import com.cycling.rssradar.core.ui.theme.rememberReducedMotion
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.cycling.rssradar.core.data.store.ListDisplayState
import com.cycling.rssradar.core.data.store.ReadingPrefs
import com.cycling.rssradar.core.data.store.ThemeMode
import com.cycling.rssradar.di.AppEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * 全局 CompositionLocal 装配点：主题模式、阅读偏好（排版/图片/渲染器/译文显示）、
 * 列表显示项三枚全局状态在此注入。以后加一项阅读偏好，只改 ReadingPrefs 一处，
 * 不再碰本文件 —— 阅读偏好四项原先各占一个 Local，合成一份后接线只剩一条。
 */
val LocalDarkTheme = staticCompositionLocalOf { true }

/** 全局阅读偏好（排版 / 图片 / 渲染器 / 译文显示）：阅读页与其弹层共享同一数据源。 */
val LocalReadingPrefs = staticCompositionLocalOf { ReadingPrefs() }

/** 信息流列表显示项（issue #56）：列表与设置页共享同一数据源。 */
val LocalListDisplay = staticCompositionLocalOf { ListDisplayState() }

/**
 * 环境宿主：读各 Store 的持久化偏好并注入 CompositionLocal，再包主题。
 * 主题模式为跟随系统时用 isSystemInDarkTheme 实时感知；设置页改模式，
 * flow 更新后这里自动重组。
 */
@Composable
fun CompositionLocalRoot(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, AppEntryPoint::class.java)
    }
    val themeStore = entryPoint.themeStore()
    val readingPrefsStore = entryPoint.readingPrefsStore()
    val listDisplayStore = entryPoint.listDisplayStore()
    val themeMode by themeStore.mode.collectAsState()
    val readingPrefs by readingPrefsStore.state.collectAsState()
    val listDisplay by listDisplayStore.state.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    ApplySystemBarIcons(darkTheme)
    // reduce-motion（docs/motion.md）：装配点读一次系统信号，观察器全局只注册一次
    val reducedMotion = rememberReducedMotion()
    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalReadingPrefs provides readingPrefs,
        LocalListDisplay provides listDisplay,
        LocalReducedMotion provides reducedMotion,
    ) {
        RssRadarTheme(darkTheme = darkTheme) {
            content()
        }
    }
}

/**
 * 系统栏图标颜色跟随**应用内**主题，不是系统主题（#68）。
 *
 * `MainActivity.onCreate` 的 `enableEdgeToEdge()` 用的是 `SystemBarStyle.auto`，
 * 判定依据只有系统 uiMode——App 自己那套「跟随系统 / 浅色 / 深色」设置它看不见。
 * 于是 App 设深色而系统是浅色时，深色图标画在纯黑背景上，直接看不见。
 *
 * 这里只补图标颜色，不重复设置 edge-to-edge（decorFitsSystemWindows 等一次性
 * 工作仍在 onCreate 做）。
 */
@Composable
private fun ApplySystemBarIcons(darkTheme: Boolean) {
    val view = LocalView.current
    DisposableEffect(darkTheme, view) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            // isAppearanceLightStatusBars = true 语义是「状态栏背景是亮的 → 图标用深色」
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        onDispose {}
    }
}

/** 从可能经过包装的 Context 里找回 Activity。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
