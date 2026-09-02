package com.cycling.rssradar.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// —— 深色主题常量（设计稿色板） ——

/** 纯黑背景，与设计稿一致。 */
val DarkBgRoot = Color(0xFF000000)

/** 卡片 / 一级容器表面：iOS Dark 风。 */
val DarkSurface1 = Color(0xFF1C1C1E)

/** Tab 选中态等次级容器。 */
val DarkSurface2 = Color(0xFF2C2C2E)

/** Hover / 描边弱化。 */
val DarkSurface3 = Color(0xFF3A3A3C)

/** 紫色强调：选中态、按钮、未读指示、tab 背景。 */
val Accent = Color(0xFF7B7CFF)
val AccentPressed = Color(0xFF6B6CFF)
val OnAccent = Color(0xFFFFFFFF)

/** 链接 / 标题选中色。 */
val Link = Color(0xFF9B9CFF)

/** 深色主题文字。 */
val DarkTextPrimary = Color(0xFFFFFFFF)
val DarkTextSecondary = Color(0xFFB0B0B6)
val DarkTextTertiary = Color(0xFF7E7E86)

/** 深色分割线。 */
val DarkDivider = Color(0xFF2A2A2D)

/** 成功 / 校验通过。 */
val Success = Color(0xFF34D399)

/** 警告。 */
val Warning = Color(0xFFFBBF24)

/** 危险 / 破坏性操作（删除、清空）。深浅两套主题下都可读，故不进 palette。 */
val Danger = Color(0xFFEF4444)

// —— 浅色主题常量（与深色同一套强调色，表面/文字反色） ——

/** 浅色背景。 */
val LightBgRoot = Color(0xFFF7F7F9)

/** 浅色卡片表面。 */
val LightSurface1 = Color(0xFFFFFFFF)

/** 浅色次级容器。 */
val LightSurface2 = Color(0xFFEBEBEF)

/** 浅色描边 / 弱化。 */
val LightSurface3 = Color(0xFFD9D9E0)

/** 浅色主题文字。 */
val LightTextPrimary = Color(0xFF1A1A1E)
val LightTextSecondary = Color(0xFF55555C)
val LightTextTertiary = Color(0xFF8A8A92)

/** 浅色分割线。 */
val LightDivider = Color(0xFFE2E2E8)

/**
 * 运行时色板：主题切换时更新这些状态，UI 层读下面的 getter 代理自动重组。
 * 深色 / 浅色两套常量见上。
 */
object RssRadarPalette {
    var bgRoot by mutableStateOf(DarkBgRoot)
    var surface1 by mutableStateOf(DarkSurface1)
    var surface2 by mutableStateOf(DarkSurface2)
    var surface3 by mutableStateOf(DarkSurface3)
    var textPrimary by mutableStateOf(DarkTextPrimary)
    var textSecondary by mutableStateOf(DarkTextSecondary)
    var textTertiary by mutableStateOf(DarkTextTertiary)
    var divider by mutableStateOf(DarkDivider)
    var onAccent by mutableStateOf(OnAccent)
}

/**
 * 兼容旧引用的 getter 代理：UI 里读到的 BgRoot / Surface1 / TextPrimary 等
 * 都是动态取 palette 当前值，主题切换时无需改任何调用点。
 */
val BgRoot: Color get() = RssRadarPalette.bgRoot
val Surface1: Color get() = RssRadarPalette.surface1
val Surface2: Color get() = RssRadarPalette.surface2
val Surface3: Color get() = RssRadarPalette.surface3
val TextPrimary: Color get() = RssRadarPalette.textPrimary
val TextSecondary: Color get() = RssRadarPalette.textSecondary
val TextTertiary: Color get() = RssRadarPalette.textTertiary
val Divider: Color get() = RssRadarPalette.divider

/** 主题切换：一次性铺好所有表面/文字色。 */
fun applyPalette(darkTheme: Boolean) {
    RssRadarPalette.bgRoot = if (darkTheme) DarkBgRoot else LightBgRoot
    RssRadarPalette.surface1 = if (darkTheme) DarkSurface1 else LightSurface1
    RssRadarPalette.surface2 = if (darkTheme) DarkSurface2 else LightSurface2
    RssRadarPalette.surface3 = if (darkTheme) DarkSurface3 else LightSurface3
    RssRadarPalette.textPrimary = if (darkTheme) DarkTextPrimary else LightTextPrimary
    RssRadarPalette.textSecondary = if (darkTheme) DarkTextSecondary else LightTextSecondary
    RssRadarPalette.textTertiary = if (darkTheme) DarkTextTertiary else LightTextTertiary
    RssRadarPalette.divider = if (darkTheme) DarkDivider else LightDivider
}
