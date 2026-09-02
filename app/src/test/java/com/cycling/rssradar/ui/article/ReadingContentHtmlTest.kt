package com.cycling.rssradar.ui.article

import com.cycling.rssradar.data.store.ReadingImageState
import com.cycling.rssradar.data.store.ReadingFontFamily
import com.cycling.rssradar.data.store.ReadingStyleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingContentHtmlTest {

    private val colors = linkedMapOf(
        "bg" to "#0E0F13",
        "fg" to "#E8E8EC",
        "muted" to "#9A9AA6",
        "codeBg" to "#1C1C22",
        "border" to "#26262E",
        "link" to "#9B9CFF",
    )

    private fun build(
        content: String,
        style: ReadingStyleState = ReadingStyleState(),
        imageUrls: Set<String> = emptySet(),
        imageCorners: Int = ReadingImageState.DEFAULT_CORNER_RADIUS,
    ) =
        ReadingContentHtml.build(
            contentHtml = content,
            style = style,
            bg = colors["bg"]!!,
            fg = colors["fg"]!!,
            muted = colors["muted"]!!,
            codeBg = colors["codeBg"]!!,
            border = colors["border"]!!,
            link = colors["link"]!!,
            imageUrls = imageUrls,
            imageCorners = imageCorners,
        )

    @Test
    fun `default style produces expected css values`() {
        val html = build("<p>hi</p>")

        assertTrue(html.contains("font-size:17px"))
        assertTrue(html.contains("line-height:1.0"))
        assertTrue(html.contains("padding:0 24px"))
        assertTrue(html.contains("background:#0E0F13"))
        assertTrue(html.contains("color:#E8E8EC"))
        assertTrue(html.contains("color:#9B9CFF"))
    }

    @Test
    fun `style params are reflected in css`() {
        val html = build(
            "<p>hi</p>",
            ReadingStyleState(fontSize = 22, lineHeight = 1.6f, horizontalPadding = 8),
        )

        assertTrue(html.contains("font-size:22px"))
        assertTrue(html.contains("line-height:1.6"))
        assertTrue(html.contains("padding:0 8px"))
    }

    @Test
    fun `font family maps to its css stack`() {
        assertTrue(build("<p>a</p>", ReadingStyleState(fontFamily = ReadingFontFamily.SYSTEM)).contains(ReadingFontFamily.SYSTEM.cssStack))
        assertTrue(build("<p>a</p>", ReadingStyleState(fontFamily = ReadingFontFamily.SERIF)).contains("Georgia"))
        assertTrue(build("<p>a</p>", ReadingStyleState(fontFamily = ReadingFontFamily.MONOSPACE)).contains("Menlo"))
    }

    @Test
    fun `content html is preserved verbatim`() {
        val content = """<p>Hi &amp; bye</p><img src="https://example.com/a.png" alt="">"""
        val html = build(content)

        assertTrue(html.contains("<body>$content</body>"))
    }

    @Test
    fun `document shell is a complete html document`() {
        val html = build("<p>x</p>")

        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("<meta name=\"viewport\""))
        assertTrue(html.trim().endsWith("</html>"))
    }

    @Test
    fun `typography css covers tables headings lists and code`() {
        val html = build("<p>x</p>")

        assertTrue(html.contains("table { display:block"))
        assertTrue(html.contains("th,td { border:1px solid"))
        assertTrue(html.contains("h1 { font-size:1.45em"))
        assertTrue(html.contains("ul,ol { margin:0 0 1em 0"))
        assertTrue(html.contains("hr { border:none"))
        assertTrue(html.contains("figcaption { text-align:center"))
        assertTrue(html.contains(":not(pre) > code"))
        assertTrue(html.contains("pre code { background:none"))
    }

    @Test
    fun `image corner radius lands in the img rule`() {
        assertTrue(build("<p>x</p>").contains("border-radius:8px"))
        assertTrue(build("<p>x</p>", imageCorners = 0).contains("border-radius:0px"))
        assertTrue(build("<p>x</p>", imageCorners = 24).contains("border-radius:24px"))
    }

    @Test
    fun `image urls turn images into img-link anchors`() {
        val content = """<p>x</p><img src="https://a.com/1.png">"""
        val html = build(content, imageUrls = setOf("https://a.com/1.png"))

        assertTrue(html.contains("""<a class="img-link" href="https://a.com/1.png">"""))
        assertTrue(html.contains("a.img-link { text-decoration:none"))
    }

    @Test
    fun `no image urls leaves the body verbatim`() {
        val content = """<p>x</p><img src="https://a.com/1.png">"""

        assertTrue(build(content).contains("<body>$content</body>"))
    }

    @Test
    fun `media card and underline styles present`() {
        val html = build("<p>x</p>")

        assertTrue(html.contains(".media-card { display:flex"))
        assertTrue(html.contains(".media-card span { color:#9B9CFF"))
        assertTrue(html.contains("text-decoration:underline"))
    }
}
