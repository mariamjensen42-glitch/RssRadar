package com.cycling.rssradar.core.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.cycling.rssradar.core.model.ReadingPrefs

/**
 * 应用内主题是否深色（区别于系统 uiMode）。
 *
 * 声明放 core:ui —— 只依赖 compose。装配在 app 的 CompositionLocalRoot：只有那里知道
 * 用户选的是跟随系统 / 浅色 / 深色，以及系统当前是不是深色。
 */
val LocalDarkTheme = staticCompositionLocalOf { true }

/** 全局阅读偏好（排版 / 图片 / 渲染器 / 译文显示）：阅读页与其弹层共享同一数据源。 */
val LocalReadingPrefs = staticCompositionLocalOf { ReadingPrefs() }

/**
 * 系统栏图标颜色跟随**应用内**主题，不是系统主题（#68）。
 *
 * `MainActivity.onCreate` 的 `enableEdgeToEdge()` 用的是 `SystemBarStyle.auto`，
 * 判定依据只有系统 uiMode——App 自己那套「跟随系统 / 浅色 / 深色」设置它看不见。
 * 于是 App 设深色而系统是浅色时，深色图标画在纯黑背景上，直接看不见。
 *
 * 这里只补图标颜色，不重复设置 edge-to-edge（decorFitsSystemWindows 等一次性
 * 工作仍在 onCreate 做）。
 *
 * **阅读页也会调它**（阅读主题 #16 可以让深色模式下出现米黄纸），所以
 * [onDispose] 必须按应用主题（[LocalDarkTheme]）还原——否则退出阅读页后
 * 系统栏图标会停在上一页的配色上。
 *
 * 放在 core:ui 而不是 app：阅读页（feature:article）必须自己调它，而 feature 够不着 app 包。
 * 它只依赖 androidx 与 [LocalDarkTheme]，不碰数据层，符合 core:ui 铁律。
 */
@Composable
fun ApplySystemBarIcons(darkTheme: Boolean) {
    val appDarkTheme = LocalDarkTheme.current
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
        onDispose {
            val w = view.context.findActivity()?.window
            if (w != null) {
                WindowCompat.getInsetsController(w, view).apply {
                    isAppearanceLightStatusBars = !appDarkTheme
                    isAppearanceLightNavigationBars = !appDarkTheme
                }
            }
        }
    }
}

/** 从可能经过包装的 Context 里找回 Activity。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
