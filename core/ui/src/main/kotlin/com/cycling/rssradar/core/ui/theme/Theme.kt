package com.cycling.rssradar.core.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// —— 动态取色派生（#27）——
// 系统（Monet）只给 primary / onPrimary 两枚且已保证对比度；accentPressed 与
// link 是本项目自己的派生色，比例即设计判据，因此抽成纯函数单测。

/** accentPressed 相对 accent 的压暗比例：对齐固定色板 #7B7CFF → #6B6CFF 的手感。 */
internal const val ACCENT_PRESSED_DARKEN = 0.12f

/** 深色下 link 相对 accent 提亮比例：纯黑背景上 accent 偏沉，提亮才看得出是链接。 */
internal const val LINK_LIGHTEN = 0.25f

/** 浅色下 link 相对 accent 压暗比例：白底上再提亮会掉对比度，所以反向压暗。 */
internal const val LINK_DARKEN = 0.10f

private val MIX_DARK = Color(0xFF000000)
private val MIX_LIGHT = Color(0xFFFFFFFF)

private val DarkError = Color(0xFFEF4444)
private val LightError = Color(0xFFDC2626)

/** 强调色四件套：本体 / 按下态 / 画在 accent 之上的前景 / 链接。 */
data class AccentColors(
    val accent: Color,
    val accentPressed: Color,
    val onAccent: Color,
    val link: Color,
)

/**
 * sRGB 通道线性插值。
 *
 * 不用 `androidx.compose.ui.graphics.lerp`：它会在 Oklab 里转一圈，
 * 顺便把 `ui-util` 拽进运行时——为一枚派生色多背一份依赖不值得（本地单测
 *  classpath 因此缺类直接挂过）。混色比例只有 10%~25%，线性空间与感知空间的
 *  差异在这点幅度上看不出来。
 */
private fun mix(from: Color, to: Color, fraction: Float): Color = Color(
    red = from.red + (to.red - from.red) * fraction,
    green = from.green + (to.green - from.green) * fraction,
    blue = from.blue + (to.blue - from.blue) * fraction,
    alpha = from.alpha + (to.alpha - from.alpha) * fraction,
)

/**
 * 由系统给出的 [accent] / [onAccent] 派生 accentPressed 与 link。纯函数，便于单测。
 *
 * 动态色板的 primary/onPrimary 由系统保证对比度，本项目只负责两枚派生色：
 * 深色下 link 提亮（纯黑背景吃色），浅色下 link 压暗（白底提亮掉对比度）。
 */
fun deriveAccentColors(accent: Color, onAccent: Color, darkTheme: Boolean): AccentColors =
    AccentColors(
        accent = accent,
        accentPressed = mix(accent, MIX_DARK, ACCENT_PRESSED_DARKEN),
        onAccent = onAccent,
        link = if (darkTheme) {
            mix(accent, MIX_LIGHT, LINK_LIGHTEN)
        } else {
            mix(accent, MIX_DARK, LINK_DARKEN)
        },
    )

/** Android 12（S）才提供系统动态色板；低于此版本开关无效（UI 需配解释文案）。 */
fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * 强调色四件套整体替换，表面阶梯与文字层级不动。
 *
 * 只换强调色：整套色板跟随（壁纸或用户拾色）会让「RssRadar 长什么样」这件事消失，
 * 且既有的紫调表面阶梯与新强调色不同源，混着用会脏。
 */
private fun RadarColors.withAccent(accent: Color, onAccent: Color, darkTheme: Boolean): RadarColors {
    val derived = deriveAccentColors(accent, onAccent, darkTheme)
    return copy(
        accent = derived.accent,
        accentPressed = derived.accentPressed,
        onAccent = derived.onAccent,
        link = derived.link,
    )
}

/**
 * 整套表面/文字跟随系统动态色板（2026-10-03：弃用固定紫调色板，改自动配色）。
 *
 * 之前这里有个只换强调色的 `withSystemAccent`，理由是「紫调表面阶梯与新强调色不同源，混着用会脏」。
 * 现在整套跟随，紫调 surface1/2/3 与 divider 全部改由系统色板的 surface 族承载，文字层级由 onSurface 族承载，
 * 那份半吊子实现已删除。
 *
 * 字段映射依据（surfaceContainer 阶梯是 M3 官方为「层叠表面」提供的，正好对上旧的
 * surface1/2/3 三档；onSurfaceVariant / outlineVariant 对上旧的次级文字与弱描边）：
 * - bgRoot ← surface（最底层，官方 surface 是背景色）
 * - surface1 ← surfaceContainerLowest（卡片，比背景略高）
 * - surface2 ← surfaceContainer（选中态等次级容器）
 * - surface3 ← surfaceContainerHighest（hover / 弱描边）
 * - articleCard ← surfaceContainerLow（内容卡片，比 surface1 亮一档，保留"卡片浮起"的观感）
 * - textPrimary ← onSurface，textSecondary ← onSurfaceVariant，textTertiary ← onSurfaceVariant
 * - divider ← outlineVariant
 *
 * [accent] 之外的字段全部取自 scheme，因此开启动态取色时表面与强调色同源，不会再出现
 * 「新强调色 + 旧紫表面」这种脏搭配。
 *
 * 低于 Android 12（[supportsDynamicColor] 为 false）时调用方回退到固定色板，此函数不会被调用。
 */
