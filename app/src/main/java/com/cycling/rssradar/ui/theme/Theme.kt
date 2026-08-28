package com.cycling.rssradar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * RssRadar 主题：固定深色 + 紫色强调，关闭 dynamic color 以保证设计稿还原。
 */
private val RssRadarColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = Surface2,
    onPrimaryContainer = TextPrimary,
    secondary = Link,
    onSecondary = OnAccent,
    background = BgRoot,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = Divider,
    outlineVariant = Surface3,
    error = Color(0xFFEF4444),
    onError = OnAccent,
)

@Composable
fun RssRadarTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = RssRadarColorScheme,
        typography = Typography,
        content = content,
    )
}
