package com.cycling.rssradar.core.data.store.prefs

import android.content.SharedPreferences
import com.cycling.rssradar.core.model.BilingualLayout
import com.cycling.rssradar.core.model.ReadingFontFamily
import com.cycling.rssradar.core.model.ReadingImageState
import com.cycling.rssradar.core.model.ReadingPrefs
import com.cycling.rssradar.core.model.ReadingRenderer
import com.cycling.rssradar.core.model.ReadingStyleState
import com.cycling.rssradar.core.model.ReadingTextAlign
import com.cycling.rssradar.core.model.ReadingTheme
import com.cycling.rssradar.core.model.TranslationDisplayState
import com.cycling.rssradar.core.model.TranslationViewMode
import com.cycling.rssradar.core.model.coerceFontSize
import com.cycling.rssradar.core.model.coerceImageCornerRadius
import com.cycling.rssradar.core.model.coerceLetterSpacing
import com.cycling.rssradar.core.model.coerceLineHeight
import com.cycling.rssradar.core.model.coercePadding
import com.cycling.rssradar.core.model.enumValueOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
            .putFloat(KEY_LETTER_SPACING, next.style.letterSpacing)
            .putString(KEY_TEXT_ALIGN, next.style.textAlign.name)
            .putInt(KEY_CORNER_RADIUS, next.image.cornerRadius)
            .putBoolean(KEY_MAXIMIZE, next.image.maximizeOnTap)
            .putString(KEY_RENDERER, next.renderer.name)
            .putString(KEY_VIEW_MODE, next.translation.viewMode.name)
            .putString(KEY_BILINGUAL_LAYOUT, next.translation.bilingualLayout.name)
            .putBoolean(KEY_IMMERSIVE, next.immersive)
            .putBoolean(KEY_AUTO_HIDE_BARS, next.autoHideBars)
            .putString(KEY_READING_THEME, next.readingTheme.name)
            .putBoolean(KEY_PULL_TO_SWITCH, next.pullToSwitchArticle)
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
            fontFamily = enumValueOrNull<ReadingFontFamily>(prefs.getString(KEY_FONT_FAMILY, null))
                ?: ReadingFontFamily.SYSTEM,
            letterSpacing = coerceLetterSpacing(
                prefs.getFloat(KEY_LETTER_SPACING, ReadingStyleState.DEFAULT_LETTER_SPACING),
            ),
            textAlign = enumValueOrNull<ReadingTextAlign>(prefs.getString(KEY_TEXT_ALIGN, null))
                ?: ReadingTextAlign.START,
        ),
        image = ReadingImageState(
            cornerRadius = coerceImageCornerRadius(
                prefs.getInt(KEY_CORNER_RADIUS, ReadingImageState.DEFAULT_CORNER_RADIUS),
            ),
            maximizeOnTap = prefs.getBoolean(KEY_MAXIMIZE, true),
        ),
        renderer = enumValueOrNull<ReadingRenderer>(prefs.getString(KEY_RENDERER, null))
            ?: ReadingRenderer.NATIVE,
        translation = TranslationDisplayState(
            viewMode = enumValueOrNull<TranslationViewMode>(prefs.getString(KEY_VIEW_MODE, null))
                ?: TranslationViewMode.TRANSLATION_ONLY,
            bilingualLayout = enumValueOrNull<BilingualLayout>(prefs.getString(KEY_BILINGUAL_LAYOUT, null))
                ?: BilingualLayout.STACKED,
        ),
        immersive = prefs.getBoolean(KEY_IMMERSIVE, true),
        autoHideBars = prefs.getBoolean(KEY_AUTO_HIDE_BARS, false),
        readingTheme = enumValueOrNull<ReadingTheme>(prefs.getString(KEY_READING_THEME, null))
            ?: ReadingTheme.FOLLOW,
        pullToSwitchArticle = prefs.getBoolean(KEY_PULL_TO_SWITCH, false),
    )

    private fun coerce(prefs: ReadingPrefs): ReadingPrefs = prefs.copy(
        style = prefs.style.copy(
            fontSize = coerceFontSize(prefs.style.fontSize),
            lineHeight = coerceLineHeight(prefs.style.lineHeight),
            horizontalPadding = coercePadding(prefs.style.horizontalPadding),
            letterSpacing = coerceLetterSpacing(prefs.style.letterSpacing),
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
        const val KEY_LETTER_SPACING = "reading_letter_spacing"
        const val KEY_TEXT_ALIGN = "reading_text_align"
        const val KEY_CORNER_RADIUS = "reading_image_corner_radius"
        const val KEY_MAXIMIZE = "reading_image_maximize_on_tap"
        const val KEY_RENDERER = "reading_renderer"
        const val KEY_VIEW_MODE = "translation_view_mode"
        const val KEY_BILINGUAL_LAYOUT = "translation_bilingual_layout"
        const val KEY_IMMERSIVE = "reading_immersive"
        const val KEY_AUTO_HIDE_BARS = "reading_auto_hide_bars"
        const val KEY_READING_THEME = "reading_theme"
        const val KEY_PULL_TO_SWITCH = "reading_pull_to_switch_article"
    }
}
