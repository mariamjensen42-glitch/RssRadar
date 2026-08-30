package com.cycling.rssradar.data.store

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
 * 阅读排版偏好持久化 + 运行态共享（ThemeStore 同款模式）。
 * 设置弹层改参数 → StateFlow 更新 → 主题宿主注入的 CompositionLocal 跟着重组，即改即见。
 */
class ReadingStyleStore(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(readPersisted())
    val state: StateFlow<ReadingStyleState> = _state.asStateFlow()

    /** 变换当前状态并持久化；写入前统一 coerce，保证落盘值永远合法。 */
    fun update(transform: (ReadingStyleState) -> ReadingStyleState) {
        val coerced = transform(_state.value).let {
            it.copy(
                fontSize = coerceFontSize(it.fontSize),
                lineHeight = coerceLineHeight(it.lineHeight),
                horizontalPadding = coercePadding(it.horizontalPadding),
            )
        }
        prefs.edit()
            .putInt(KEY_FONT_SIZE, coerced.fontSize)
            .putFloat(KEY_LINE_HEIGHT, coerced.lineHeight)
            .putInt(KEY_PADDING, coerced.horizontalPadding)
            .putString(KEY_FONT_FAMILY, coerced.fontFamily.name)
            .apply()
        _state.value = coerced
    }

    private fun readPersisted(): ReadingStyleState = ReadingStyleState(
        fontSize = coerceFontSize(prefs.getInt(KEY_FONT_SIZE, ReadingStyleState.DEFAULT_FONT_SIZE)),
        lineHeight = coerceLineHeight(prefs.getFloat(KEY_LINE_HEIGHT, ReadingStyleState.DEFAULT_LINE_HEIGHT)),
        horizontalPadding = coercePadding(prefs.getInt(KEY_PADDING, ReadingStyleState.DEFAULT_PADDING)),
        fontFamily = prefs.getString(KEY_FONT_FAMILY, null)
            ?.let { name -> runCatching { ReadingFontFamily.valueOf(name) }.getOrNull() }
            ?: ReadingFontFamily.SYSTEM,
    )

    companion object {
        private const val KEY_FONT_SIZE = "reading_font_size"
        private const val KEY_LINE_HEIGHT = "reading_line_height"
        private const val KEY_PADDING = "reading_horizontal_padding"
        private const val KEY_FONT_FAMILY = "reading_font_family"
    }
}
