package com.cycling.rssradar.data.store

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 译文显示模式：纯译文（替换式）或双语对照。 */
enum class TranslationViewMode { TRANSLATION_ONLY, BILINGUAL }

/** 双语对照的排布：上下堆叠或左右并排。 */
enum class BilingualLayout { STACKED, SIDE_BY_SIDE }

/**
 * 译文显示偏好（翻译功能 v2）：显示模式 + 双语排布。
 * 与翻译过程状态（VM 的 TranslationState）分离——过程态随文章生灭，
 * 这里是用户级环境偏好，阅读页横幅与译文渲染区共享同一数据源。
 *
 * 默认纯译文 + 上下：替换式翻译是既有行为，老用户升级后视觉不变。
 */
data class TranslationDisplayState(
    val viewMode: TranslationViewMode = TranslationViewMode.TRANSLATION_ONLY,
    val bilingualLayout: BilingualLayout = BilingualLayout.STACKED,
)

/** 译文显示偏好持久化 + 运行态共享（ReadingImageStore 同款模式）。 */
class TranslationDisplayStore(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(readPersisted())
    val state: StateFlow<TranslationDisplayState> = _state.asStateFlow()

    fun update(transform: (TranslationDisplayState) -> TranslationDisplayState) {
        val next = transform(_state.value)
        prefs.edit()
            .putString(KEY_VIEW_MODE, next.viewMode.name)
            .putString(KEY_BILINGUAL_LAYOUT, next.bilingualLayout.name)
            .apply()
        _state.value = next
    }

    private fun readPersisted(): TranslationDisplayState {
        // 枚举名读不到（历史脏数据/改名）时回退默认值
        val mode = prefs.getString(KEY_VIEW_MODE, null)
            ?.let { runCatching { TranslationViewMode.valueOf(it) }.getOrNull() }
            ?: TranslationViewMode.TRANSLATION_ONLY
        val layout = prefs.getString(KEY_BILINGUAL_LAYOUT, null)
            ?.let { runCatching { BilingualLayout.valueOf(it) }.getOrNull() }
            ?: BilingualLayout.STACKED
        return TranslationDisplayState(mode, layout)
    }

    companion object {
        private const val KEY_VIEW_MODE = "translation_view_mode"
        private const val KEY_BILINGUAL_LAYOUT = "translation_bilingual_layout"
    }
}
