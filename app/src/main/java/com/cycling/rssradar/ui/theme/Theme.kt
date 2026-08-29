package com.cycling.rssradar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * RssRadar 主题：深色 / 浅色两套色板 + 固定紫色强调。
 * 深色保持 iOS Dark 风（纯黑背景），浅色用近白表面。
 * 关闭 dynamic color 以保证设计稿还原。
 */
private val RssRadarDarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = DarkSurface2,
    onPrimaryContainer = DarkTextPrimary,
    secondary = Link,
    onSecondary = OnAccent,
    background = DarkBgRoot,
    onBackground = DarkTextPrimary,
    surface = DarkSurface1,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurface2,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkDivider,
    outlineVariant = DarkSurface3,
    error = Color(0xFFEF4444),
    onError = OnAccent,
)

private val RssRadarLightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = LightSurface2,
    onPrimaryContainer = LightTextPrimary,
    secondary = Link,
    onSecondary = OnAccent,
    background = LightBgRoot,
    onBackground = LightTextPrimary,
    surface = LightSurface1,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurface2,
    onSurfaceVariant = LightTextSecondary,
    outline = LightDivider,
    outlineVariant = LightSurface3,
    error = Color(0xFFDC2626),
    onError = OnAccent,
)

@Composable
fun RssRadarTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    // 先铺好运行时色板，再渲染内容——UI 读 BgRoot/TextPrimary 等 getter 代理时
    // 会拿到当前主题的色值并自动重组
    applyPalette(darkTheme)
    MaterialTheme(
        colorScheme = if (darkTheme) RssRadarDarkColorScheme else RssRadarLightColorScheme,
        typography = Typography,
        content = content,
    )
}
