package com.cycling.rssradar.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * RssRadar 主题：深色 / 浅色两套色板 + 固定紫色强调。
 * 深色保持 iOS Dark 风（纯黑背景），浅色用近白表面。
 * 关闭 dynamic color 以保证设计稿还原。
 *
 * 色板经 [LocalRadarColors] 注入，UI 层统一用 [radarColors] 读取；
 * M3 colorScheme 槽位与之同步映射，供 M3 组件内部取色。
 */
private val RssRadarDarkColorScheme = darkColorScheme(
    primary = AccentValue,
    onPrimary = OnAccentValue,
    primaryContainer = DarkSurface2,
    onPrimaryContainer = DarkTextPrimary,
    secondary = LinkValue,
    onSecondary = OnAccentValue,
    background = DarkBgRoot,
    onBackground = DarkTextPrimary,
    surface = DarkSurface1,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurface2,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkDivider,
    outlineVariant = DarkSurface3,
    error = Color(0xFFEF4444),
    onError = OnAccentValue,
)

private val RssRadarLightColorScheme = lightColorScheme(
    primary = AccentValue,
    onPrimary = OnAccentValue,
    primaryContainer = LightSurface2,
    onPrimaryContainer = LightTextPrimary,
    secondary = LinkValue,
    onSecondary = OnAccentValue,
    background = LightBgRoot,
    onBackground = LightTextPrimary,
    surface = LightSurface1,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurface2,
    onSurfaceVariant = LightTextSecondary,
    outline = LightDivider,
    outlineVariant = LightSurface3,
    error = Color(0xFFDC2626),
    onError = OnAccentValue,
)

@Composable
fun RssRadarTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    // 色板快照注入 CompositionLocal，UI 读 radarColors() 时随主题切换自动重组
    CompositionLocalProvider(
        LocalRadarColors provides if (darkTheme) RadarColors.Dark else RadarColors.Light,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) RssRadarDarkColorScheme else RssRadarLightColorScheme,
            typography = Typography,
            content = content,
        )
    }
}
