package com.cycling.rssradar.ui.article

import androidx.compose.ui.graphics.Color
import com.cycling.rssradar.core.data.store.ReadingTheme
import com.cycling.rssradar.core.ui.theme.RadarColors

/**
 * 阅读主题 → 阅读页色板（ReadYou 差距表 #16）。纯函数，JVM 单测。
 *
 * **为什么返回一整份 [RadarColors] 而不是一枚背景色**：阅读页有上百处
 * `radarColors()` 调用（顶栏、底栏、卡片、正文、WebView 注入的 CSS 全靠它）。
 * 逐处改成「读阅读主题」不现实也必然漏；由 `ArticleDetailScreen` 在整页外面
 * 包一层 `CompositionLocalProvider(LocalRadarColors provides pageColors)`，
 * 下游零改动自动跟随，漏掉一处都不可能。
 *
 * **只换背景/表面/文字**：强调色四件套仍用应用的（含 #29 的自定义色）——
 * 阅读主题决定的是「纸的颜色」，不是换一套品牌色。
 */
fun ReadingTheme.pageColors(app: RadarColors): RadarColors = when (this) {
    ReadingTheme.FOLLOW -> app
    ReadingTheme.PAPER -> app.paper()
    ReadingTheme.GRAY -> app.gray()
    ReadingTheme.NIGHT -> app.night()
}

private fun RadarColors.paper() = copy(
    bgRoot = Color(0xFFF5F1E6),
    surface1 = Color(0xFFFBF8F0),
    surface2 = Color(0xFFEDE7D6),
    surface3 = Color(0xFFE0D9C4),
    articleCard = Color(0xFFFBF8F0),
    textPrimary = Color(0xFF3A3226),
    textSecondary = Color(0xFF6B6152),
    textTertiary = Color(0xFF8C8272),
    divider = Color(0xFFDED6C0),
)

private fun RadarColors.gray() = copy(
    bgRoot = Color(0xFFE9E9EA),
    surface1 = Color(0xFFF4F4F5),
    surface2 = Color(0xFFDEDEE0),
    surface3 = Color(0xFFCFCFD2),
    articleCard = Color(0xFFF4F4F5),
    textPrimary = Color(0xFF2C2C2E),
    textSecondary = Color(0xFF5A5A5F),
    textTertiary = Color(0xFF86868B),
    divider = Color(0xFFD2D2D6),
)

private fun RadarColors.night() = copy(
    bgRoot = Color(0xFF1C1C1E),
    surface1 = Color(0xFF262629),
    surface2 = Color(0xFF333338),
    surface3 = Color(0xFF42424A),
    articleCard = Color(0xFF262629),
    textPrimary = Color(0xFFD8D8DC),
    textSecondary = Color(0xFFA0A0A8),
    textTertiary = Color(0xFF7E7E88),
    divider = Color(0xFF34343A),
)
