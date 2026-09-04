package com.cycling.rssradar.core.data.store

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 正文字体族（issue #42）。纯 JVM 枚举：cssStack 供 WebView 模板直接拼接，
 * Compose 侧的 FontFamily 映射放 UI 层，保证本包可被 JVM 单测。
 */
enum class ReadingFontFamily(val label: String, val cssStack: String) {
    SYSTEM("系统", "-apple-system,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif"),
    SERIF("衬线", "Georgia,'Noto Serif SC','Songti SC',serif"),
    MONOSPACE("等宽", "Menlo,Consolas,'Courier New',monospace"),
}

/** 阅读排版状态。纯数据类，无 Android 依赖，是 styled-HTML 构建缝的输入。 */
data class ReadingStyleState(
    val fontSize: Int = DEFAULT_FONT_SIZE,
    val lineHeight: Float = DEFAULT_LINE_HEIGHT,
    val horizontalPadding: Int = DEFAULT_PADDING,
    val fontFamily: ReadingFontFamily = ReadingFontFamily.SYSTEM,
) {
    companion object {
        const val DEFAULT_FONT_SIZE = 17
        const val DEFAULT_LINE_HEIGHT = 1.0f
        const val DEFAULT_PADDING = 24

        const val FONT_SIZE_MIN = 12
        const val FONT_SIZE_MAX = 28
        const val LINE_HEIGHT_MIN = 0.8f
        const val LINE_HEIGHT_MAX = 2.5f
        const val PADDING_MIN = 0
        const val PADDING_MAX = 48
    }
}

/** 数值夹取：UI 输入与持久化读取共用，纯函数（单一测试缝的一部分）。 */
fun coerceFontSize(value: Int): Int =
    value.coerceIn(ReadingStyleState.FONT_SIZE_MIN, ReadingStyleState.FONT_SIZE_MAX)

fun coerceLineHeight(value: Float): Float =
    value.coerceIn(ReadingStyleState.LINE_HEIGHT_MIN, ReadingStyleState.LINE_HEIGHT_MAX)

fun coercePadding(value: Int): Int =
    value.coerceIn(ReadingStyleState.PADDING_MIN, ReadingStyleState.PADDING_MAX)

/**
 * 阅读页图片显示偏好（图片圆角 / 点击放大，ReadYou 差距表第 19 项）。
 *
 * 默认值 = 引入本功能前的渲染结果（`border-radius:8px` / `RoundedCornerShape(8.dp)`），
 * 老用户升级后视觉不变（与列表显示项 issue #56 同一原则）。
 * 点击放大默认开：ReadYou 也是默认 ON。
 */
data class ReadingImageState(
    val cornerRadius: Int = DEFAULT_CORNER_RADIUS,
    val maximizeOnTap: Boolean = true,
) {
    companion object {
        const val DEFAULT_CORNER_RADIUS = 8
        const val CORNER_RADIUS_MIN = 0
        const val CORNER_RADIUS_MAX = 24
    }
}

fun coerceImageCornerRadius(value: Int): Int =
    value.coerceIn(ReadingImageState.CORNER_RADIUS_MIN, ReadingImageState.CORNER_RADIUS_MAX)

/**
 * 阅读页正文渲染器选择（原生双渲染器，ADR-0009）。
 *
 * 默认 WEBVIEW：原生路对表格/视频/内联样式明显退化（ADR-0009 已记录），
 * 默认开会让多数文章变丑；被 WebView 滚动闪烁困扰的用户手动切原生即可。
 */
enum class ReadingRenderer(val label: String) {
    WEBVIEW("WebView"),
    NATIVE("原生 Compose"),
}

/** 译文显示模式：纯译文（替换式）或双语对照。 */
enum class TranslationViewMode { TRANSLATION_ONLY, BILINGUAL }

/** 双语对照的排布：上下堆叠或左右并排。 */
enum class BilingualLayout { STACKED, SIDE_BY_SIDE }

/**
 * 译文显示偏好（翻译功能 v2）：显示模式 + 双语排布。
 * 与翻译过程状态（VM 的 TranslationState）分离——过程态随文章生灭，
 * 这里是用户级环境偏好。
 *
 * 默认纯译文 + 上下：替换式翻译是既有行为，老用户升级后视觉不变。
 */
data class TranslationDisplayState(
    val viewMode: TranslationViewMode = TranslationViewMode.TRANSLATION_ONLY,
    val bilingualLayout: BilingualLayout = BilingualLayout.STACKED,
)

/**
 * **阅读偏好**（Reading preferences）：阅读页一整套只影响呈现、不影响内容的
 * 用户级设置——排版四项（字号/行距/边距/字体族）、图片两项、正文渲染器、译文显示方式。
 *
 * 四项原先各是一个独立 Store（ReadingStyleStore / ReadingImageStore /
 * ReadingRendererStore / TranslationDisplayStore），每个都要重复一套
 * 「Hilt provides → EntryPoint → CompositionLocal 声明 → 取值 → collect → provides
 * → ViewModel 构造 → ViewModel 暴露 → ViewModel 写方法」九点接线。合成一份 state
 * 后接线只剩一条，四项降为本模块的内部缝。
 *
 * _Avoid_: 阅读设置、排版设置、显示偏好
 */
