package com.cycling.rssradar.core.data.store

import com.cycling.rssradar.core.data.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阅读偏好模块：默认值、落盘、越界夹取与重启后恢复。
 *
 * 排版 / 图片 / 渲染器 / 译文显示四项合成一份 state 后，这些规则全部集中在
 * 一个模块里——这里逐条断言，取代原先分散在四个 Store（实际只有图片那一个有测试）。
 */
class ReadingPrefsStoreTest {

    private fun store(prefs: FakeSharedPreferences = FakeSharedPreferences()) =
        ReadingPrefsStore(prefs) to prefs

    // ---- 默认值 ----

    @Test
    fun `defaults keep the pre feature look`() {
        val (s, _) = store()

        assertEquals(ReadingStyleState.DEFAULT_FONT_SIZE, s.state.value.style.fontSize)
        assertEquals(ReadingStyleState.DEFAULT_LINE_HEIGHT, s.state.value.style.lineHeight)
        assertEquals(ReadingStyleState.DEFAULT_PADDING, s.state.value.style.horizontalPadding)
        assertEquals(ReadingFontFamily.SYSTEM, s.state.value.style.fontFamily)
        assertEquals(ReadingImageState.DEFAULT_CORNER_RADIUS, s.state.value.image.cornerRadius)
        assertTrue(s.state.value.image.maximizeOnTap)
        assertEquals(ReadingRenderer.NATIVE, s.state.value.renderer)
        assertEquals(TranslationViewMode.TRANSLATION_ONLY, s.state.value.translation.viewMode)
        assertEquals(BilingualLayout.STACKED, s.state.value.translation.bilingualLayout)
    }

    // ---- 落盘与恢复 ----

    @Test
    fun `every group survives a store restart`() {
        val prefs = FakeSharedPreferences()
        ReadingPrefsStore(prefs).update {
            it.copy(
                style = it.style.copy(fontSize = 21, fontFamily = ReadingFontFamily.SERIF),
                image = it.image.copy(cornerRadius = 16, maximizeOnTap = false),
                renderer = ReadingRenderer.NATIVE,
                translation = TranslationDisplayState(
                    viewMode = TranslationViewMode.BILINGUAL,
                    bilingualLayout = BilingualLayout.SIDE_BY_SIDE,
                ),
            )
        }

        val reloaded = ReadingPrefsStore(prefs).state.value
        assertEquals(21, reloaded.style.fontSize)
        assertEquals(ReadingFontFamily.SERIF, reloaded.style.fontFamily)
        assertEquals(16, reloaded.image.cornerRadius)
        assertFalse(reloaded.image.maximizeOnTap)
        assertEquals(ReadingRenderer.NATIVE, reloaded.renderer)
        assertEquals(TranslationViewMode.BILINGUAL, reloaded.translation.viewMode)
        assertEquals(BilingualLayout.SIDE_BY_SIDE, reloaded.translation.bilingualLayout)
    }

    @Test
    fun `keys match the pre merge stores so existing settings survive upgrade`() {
        val prefs = FakeSharedPreferences()
        ReadingPrefsStore(prefs).update { it.copy(style = it.style.copy(fontSize = 22)) }

        // 沿用拆分前四个 Store 的原键，不迁移
        assertEquals(22, prefs.getInt("reading_font_size", -1))
    }

    // ---- 夹取：写入与读取两侧都夹 ----

    @Test
    fun `out of range writes are clamped before persisting`() {
        val (s, prefs) = store()

        s.update {
            it.copy(
                style = it.style.copy(fontSize = 999, lineHeight = 9f, horizontalPadding = -40),
                image = it.image.copy(cornerRadius = 999),
            )
        }

        val v = s.state.value
        assertEquals(ReadingStyleState.FONT_SIZE_MAX, v.style.fontSize)
        assertEquals(ReadingStyleState.LINE_HEIGHT_MAX, v.style.lineHeight)
        assertEquals(ReadingStyleState.PADDING_MIN, v.style.horizontalPadding)
        assertEquals(ReadingImageState.CORNER_RADIUS_MAX, v.image.cornerRadius)
        // 落盘值同样是夹过的，绝不写入非法范围
        assertEquals(ReadingStyleState.FONT_SIZE_MAX, prefs.getInt("reading_font_size", -1))
        assertEquals(ReadingImageState.CORNER_RADIUS_MAX, prefs.getInt("reading_image_corner_radius", -1))
    }

    @Test
    fun `out of range persisted values are clamped on read`() {
        val prefs = FakeSharedPreferences()
        prefs.edit()
            .putInt("reading_font_size", 80)
            .putFloat("reading_line_height", 0.1f)
            .putInt("reading_image_corner_radius", 80)
            .apply()

        val v = ReadingPrefsStore(prefs).state.value
        assertEquals(ReadingStyleState.FONT_SIZE_MAX, v.style.fontSize)
        assertEquals(ReadingStyleState.LINE_HEIGHT_MIN, v.style.lineHeight)
        assertEquals(ReadingImageState.CORNER_RADIUS_MAX, v.image.cornerRadius)
    }

    // ---- 脏数据回落 ----

    @Test
    fun `unknown enum names fall back to defaults instead of throwing`() {
        val prefs = FakeSharedPreferences()
        prefs.edit()
            .putString("reading_font_family", "NOT_A_FONT")
            .putString("reading_renderer", "NOT_A_RENDERER")
            .putString("translation_view_mode", "NOT_A_MODE")
            .putString("translation_bilingual_layout", "NOT_A_LAYOUT")

        val v = ReadingPrefsStore(prefs).state.value
        assertEquals(ReadingFontFamily.SYSTEM, v.style.fontFamily)
        assertEquals(ReadingRenderer.NATIVE, v.renderer)
        assertEquals(TranslationViewMode.TRANSLATION_ONLY, v.translation.viewMode)
        assertEquals(BilingualLayout.STACKED, v.translation.bilingualLayout)
    }

    // ---- 各组互不干扰 ----

    @Test
    fun `updating one group leaves the others untouched`() {
        val (s, _) = store()
        s.update { it.copy(renderer = ReadingRenderer.NATIVE) }

        assertEquals(ReadingRenderer.NATIVE, s.state.value.renderer)
        assertEquals(ReadingStyleState.DEFAULT_FONT_SIZE, s.state.value.style.fontSize)
        assertEquals(ReadingImageState.DEFAULT_CORNER_RADIUS, s.state.value.image.cornerRadius)
        assertEquals(TranslationViewMode.TRANSLATION_ONLY, s.state.value.translation.viewMode)
    }
}
