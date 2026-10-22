package com.cycling.rssradar.core.model

/**
 * 正文字体族（issue #42）。纯 JVM 枚举：cssStack 供 WebView 模板直接拼接，
 * Compose 侧的 FontFamily 映射放 UI 层，保证本包可被 JVM 单测。
 */
enum class ReadingFontFamily(val label: String, val cssStack: String) {
    SYSTEM("系统", "-apple-system,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif"),
    SERIF("衬线", "Georgia,'Noto Serif SC','Songti SC',serif"),
    MONOSPACE("等宽", "Menlo,Consolas,'Courier New',monospace"),
}

/**
 * 正文对齐（ReadYou 差距表第 17 项）。
 *
 * 只对**没有自带对齐声明**的段落生效：正文里 `<p align="center">` 这种显式声明
 * 是内容的一部分，用户的全局偏好不该盖掉它（原生路靠 TextStyle 合并天然做到，
 * WebView 路靠 CSS 选择器同样如此）。
 */
enum class ReadingTextAlign(val label: String, val css: String) {
    START("左对齐", "left"),
    JUSTIFY("两端对齐", "justify"),
    CENTER("居中", "center"),
    END("右对齐", "right"),
}

/** 阅读排版状态。纯数据类，无 Android 依赖，是 styled-HTML 构建缝的输入。 */
data class ReadingStyleState(
    val fontSize: Int = DEFAULT_FONT_SIZE,
    val lineHeight: Float = DEFAULT_LINE_HEIGHT,
    val horizontalPadding: Int = DEFAULT_PADDING,
    val fontFamily: ReadingFontFamily = ReadingFontFamily.SYSTEM,
    /**
     * 字间距（sp，ReadYou 差距表第 17 项）。默认 0 = 引入前的排版，老用户升级视觉不变。
     * 中文长段落拉开一点字距明显好读；上限 3sp 是再大就散架的经验值。
     */
    val letterSpacing: Float = DEFAULT_LETTER_SPACING,
    val textAlign: ReadingTextAlign = ReadingTextAlign.START,
) {
    companion object {
        const val DEFAULT_FONT_SIZE = 17
        const val DEFAULT_LINE_HEIGHT = 1.0f
        const val DEFAULT_PADDING = 24
        const val DEFAULT_LETTER_SPACING = 0f

        const val FONT_SIZE_MIN = 12
        const val FONT_SIZE_MAX = 28
        const val LINE_HEIGHT_MIN = 0.8f
        const val LINE_HEIGHT_MAX = 2.5f
        const val PADDING_MIN = 0
        const val PADDING_MAX = 48
        const val LETTER_SPACING_MIN = 0f
        const val LETTER_SPACING_MAX = 3f
    }
}

/** 数值夹取：UI 输入与持久化读取共用，纯函数（单一测试缝的一部分）。 */
fun coerceFontSize(value: Int): Int =
    value.coerceIn(ReadingStyleState.FONT_SIZE_MIN, ReadingStyleState.FONT_SIZE_MAX)

fun coerceLineHeight(value: Float): Float =
    value.coerceIn(ReadingStyleState.LINE_HEIGHT_MIN, ReadingStyleState.LINE_HEIGHT_MAX)

fun coercePadding(value: Int): Int =
    value.coerceIn(ReadingStyleState.PADDING_MIN, ReadingStyleState.PADDING_MAX)

fun coerceLetterSpacing(value: Float): Float =
    value.coerceIn(ReadingStyleState.LETTER_SPACING_MIN, ReadingStyleState.LETTER_SPACING_MAX)

/**
 * 阅读主题（ReadYou 差距表第 16 项）：阅读页专属配色。
 *
 * 只覆盖背景/表面/文字，**强调色仍跟随应用**——阅读主题是「纸的颜色」，
 * 不是换一套品牌色；#29 自定义的强调色在这里照样生效。
 *
 * **各档固定、不随深色模式变**：挑「纸张」就是在深色模式下也要米黄纸，
 * 若跟随系统深浅就失去了手动选它的意义。默认 [FOLLOW]，老用户升级视觉不变。
 */
enum class ReadingTheme(val label: String) {
    /** 用应用当前色板（深色纯黑 / 浅色近白）。 */
    FOLLOW("跟随应用"),

    /** 米黄纸：长文最不刺眼的一档。 */
    PAPER("纸张"),

    /** 浅灰：Reeder 那种中性灰底。 */
    GRAY("淡灰"),

    /** 深灰（非纯黑）：OLED 上不发光，比纯黑柔和。 */
    NIGHT("夜间灰"),
}

/**
 * 阅读页正文渲染器选择（原生双渲染器）。
 *
 * 默认 WEBVIEW：原生路对表格/视频/内联样式明显退化（已知取舍），
 * 默认开会让多数文章变丑；被 WebView 滚动闪烁困扰的用户手动切原生即可。
 */
enum class ReadingRenderer(val label: String) {
    WEBVIEW("WebView"),
    NATIVE("原生 Compose"),
}
