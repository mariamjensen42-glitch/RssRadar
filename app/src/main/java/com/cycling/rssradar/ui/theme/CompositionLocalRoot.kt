package com.cycling.rssradar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.cycling.rssradar.core.data.di.AppEntryPoint
import com.cycling.rssradar.core.model.ThemeMode
import com.cycling.rssradar.core.ui.theme.ApplySystemBarIcons
import com.cycling.rssradar.core.ui.theme.LocalDarkTheme
import com.cycling.rssradar.core.ui.theme.LocalListDisplay
import com.cycling.rssradar.core.ui.theme.LocalReadingPrefs
import com.cycling.rssradar.core.ui.theme.LocalReducedMotion
import com.cycling.rssradar.core.ui.theme.RssRadarTheme
import com.cycling.rssradar.core.ui.theme.rememberReducedMotion
import dagger.hilt.android.EntryPointAccessors

/**
 * 全局 CompositionLocal 装配点：主题模式、阅读偏好（排版/图片/渲染器/译文显示）、
 * 列表显示项三枚全局状态在此注入。以后加一项阅读偏好，只改 ReadingPrefs 一处，
 * 不再碰本文件 —— 阅读偏好四项原先各占一个 Local，合成一份后接线只剩一条。
 *
 * **声明与装配分家**：`LocalDarkTheme` / `LocalReadingPrefs` / `LocalListDisplay` 三枚声明都住在
 * `core.ui.theme` —— 它们只依赖 core:model 的数据类型，而 feature 模块（feed / article）要读同一个
 * Local、又够不着 app 包，声明留在 app 就等于 feature 依赖 app。本文件只剩**装配**：读各 Store 的
 * 持久化值后 provides，这一步依赖 core:data（AppEntryPoint），所以必须留在 app。
 *
 * 主题模式为跟随系统时用 isSystemInDarkTheme 实时感知；设置页改模式，flow 更新后这里自动重组。
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
    // 强调色（#27 动态取色 / #29 自定义主色）：自定义优先于系统取色
    val dynamicColor by themeStore.dynamicColor.collectAsState()
    val customAccent by themeStore.customAccent.collectAsState()
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
        RssRadarTheme(
            darkTheme = darkTheme,
            dynamicColor = dynamicColor,
            customAccentArgb = customAccent,
        ) {
            content()
        }
    }
}
