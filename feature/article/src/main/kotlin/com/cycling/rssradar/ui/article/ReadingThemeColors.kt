package com.cycling.rssradar.ui.article

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.cycling.rssradar.core.model.ReadingTheme

/**
 * 阅读主题 → 阅读页色板（ReadYou 差距表 #16）。纯函数，JVM 单测。
 *
 * **为什么返回一整份 [ColorScheme] 而不是一枚背景色**：阅读页有上百处主题色取用
 * （顶栏、底栏、卡片、正文、WebView 注入的 CSS 全靠它）。逐处改成「读阅读主题」
 * 不现实也必然漏；由 `ArticleDetailScreen` 在整页外面包一层
 * `MaterialExpressiveTheme(colorScheme = pageScheme)`，下游零改动自动跟随，
 * 漏掉一处都不可能。
 *
 * **只换表面/文字/描边**：强调色三件套仍用应用的（含 #29 的自定义色）——
 * 阅读主题决定的是「纸的颜色」，不是换一套品牌色。
 */
fun ReadingTheme.pageScheme(app: ColorScheme): ColorScheme = when (this) {
    ReadingTheme.FOLLOW -> app
    ReadingTheme.PAPER -> app.paper()
    ReadingTheme.GRAY -> app.gray()
    ReadingTheme.NIGHT -> app.night()
}

private fun ColorScheme.paper() = copy(
    surface = Color(0xFFF5F1E6),
    surfaceContainerLowest = Color(0xFFFBF8F0),
    surfaceContainerLow = Color(0xFFFBF8F0),
    surfaceContainer = Color(0xFFEDE7D6),
    surfaceContainerHighest = Color(0xFFE0D9C4),
    onSurface = Color(0xFF3A3226),
    onSurfaceVariant = Color(0xFF6B6152),
    outlineVariant = Color(0xFFDED6C0),
)

private fun ColorScheme.gray() = copy(
    surface = Color(0xFFE9E9EA),
    surfaceContainerLowest = Color(0xFFF4F4F5),
    surfaceContainerLow = Color(0xFFF4F4F5),
    surfaceContainer = Color(0xFFDEDEE0),
    surfaceContainerHighest = Color(0xFFCFCFD2),
    onSurface = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF5A5A5F),
    outlineVariant = Color(0xFFD2D2D6),
)

private fun ColorScheme.night() = copy(
    surface = Color(0xFF1C1C1E),
    surfaceContainerLowest = Color(0xFF262629),
    surfaceContainerLow = Color(0xFF262629),
    surfaceContainer = Color(0xFF333338),
    surfaceContainerHighest = Color(0xFF42424A),
    onSurface = Color(0xFFD8D8DC),
    onSurfaceVariant = Color(0xFFA0A0A8),
    outlineVariant = Color(0xFF34343A),
)
