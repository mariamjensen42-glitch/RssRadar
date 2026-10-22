package com.cycling.rssradar.core.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// —— 强调色派生（#27 动态取色 / #29 自定义主色）——
// 系统（Monet）只给 primary / onPrimary 两枚且已保证对比度；link 是本项目自己的派生色，
// 比例即设计判据，因此抽成纯函数单测。

/** 深色下 link 相对 accent 提亮比例：纯黑背景上 accent 偏沉，提亮才看得出是链接。 */
internal const val LINK_LIGHTEN = 0.25f

/** 浅色下 link 相对 accent 压暗比例：白底上再提亮会掉对比度，所以反向压暗。 */
internal const val LINK_DARKEN = 0.10f

private val MIX_DARK = Color(0xFF000000)
private val MIX_LIGHT = Color(0xFFFFFFFF)

/** 强调色三件套：本体 / 画在 accent 之上的前景 / 链接。 */
data class AccentColors(
    val accent: Color,
    val onAccent: Color,
    val link: Color,
)

/**
 * sRGB 通道线性插值。
 *
 * 不用 `androidx.compose.ui.graphics.lerp`：它会在 Oklab 里转一圈，
 * 顺便把 `ui-util` 拽进运行时——为一枚派生色多背一份依赖不值得（本地单测
 * classpath 因此缺类直接挂过）。混色比例只有 10%~25%，线性空间与感知空间的
 * 差异在这点幅度上看不出来。
 */
private fun mix(from: Color, to: Color, fraction: Float): Color = Color(
    red = from.red + (to.red - from.red) * fraction,
    green = from.green + (to.green - from.green) * fraction,
    blue = from.blue + (to.blue - from.blue) * fraction,
    alpha = from.alpha + (to.alpha - from.alpha) * fraction,
)

/**
 * 由系统给出的 [accent] / [onAccent] 派生 link。纯函数，便于单测。
 *
 * 动态色板的 primary/onPrimary 由系统保证对比度，本项目只负责 link 一枚派生色：
 * 深色下提亮（纯黑背景吃色），浅色下压暗（白底提亮掉对比度）。
 */
fun deriveAccentColors(accent: Color, onAccent: Color, darkTheme: Boolean): AccentColors =
    AccentColors(
        accent = accent,
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
 * 自定义主色（#29）：只换强调色三件套，表面与文字不动。
 *
 * 容器色故意指向中性 surface 族而不是 accent 派生色——用户调的是「主色」，
 * 不是让整块容器跟着染色；M3 默认的 primaryContainer 是 accent 的浅色变体，
 * 与「只换强调色」的约定不符，所以这里显式压回表面阶梯。
 */
private fun ColorScheme.withAccent(accent: Color, darkTheme: Boolean): ColorScheme {
    val derived = deriveAccentColors(accent, onAccentFor(accent), darkTheme)
    return copy(
        primary = derived.accent,
        onPrimary = derived.onAccent,
        primaryContainer = surfaceContainer,
        onPrimaryContainer = onSurface,
        secondary = derived.link,
        onSecondary = derived.onAccent,
    )
}

/**
 * RssRadar 主题：M3 Expressive 官方色板 + 强调色来源。
 *
 * 色板来源三选一（优先级从高到低）：
 * 1. 自定义主色（#29）：只换强调色三件套，表面与文字保持当前色板。
 * 2. 系统动态取色（#27，开关默认开）：Android 12+ 取壁纸色；低于 12 回退 M3 基线色板。
 * 3. M3 基线色板：`darkColorScheme()` / `lightColorScheme()`。
 *
 * 用 [MaterialExpressiveTheme] 而不是 `MaterialTheme`：后者的 motionScheme 默认是
 * `MotionScheme.standard()`，不显式给就等于全 app 走非 Expressive 动效。
 */
@Composable
fun RssRadarTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean = false,
    customAccentArgb: Long? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = remember(darkTheme, dynamicColor, customAccentArgb, context) {
        val base = if (dynamicColor && supportsDynamicColor()) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (darkTheme) darkColorScheme() else lightColorScheme()
        }
        customAccentArgb?.let { base.withAccent(Color(it), darkTheme) } ?: base
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content,
    )
}
