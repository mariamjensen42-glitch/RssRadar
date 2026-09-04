package com.cycling.rssradar.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// —— 深色主题常量（设计稿色板） ——

/** 纯黑背景，与设计稿一致。 */
internal val DarkBgRoot = Color(0xFF000000)

/** 卡片 / 一级容器表面：iOS Dark 风。 */
internal val DarkSurface1 = Color(0xFF1C1C1E)

/** Tab 选中态等次级容器。 */
internal val DarkSurface2 = Color(0xFF2C2C2E)

/** Hover / 描边弱化。 */
internal val DarkSurface3 = Color(0xFF3A3A3C)

/** 紫色强调：选中态、按钮、未读指示、tab 背景。 */
internal val AccentValue = Color(0xFF7B7CFF)
internal val AccentPressedValue = Color(0xFF6B6CFF)
internal val OnAccentValue = Color(0xFFFFFFFF)

/** 链接 / 标题选中色。 */
internal val LinkValue = Color(0xFF9B9CFF)

/** 深色主题文字。 */
internal val DarkTextPrimary = Color(0xFFFFFFFF)
internal val DarkTextSecondary = Color(0xFFB0B0B6)
internal val DarkTextTertiary = Color(0xFF7E7E86)

/** 深色分割线。 */
internal val DarkDivider = Color(0xFF2A2A2D)

// —— 浅色主题常量（与深色同一套强调色，表面/文字反色） ——

internal val LightBgRoot = Color(0xFFF7F7F9)
internal val LightSurface1 = Color(0xFFFFFFFF)
internal val LightSurface2 = Color(0xFFEBEBEF)
internal val LightSurface3 = Color(0xFFD9D9E0)
internal val LightTextPrimary = Color(0xFF1A1A1E)
internal val LightTextSecondary = Color(0xFF55555C)
internal val LightTextTertiary = Color(0xFF8A8A92)
internal val LightDivider = Color(0xFFE2E2E8)

/** 语义色：成功 / 警告 / 危险。深浅两套主题下都可读，不随主题切换。 */
val Success = Color(0xFF34D399)
val Warning = Color(0xFFFBBF24)
val Danger = Color(0xFFEF4444)

/**
 * 运行时色板（不可变快照）：主题切换时由 [RssRadarTheme] 通过
 * [LocalRadarColors] 提供新的实例，UI 层统一用 [radarColors] 读取。
 */
data class RadarColors(
    val bgRoot: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val accent: Color,
    val accentPressed: Color,
    val onAccent: Color,
    val link: Color,
) {
    companion object {
        /** iOS Dark 风：纯黑背景。 */
        val Dark: RadarColors = RadarColors(
            bgRoot = DarkBgRoot,
            surface1 = DarkSurface1,
            surface2 = DarkSurface2,
            surface3 = DarkSurface3,
            textPrimary = DarkTextPrimary,
            textSecondary = DarkTextSecondary,
            textTertiary = DarkTextTertiary,
            divider = DarkDivider,
            accent = AccentValue,
            accentPressed = AccentPressedValue,
            onAccent = OnAccentValue,
            link = LinkValue,
        )

        /** 近白表面，强调色不变。 */
        val Light: RadarColors = RadarColors(
            bgRoot = LightBgRoot,
            surface1 = LightSurface1,
            surface2 = LightSurface2,
            surface3 = LightSurface3,
            textPrimary = LightTextPrimary,
            textSecondary = LightTextSecondary,
            textTertiary = LightTextTertiary,
            divider = LightDivider,
            accent = AccentValue,
            accentPressed = AccentPressedValue,
            onAccent = OnAccentValue,
            link = LinkValue,
        )
    }
}

/** 全局色板注入点：由 [RssRadarTheme] 提供。 */
val LocalRadarColors = staticCompositionLocalOf { RadarColors.Dark }

/** 统一读取入口：`radarColors().textPrimary`。 */
@Composable
fun radarColors(): RadarColors = LocalRadarColors.current
