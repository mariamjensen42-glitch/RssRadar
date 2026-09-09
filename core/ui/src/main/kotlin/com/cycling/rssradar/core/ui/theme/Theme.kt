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

private fun RadarColors.withSystemAccent(context: Context, darkTheme: Boolean): RadarColors {
    val scheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    return withAccent(scheme.primary, scheme.onPrimary, darkTheme)
}

/**
 * RssRadar 主题：深色 / 浅色两套色板 + 强调色（固定紫，或 #27 动态取色）。
 * 深色保持 iOS Dark 风（纯黑背景），浅色用近白表面。
 *
 * 色板经 [LocalRadarColors] 注入，UI 层统一用 [radarColors] 读取；
 * M3 colorScheme 槽位由同一份 [RadarColors] 映射，供 M3 组件内部取色——
 * 因此开关动态取色时两边不会走偏。
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
            customAccentArgb != null -> {
                val accent = Color(customAccentArgb)
                base.withAccent(accent, onAccentFor(accent), darkTheme)
            }
            dynamicColor && supportsDynamicColor() -> base.withSystemAccent(context, darkTheme)
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
