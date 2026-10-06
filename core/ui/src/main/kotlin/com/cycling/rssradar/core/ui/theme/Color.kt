package com.cycling.rssradar.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// —— 固定色板（低版本回退用，非设计主色）——
//
// 2026-10-03 起本App 的主配色是**系统动态取色**（Android 12+ 取壁纸色，
// 见 Theme.kt 的 withSystemScheme）。下面这批常量不再是「RssRadar 长什么样」，
// 只是 Android 11 及以下拿不到系统色板时的回退值，保证可读、不至于出现
// 浅底白字这类不可用配色。改它们不会影响 Android 12+ 的观感。

// —— 深色主题回退值 ——

/** 纯黑背景。 */
internal val DarkBgRoot = Color(0xFF000000)

/**
 * 卡片 / 一级容器表面。
 *
 * 原为紫调（与固定 accent 同源），现在accent 跟随系统，故这档回退值取中性深灰 ——
 * 低版本设备上即便与动态色 accent 混搭，也不会出现"紫底+ 别的色 accent"这种脏搭配。
 */
internal val DarkSurface1 = Color(0xFF1C1C1E)

/** Tab 选中态等次级容器。 */
internal val DarkSurface2 = Color(0xFF2C2C2E)

/** Hover / 描边弱化。 */
internal val DarkSurface3 = Color(0xFF3A3A3C)

/**
 * 文章卡片专用底色：比 surface1 亮一档，让卡片从纯黑背景里明确浮起。
 *
 * 独立成一个字段而不是直接改surface1，是为了让内容卡片能单独调明度，
 * 不被弹窗 / 输入框的取色牵连。
 */
internal val DarkArticleCard = Color(0xFF242426)

/** 回退强调色：仅低版本设备使用，高版本由系统壁纸取色覆盖。 */
internal val AccentValue = Color(0xFF7B7CFF)
internal val AccentPressedValue = Color(0xFF6B6CFF)
internal val OnAccentValue = Color(0xFFFFFFFF)

/** 回退链接色。 */
internal val LinkValue = Color(0xFF9B9CFF)

/** 深色主题文字。 */
internal val DarkTextPrimary = Color(0xFFFFFFFF)
internal val DarkTextSecondary = Color(0xFFB0B0B6)
/**
 * 三级文字：已读弱化（dimRead）、时间戳、次要标签。
 *
 * 在中性深灰底（surface1 #1C1C1E）上对比度约 5.2:1（对纯黑底 6.5:1），三条对比度门槛都宽裕。
 */
internal val DarkTextTertiary = Color(0xFF8E8E96)

/** 深色分割线。 */
internal val DarkDivider = Color(0xFF38383A)

// —— 浅色主题回退值 ——

/** 页面底：近白的中性灰；卡片纯白，靠明度差浮起。 */
internal val LightBgRoot = Color(0xFFF5F5F7)
internal val LightSurface1 = Color(0xFFFFFFFF)

/** 浅色下文章卡片保持纯白：白卡配中性淡底最干净。 */
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

/**
 * 背景是否偏亮：系统栏图标该用深色还是浅色，按底色算而不是按「是不是深色模式」——
 * 阅读主题可以让深色模式下出现米黄纸（#16），此时状态栏图标必须跟着翻。
 */
fun RadarColors.isLightBackground(): Boolean = relativeLuminance(bgRoot) >= 0.5f

/** 全局色板注入点：由 [RssRadarTheme] 提供。 */
val LocalRadarColors = staticCompositionLocalOf { RadarColors.Dark }

/** 统一读取入口：`radarColors().textPrimary`。 */
@Composable
fun radarColors(): RadarColors = LocalRadarColors.current
