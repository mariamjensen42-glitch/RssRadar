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
        preferSummary: Boolean = false,
        immersive: Boolean = false,
    ) = resolveBodyPlan(translationActive, segments, content, summary, renderer, preferSummary, immersive)

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

    // ---- 正文 / 摘要 切换（ReadYou 的 renderDescriptionContent 同款） ----

    @Test
    fun `summary switch is offered only when the two differ materially`() {
        val full = "<p>" + "字".repeat(600) + "</p>"
        val brief = "字".repeat(100)
        assertTrue(canSwitchToSummary(full, brief))

        // 正文就是摘要本身（ADR-0001「取较长者」的直接后果）：切了等于没切
        assertFalse(canSwitchToSummary(brief, brief))
        assertFalse(canSwitchToSummary(null, brief))
        assertFalse(canSwitchToSummary(full, null))
        assertFalse(canSwitchToSummary("", brief))
        assertFalse(canSwitchToSummary(full, "   "))
        // 差距不足 120 字，切过去读者看不出区别
        assertFalse(canSwitchToSummary("字".repeat(200), "字".repeat(130)))
    }

    @Test
    fun `preferring summary swaps the body source but keeps the render path`() {
        val content = "<p>" + "字".repeat(600) + "</p>"
        val summary = "<p>" + "摘".repeat(300) + "</p>"

        val asSummary = plan(content = content, summary = summary, preferSummary = true)
        assertEquals(BodyMode.WEBVIEW, asSummary.mode)
        assertTrue(asSummary.summaryMode)

        val asFull = plan(content = content, summary = summary)
        assertEquals(BodyMode.WEBVIEW, asFull.mode)
        assertFalse(asFull.summaryMode)
    }

    @Test
    fun `preferring summary falls back to content when the summary is blank`() {
        // 摘要为空还硬切过去 = 给读者一个空白页，比「点了没反应」更糟
        val result = plan(content = "<p>正文</p>", summary = "   ", preferSummary = true)

        assertEquals(BodyMode.WEBVIEW, result.mode)
        assertFalse(result.summaryMode)
    }

    @Test
    fun `summary mode also applies to the native renderer`() {
        val content = "<p>" + "字".repeat(600) + "</p>"
        val summary = "<p>摘要一句话</p>"

        val result = plan(
            content = content,
            summary = summary,
            renderer = ReadingRenderer.NATIVE,
            preferSummary = true,
        )

        assertEquals(BodyMode.NATIVE, result.mode)
        assertTrue(result.summaryMode)
        // 渲染的是摘要那棵树，不是正文的
        assertEquals(1, result.nativeNodes.size)
    }
}
