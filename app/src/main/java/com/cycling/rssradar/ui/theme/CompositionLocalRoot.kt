package com.cycling.rssradar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.cycling.rssradar.data.store.ListDisplayState
import com.cycling.rssradar.data.store.ReadingPrefs
import com.cycling.rssradar.data.store.ThemeMode
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
    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalReadingPrefs provides readingPrefs,
        LocalListDisplay provides listDisplay,
    ) {
        RssRadarTheme(darkTheme = darkTheme) {
            content()
        }
    }
}
