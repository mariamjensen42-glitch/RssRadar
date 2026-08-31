package com.cycling.rssradar.data.store

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 阅读页正文渲染器选择（原生双渲染器，ADR-0009）。
 * 纯 JVM 枚举 + 数据类，与 ReadingStyleStore 同款模式，可被 JVM 单测。
 *
 * 默认 WEBVIEW：原生路对表格/视频/内联样式明显退化（ADR-0009 已记录），
 * 默认开会让多数文章变丑；被 WebView 滚动闪烁困扰的用户手动切原生即可。
 */
enum class ReadingRenderer(val label: String) {
    WEBVIEW("WebView"),
    NATIVE("原生 Compose"),
}

data class ReadingRendererState(val renderer: ReadingRenderer = ReadingRenderer.WEBVIEW)

/**
 * 渲染器偏好持久化 + 运行态共享（ThemeStore / ReadingStyleStore 同款模式）。
 * 设置页改 → StateFlow 更新 → 阅读页重组按新渲染器组合。
 */
class ReadingRendererStore(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(readPersisted())
    val state: StateFlow<ReadingRendererState> = _state.asStateFlow()

    fun set(renderer: ReadingRenderer) {
        prefs.edit().putString(KEY, renderer.name).apply()
        _state.value = ReadingRendererState(renderer)
    }

    private fun readPersisted(): ReadingRendererState {
        val name = prefs.getString(KEY, null)
        val renderer = runCatching { ReadingRenderer.valueOf(name ?: ReadingRenderer.WEBVIEW.name) }
            .getOrDefault(ReadingRenderer.WEBVIEW)
        return ReadingRendererState(renderer)
    }

    companion object {
        private const val KEY = "reading_renderer"
    }
}