private fun RadarColors.withSystemScheme(context: Context, darkTheme: Boolean): RadarColors {
    val scheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val derived = deriveAccentColors(scheme.primary, scheme.onPrimary, darkTheme)
    return copy(
        bgRoot = scheme.surface,
        surface1 = scheme.surfaceContainerLowest,
        surface2 = scheme.surfaceContainer,
        surface3 = scheme.surfaceContainerHighest,
        articleCard = scheme.surfaceContainerLow,
        textPrimary = scheme.onSurface,
        textSecondary = scheme.onSurfaceVariant,
        textTertiary = scheme.onSurfaceVariant,
        divider = scheme.outlineVariant,
        accent = derived.accent,
        accentPressed = derived.accentPressed,
        onAccent = derived.onAccent,
        link = derived.link,
    )
}

/**
 * RssRadar 主题：深色 / 浅色两套色板 + 强调色来源。
 *
 * 色板来源三选一（优先级从高到低）：
 * 1. 自定义主色（#29）：只换强调色四件套，表面与文字保持当前色板。
 * 2. 系统动态取色（#27）：Android 12+ 取壁纸色，**整套表面与文字一并跟随**（见 [withSystemScheme]）；
 *    低于 12 的设备回退到固定色板，开关不生效。
 * 3. 固定紫调色板：[RadarColors.Dark] / [RadarColors.Light]。
 *
 * 深色保持 iOS Dark 风（纯黑背景），浅色用近白表面——这是回退色板的表现。
 *
 * 色板经 [LocalRadarColors] 注入，UI 层统一用 [radarColors] 读取；
 * M3 colorScheme 槽位由同一份 [RadarColors] 映射，供 M3 组件内部取色——
 * 因此开关动态取色时两边不会走偏。
 */
/**
 * RadarColors → M3 colorScheme。
 *
 * surfaceContainer 族必须显式映射：M3 组件的默认底色走的就是这几个 slot
 * （DropdownMenu←Container、ModalBottomSheet←ContainerLow、BottomAppBar / SegmentedButton
 * ←Container 系），不映射就会落到 [darkColorScheme] 的内置 baseline 色板（紫灰），
 * 与项目配色脱节——表现成「菜单 / 底部抽屉看着像没适配过的默认组件」。
 *
 * 阶梯与 [withSystemScheme] 的反向对应保持一致：
 * Lowest ← surface1 · Low ← articleCard · Container ← surface2 · Highest ← surface3。
 * 项目只有四档，High 沿用 Container。
 */
private fun darkScheme(colors: RadarColors) = darkColorScheme(
    primary = colors.accent,
    onPrimary = colors.onAccent,
    primaryContainer = colors.surface2,
    onPrimaryContainer = colors.textPrimary,
    secondary = colors.link,
    onSecondary = colors.onAccent,
    background = colors.bgRoot,
    onBackground = colors.textPrimary,
    surface = colors.surface1,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.surface2,
    onSurfaceVariant = colors.textSecondary,
    surfaceContainerLowest = colors.surface1,
    surfaceContainerLow = colors.articleCard,
    surfaceContainer = colors.surface2,
    surfaceContainerHigh = colors.surface2,
    surfaceContainerHighest = colors.surface3,
    outline = colors.divider,
    outlineVariant = colors.surface3,
    error = DarkError,
    onError = colors.onAccent,
)

private fun lightScheme(colors: RadarColors) = lightColorScheme(
    primary = colors.accent,
    onPrimary = colors.onAccent,
    primaryContainer = colors.surface2,
    onPrimaryContainer = colors.textPrimary,
    secondary = colors.link,
    onSecondary = colors.onAccent,
    background = colors.bgRoot,
    onBackground = colors.textPrimary,
    surface = colors.surface1,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.surface2,
    onSurfaceVariant = colors.textSecondary,
    surfaceContainerLowest = colors.surface1,
    surfaceContainerLow = colors.articleCard,
    surfaceContainer = colors.surface2,
    surfaceContainerHigh = colors.surface2,
    surfaceContainerHighest = colors.surface3,
    outline = colors.divider,
    outlineVariant = colors.surface3,
    error = LightError,
    onError = colors.onAccent,
)

@Composable
fun RssRadarTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean = false,
    customAccentArgb: Long? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // 色板快照注入 CompositionLocal，UI 读 radarColors() 时随主题切换自动重组。
    // 强调色三选一：自定义 > 系统动态取色 > 默认紫。两个上层开关由调用方保证互斥，
    // 这里只按优先级取，不替用户做「两个都开」的猜测。
    val colors = remember(darkTheme, dynamicColor, customAccentArgb, context) {
        val base = if (darkTheme) RadarColors.Dark else RadarColors.Light
        when {
            //自定义主色优先：只换强调色，表面与文字不动（用户明确挑了颜色，不该被系统取色覆盖）
            customAccentArgb != null -> {
                val accent = Color(customAccentArgb)
                base.withAccent(accent, onAccentFor(accent), darkTheme)
            }
            // 动态取色：整套色板跟随系统（表面 + 文字 + 强调色同源）
            dynamicColor && supportsDynamicColor() -> base.withSystemScheme(context, darkTheme)
            // 低版本回退固定色板
            else -> base
        }
    }
    CompositionLocalProvider(LocalRadarColors provides colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkScheme(colors) else lightScheme(colors),
            typography = Typography,
            content = content,
        )
    }
}
