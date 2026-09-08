package com.cycling.rssradar.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** 默认强调色：与 [RadarColors] 的固定紫一致（#7B7CFF）。 */
const val DEFAULT_ACCENT_ARGB = 0xFF7B7CFFL

/**
 * 预设强调色（ARGB，#29）。第一枚即默认紫——选中它等于「回到默认」，
 * 不是「自定义了一个紫」。
 */
val ACCENT_PRESETS: List<Long> = listOf(
    0xFF7B7CFFL, // 默认紫
    0xFF5B8DEFL, // 蓝
    0xFF22A7F0L, // 天蓝
    0xFF12B3A6L, // 青
    0xFF2FA84FL, // 绿
    0xFF65A30DL, // 草绿
    0xFFD9A100L, // 琥珀
    0xFFE8722CL, // 橙
    0xFFE5484DL, // 红
    0xFFD6409FL, // 玫红
    0xFF8E4EC6L, // 紫罗兰
    0xFF64748BL, // 石板
)

// —— 前景色判据：白字还是黑字 ——
//
// 自定义色由用户随手挑，可能会被挑出一枚很浅的颜色，白字画上去直接糊掉。
// 所以前景色不算设计常量，而是按 WCAG 对比度算出来的——这是判据，必须能单测。

private const val LINEAR_CUTOFF = 0.03928f
private const val LINEAR_SLOPE = 12.92f
private const val SRGB_OFFSET = 0.055f
private const val SRGB_DIVISOR = 1.055f
private const val SRGB_EXPONENT = 2.4f
private const val LUMA_RED = 0.2126f
private const val LUMA_GREEN = 0.7152f
private const val LUMA_BLUE = 0.0722f
private const val CONTRAST_FLARE = 0.05f

private const val LUMA_WHITE = 1f
private const val LUMA_BLACK = 0f

private fun linearize(channel: Float): Float =
    if (channel <= LINEAR_CUTOFF) {
        channel / LINEAR_SLOPE
    } else {
        ((channel + SRGB_OFFSET) / SRGB_DIVISOR).pow(SRGB_EXPONENT)
    }

/** WCAG 相对亮度（0=纯黑，1=纯白）。 */
internal fun relativeLuminance(color: Color): Float =
    LUMA_RED * linearize(color.red) +
        LUMA_GREEN * linearize(color.green) +
        LUMA_BLUE * linearize(color.blue)

/** 两个亮度之间的 WCAG 对比度（1..21）。 */
internal fun contrastRatio(one: Float, other: Float): Float {
    val hi = maxOf(one, other)
    val lo = minOf(one, other)
    return (hi + CONTRAST_FLARE) / (lo + CONTRAST_FLARE)
}

/**
 * 自定义强调色的前景色：黑与白里挑对比度更高的那个。
 *
 * 只用于自定义色——固定配色（默认紫配白字）是设计决策，不能被算法改掉。
 */
fun onAccentFor(accent: Color): Color {
    val luma = relativeLuminance(accent)
    return if (contrastRatio(LUMA_WHITE, luma) >= contrastRatio(LUMA_BLACK, luma)) {
        Color(0xFFFFFFFFL)
    } else {
        Color(0xFF000000L)
    }
}

// —— HSL ↔ ARGB ——
//
// 滑杆按 HSL 调（拖「色相」不会顺手把颜色调灰，符合直觉），持久化存一枚 ARGB。
// 手写而非用 compose 的 Color.hsl：后者在色彩空间里绕一圈，把 ui-util 拽进
// 运行时，本地单测 classpath 里没有（同 Theme.kt 里 mix() 的教训）。

/** 滑杆三元组：hue 0..360，saturation / lightness 0..1。 */
data class HslColor(val hue: Float, val saturation: Float, val lightness: Float)

private fun channel(v: Float): Int = (v.coerceIn(0f, 1f) * 255f).roundToInt()

/** HSL → ARGB（标准 HSL 算法）。分量越界先夹紧，滑杆拖到底也不会算出脏颜色。 */
fun HslColor.toArgb(): Long {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    val c = (1f - abs(2f * l - 1f)) * s
    val sector = h / 60f
    val x = c * (1f - abs(sector % 2f - 1f))
    val (r, g, b) = when {
        sector < 1f -> Triple(c, x, 0f)
        sector < 2f -> Triple(x, c, 0f)
        sector < 3f -> Triple(0f, c, x)
        sector < 4f -> Triple(0f, x, c)
        sector < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return (0xFFL shl 24) or
        (channel(r + m).toLong() shl 16) or
        (channel(g + m).toLong() shl 8) or
        channel(b + m).toLong()
}

/** ARGB → HSL：[HslColor.toArgb] 的逆运算，用于把已存的颜色回填到滑杆。 */
fun argbToHsl(argb: Long): HslColor {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val hi = max(max(r, g), b)
    val lo = min(min(r, g), b)
    val l = (hi + lo) / 2f
    val delta = hi - lo
    if (delta == 0f) return HslColor(0f, 0f, l)
    val s = (delta / (1f - abs(2f * l - 1f))).coerceIn(0f, 1f)
    val hue = when (hi) {
        r -> (g - b) / delta
        g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    } * 60f
    return HslColor(((hue % 360f) + 360f) % 360f, s, l)
}