data class ReadingPrefs(
    val style: ReadingStyleState = ReadingStyleState(),
    val image: ReadingImageState = ReadingImageState(),
    val renderer: ReadingRenderer = ReadingRenderer.WEBVIEW,
    val translation: TranslationDisplayState = TranslationDisplayState(),
)

/**
 * 阅读偏好模块：持久化 + 运行态共享的唯一入口。
 *
 * interface 就两个成员 —— [state] 与 [update]；夹取、默认值、枚举名回落、
 * 分片落盘全在实现里。调用方写 `update { it.copy(...) }` 即可，
 * 落盘值永远合法（写入前统一过 [coerce]），不必自己记得 coerce。
 *
 * 测试缝：构造只吃 [SharedPreferences]，JVM 测试塞内存实例即可（见 FakeSharedPreferences）。
 */
class ReadingPrefsStore(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(readPersisted())
    val state: StateFlow<ReadingPrefs> = _state.asStateFlow()

    /**
     * 变换当前偏好并持久化。写入前统一 [coerce]，因此任何调用方写进来的值都不可能
     * 把非法范围落盘——「落盘值永远合法」这条不变量住在模块里，不靠调用方自觉。
     */
    fun update(transform: (ReadingPrefs) -> ReadingPrefs) {
        val next = coerce(transform(_state.value))
        prefs.edit()
            .putInt(KEY_FONT_SIZE, next.style.fontSize)
            .putFloat(KEY_LINE_HEIGHT, next.style.lineHeight)
            .putInt(KEY_PADDING, next.style.horizontalPadding)
            .putString(KEY_FONT_FAMILY, next.style.fontFamily.name)
            .putInt(KEY_CORNER_RADIUS, next.image.cornerRadius)
            .putBoolean(KEY_MAXIMIZE, next.image.maximizeOnTap)
            .putString(KEY_RENDERER, next.renderer.name)
            .putString(KEY_VIEW_MODE, next.translation.viewMode.name)
            .putString(KEY_BILINGUAL_LAYOUT, next.translation.bilingualLayout.name)
            .apply()
        _state.value = next
    }

    private fun readPersisted(): ReadingPrefs = ReadingPrefs(
        style = ReadingStyleState(
            fontSize = coerceFontSize(prefs.getInt(KEY_FONT_SIZE, ReadingStyleState.DEFAULT_FONT_SIZE)),
            lineHeight = coerceLineHeight(
                prefs.getFloat(KEY_LINE_HEIGHT, ReadingStyleState.DEFAULT_LINE_HEIGHT),
            ),
            horizontalPadding = coercePadding(
                prefs.getInt(KEY_PADDING, ReadingStyleState.DEFAULT_PADDING),
            ),
            // 枚举名读不到（历史脏数据/改名）时回退默认值，不让老用户崩在启动路径上
            fontFamily = prefs.getString(KEY_FONT_FAMILY, null)
                ?.let { runCatching { ReadingFontFamily.valueOf(it) }.getOrNull() }
                ?: ReadingFontFamily.SYSTEM,
        ),
        image = ReadingImageState(
            cornerRadius = coerceImageCornerRadius(
                prefs.getInt(KEY_CORNER_RADIUS, ReadingImageState.DEFAULT_CORNER_RADIUS),
            ),
            maximizeOnTap = prefs.getBoolean(KEY_MAXIMIZE, true),
        ),
        renderer = prefs.getString(KEY_RENDERER, null)
            ?.let { runCatching { ReadingRenderer.valueOf(it) }.getOrNull() }
            ?: ReadingRenderer.WEBVIEW,
        translation = TranslationDisplayState(
            viewMode = prefs.getString(KEY_VIEW_MODE, null)
                ?.let { runCatching { TranslationViewMode.valueOf(it) }.getOrNull() }
                ?: TranslationViewMode.TRANSLATION_ONLY,
            bilingualLayout = prefs.getString(KEY_BILINGUAL_LAYOUT, null)
                ?.let { runCatching { BilingualLayout.valueOf(it) }.getOrNull() }
                ?: BilingualLayout.STACKED,
        ),
    )

    private fun coerce(prefs: ReadingPrefs): ReadingPrefs = prefs.copy(
        style = prefs.style.copy(
            fontSize = coerceFontSize(prefs.style.fontSize),
            lineHeight = coerceLineHeight(prefs.style.lineHeight),
            horizontalPadding = coercePadding(prefs.style.horizontalPadding),
        ),
        image = prefs.image.copy(
            cornerRadius = coerceImageCornerRadius(prefs.image.cornerRadius),
        ),
    )

    private companion object {
        // 键沿用拆分前四个 Store 的原键：老用户升级后设置原样保留，不走迁移
        const val KEY_FONT_SIZE = "reading_font_size"
        const val KEY_LINE_HEIGHT = "reading_line_height"
        const val KEY_PADDING = "reading_horizontal_padding"
        const val KEY_FONT_FAMILY = "reading_font_family"
        const val KEY_CORNER_RADIUS = "reading_image_corner_radius"
        const val KEY_MAXIMIZE = "reading_image_maximize_on_tap"
        const val KEY_RENDERER = "reading_renderer"
        const val KEY_VIEW_MODE = "translation_view_mode"
        const val KEY_BILINGUAL_LAYOUT = "translation_bilingual_layout"
    }
}
