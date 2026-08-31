package com.cycling.rssradar.data.store

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 阅读页图片显示偏好（图片圆角 / 点击放大，ReadYou 差距表第 19 项）。
 *
 * 与 [ReadingStyleState] 分开存：排版四项（字号/行距/边距/字体族）是"文字"维度，
 * 图片两项独立成 Store，避免给排版面板与 VM 塞进无关字段。
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
 * 图片偏好持久化 + 运行态共享（ReadingRendererStore 同款模式）。
 * 设置面板改 → StateFlow 更新 → CompositionLocal 注入 → 阅读页图片即时重绘。
 */
class ReadingImageStore(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(readPersisted())
    val state: StateFlow<ReadingImageState> = _state.asStateFlow()

    fun update(transform: (ReadingImageState) -> ReadingImageState) {
        val coerced = transform(_state.value)
            .let { it.copy(cornerRadius = coerceImageCornerRadius(it.cornerRadius)) }
        prefs.edit()
            .putInt(KEY_CORNER_RADIUS, coerced.cornerRadius)
            .putBoolean(KEY_MAXIMIZE, coerced.maximizeOnTap)
            .apply()
        _state.value = coerced
    }

    private fun readPersisted(): ReadingImageState = ReadingImageState(
        cornerRadius = coerceImageCornerRadius(
            prefs.getInt(KEY_CORNER_RADIUS, ReadingImageState.DEFAULT_CORNER_RADIUS),
        ),
        maximizeOnTap = prefs.getBoolean(KEY_MAXIMIZE, true),
    )

    companion object {
        private const val KEY_CORNER_RADIUS = "reading_image_corner_radius"
        private const val KEY_MAXIMIZE = "reading_image_maximize_on_tap"
    }
}
