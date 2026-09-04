package com.cycling.rssradar.ui.article

import com.cycling.rssradar.core.data.store.ReadingRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 正文渲染模式判据的 JVM 证明。
 *
 * 这五条分支此前是 [ReadingBody] 里八个派生 val 隐式拼出来的，埋在 Composable 内
 * 一条都测不到；挪成 [resolveBodyPlan] 后，「什么情况下走哪条路」在这里定案。
 */
class BodyModeTest {

    private fun segment(original: String, translated: String? = null) = TranslationSegmentUi(
        originalHtml = original,
        translatedHtml = translated,
        blocks = listOf(original),
    )

    private fun plan(
        translationActive: Boolean = false,
        segments: List<TranslationSegmentUi> = emptyList(),
        content: String? = null,
        summary: String? = null,
        renderer: ReadingRenderer = ReadingRenderer.WEBVIEW,
    ) = resolveBodyPlan(translationActive, segments, content, summary, renderer)

    // ---- 无正文 ----

    @Test
    fun `no content falls back to the summary only branch`() {
        val result = plan(content = null, summary = "摘要")

        assertEquals(BodyMode.NO_CONTENT, result.mode)
        assertTrue(result.nativeNodes.isEmpty())
        assertNull(result.fallbackHtml)
    }

    @Test
    fun `no content wins over the native renderer`() {
        val result = plan(content = null, renderer = ReadingRenderer.NATIVE)

        assertEquals(BodyMode.NO_CONTENT, result.mode)
    }

    // ---- WebView 路 ----

    @Test
    fun `webview renderer always takes the webview branch`() {
        val result = plan(content = "<p>正文</p>", renderer = ReadingRenderer.WEBVIEW)

        assertEquals(BodyMode.WEBVIEW, result.mode)
        assertTrue(result.nativeNodes.isEmpty())
    }

    // ---- 原生路 ----

    @Test
    fun `native renderer with parseable content takes the native branch and carries the tree`() {
        val result = plan(content = "<p>正文</p>", renderer = ReadingRenderer.NATIVE)

        assertEquals(BodyMode.NATIVE, result.mode)
        assertTrue("中间树必须随计划一起给出，否则调用方要解析第二遍", result.nativeNodes.isNotEmpty())
    }

    @Test
    fun `native renderer with an empty tree falls back to webview`() {
        // 解析一无所获（只剩注释）：绝不把正文渲染成空白页
        val result = plan(content = "<!-- 只有注释 -->", renderer = ReadingRenderer.NATIVE)

        assertEquals(BodyMode.WEBVIEW, result.mode)
    }

    // ---- 译文路 ----

    @Test
    fun `translation with renderable segments takes the translation branch`() {
        val result = plan(
            translationActive = true,
            segments = listOf(segment("<p>a</p>", "<p>甲</p>")),
            content = "<p>a</p>",
        )

        assertEquals(BodyMode.TRANSLATION, result.mode)
    }

    @Test
    fun `translation with no segments yet stays on the translation branch`() {
        // 渐进翻译刚起步：分段表还是空的，不该被误判成"全解析不出"而闪回 WebView
        val result = plan(translationActive = true, segments = emptyList(), content = "<p>a</p>")

        assertEquals(BodyMode.TRANSLATION, result.mode)
    }

    @Test
    fun `translation with partially translated segments stays on the translation branch`() {
        val result = plan(
            translationActive = true,
            segments = listOf(
                segment("<p>a</p>", "<p>甲</p>"),
                segment("<p>b</p>", null), // 还没翻到，原文淡显
            ),
        )

        assertEquals(BodyMode.TRANSLATION, result.mode)
    }

    // ---- 译文兜底 ----

    @Test
    fun `translation whose segments parse to nothing falls back to joined translations`() {
        val result = plan(
            translationActive = true,
            segments = listOf(
                segment("<!-- 怪 HTML -->", "<!-- 译不出来 -->"),
                segment("<!-- 又一段 -->", "  "),
            ),
            content = "<p>原文</p>",
            summary = "摘要",
        )

        assertEquals(BodyMode.TRANSLATION_FALLBACK, result.mode)
        assertEquals("<!-- 译不出来 -->  ", result.fallbackHtml)
    }

    @Test
    fun `fallback drops to the original content when nothing was translated`() {
        val result = plan(
            translationActive = true,
            segments = listOf(segment("<!-- 怪 HTML -->", null)),
            content = "<p>原文</p>",
            summary = "摘要",
        )

        assertEquals(BodyMode.TRANSLATION_FALLBACK, result.mode)
        assertEquals("<p>原文</p>", result.fallbackHtml)
    }

    @Test
    fun `fallback drops to the summary when there is no content either`() {
        val result = plan(
            translationActive = true,
            segments = listOf(segment("<!-- 怪 HTML -->", null)),
            content = null,
            summary = "摘要",
        )

        assertEquals(BodyMode.TRANSLATION_FALLBACK, result.mode)
        assertEquals("摘要", result.fallbackHtml)
    }

    @Test
    fun `translation takes priority over the renderer choice`() {
        // 译文一律走原生分段渲染，渲染器偏好不影响它
        val result = plan(
            translationActive = true,
            segments = listOf(segment("<p>a</p>", "<p>甲</p>")),
            content = "<p>a</p>",
            renderer = ReadingRenderer.WEBVIEW,
        )

        assertEquals(BodyMode.TRANSLATION, result.mode)
    }

    // ---- 视口渲染（ADR-0007 的 OOM 防线） ----

    @Test
    fun `viewport only applies to illustrated webview content`() {
        assertTrue(shouldUseViewport(BodyMode.WEBVIEW, "<p>x</p><img src='a'>"))
        assertTrue(shouldUseViewport(BodyMode.WEBVIEW, "<IMG SRC='a'>"))

        // 纯文字的 WebView 栅格内存可控，不必走视口
        assertFalse(shouldUseViewport(BodyMode.WEBVIEW, "<p>纯文字</p>"))
        // 原生路与译文路是 Compose 渲染，没有这条约束
        assertFalse(shouldUseViewport(BodyMode.NATIVE, "<img src='a'>"))
        assertFalse(shouldUseViewport(BodyMode.TRANSLATION, "<img src='a'>"))
        assertFalse(shouldUseViewport(BodyMode.NO_CONTENT, null))
    }
}
