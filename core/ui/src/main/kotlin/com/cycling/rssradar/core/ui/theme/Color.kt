package com.cycling.rssradar.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// —— 深色主题常量（设计稿色板） ——

/** 纯黑背景，与设计稿一致。 */
internal val DarkBgRoot = Color(0xFF000000)

/**
 * 卡片 / 一级容器表面：紫调，accent 的同色系低饱和版本（Rosé Pine 系）。
 *
 * 2026-09-07 由中性灰 #1C1C1E 改紫：纯中性灰与紫色 accent 完全脱节，
 * 卡片因此显得普通。整条表面阶梯（surface1/2/3 + divider）统一带紫，
 * 否则弹窗、输入框、Tab 选中态会留下灰色孤岛。
 */
internal val DarkSurface1 = Color(0xFF1E1C2C)

/** Tab 选中态等次级容器。 */
internal val DarkSurface2 = Color(0xFF322F4A)

/** Hover / 描边弱化。 */
internal val DarkSurface3 = Color(0xFF413C60)

/**
 * 文章卡片专用底色：比 surface1 亮一档，让卡片从纯黑背景里明确浮起。
 *
 * 独立成一个字段而不是直接改 surface1，是为了让内容卡片能单独调明度，
 * 不被弹窗 / 输入框的取色牵连。
 */
internal val DarkArticleCard = Color(0xFF232136)

/** 紫色强调：选中态、按钮、未读指示、tab 背景。 */
internal val AccentValue = Color(0xFF7B7CFF)
internal val AccentPressedValue = Color(0xFF6B6CFF)
internal val OnAccentValue = Color(0xFFFFFFFF)

/** 链接 / 标题选中色。 */
internal val LinkValue = Color(0xFF9B9CFF)

/** 深色主题文字。 */
internal val DarkTextPrimary = Color(0xFFFFFFFF)
internal val DarkTextSecondary = Color(0xFFB0B0B6)
/**
 * 三级文字：已读弱化（dimRead）、时间戳、次要标签。
 *
 * 卡片底色提亮后 #7E7E86 对比度掉到约 3.5:1，已读条目明显吃力，
 * 因此提亮一档到 #8E8E96（约 4.1:1）。差一档不足以单独开字段。
 */
internal val DarkTextTertiary = Color(0xFF8E8E96)

/** 深色分割线：随表面阶梯一起带紫。 */
internal val DarkDivider = Color(0xFF2E2B42)

// —— 浅色主题常量（与深色同一套强调色，表面/文字反色） ——

/** 页面底：带一丝紫，与深色主题同一个"底子"；卡片保持纯白不跟着发脏。 */
internal val LightBgRoot = Color(0xFFF6F5FA)
internal val LightSurface1 = Color(0xFFFFFFFF)

/** 浅色下文章卡片仍是纯白：白卡配淡紫底最干净，不额外上色。 */
internal val LightArticleCard = Color(0xFFFFFFFF)
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
    /** 文章卡片底色。仅内容卡片使用，弹窗 / 输入框继续用 [surface1]。 */
    val articleCard: Color,
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
        /** 纯黑背景 + 紫调表面阶梯。 */
        val Dark: RadarColors = RadarColors(
            bgRoot = DarkBgRoot,
            surface1 = DarkSurface1,
            surface2 = DarkSurface2,
            surface3 = DarkSurface3,
            articleCard = DarkArticleCard,
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
            articleCard = LightArticleCard,
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
