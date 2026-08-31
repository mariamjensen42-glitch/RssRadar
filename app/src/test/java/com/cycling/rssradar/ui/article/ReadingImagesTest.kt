package com.cycling.rssradar.ui.article

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 正文图片提取与"点图放大"链接包装（issue #60，ADR-0011）。纯 JVM，含 jsoup。 */
class ReadingImagesTest {

    private val html = """
        <p>intro</p>
        <img src="https://a.com/1.png" alt="one">
        <figure><img src="https://a.com/2.png"></figure>
        <img src="https://a.com/1.png">
        <img src="/relative.png">
        <img>
        <a href="https://a.com/page"><img src="https://a.com/3.png"></a>
    """.trimIndent()

    @Test
    fun `extract keeps document order and drops duplicates`() {
        val urls = ReadingImages.extract(html)

        assertEquals(listOf("https://a.com/1.png", "https://a.com/2.png", "https://a.com/3.png"), urls)
    }

    @Test
    fun `extract drops relative and empty sources`() {
        assertFalse(ReadingImages.extract(html).any { it.startsWith("/") })
        assertEquals(emptyList<String>(), ReadingImages.extract("<p>no image</p>"))
    }

    @Test
    fun `wrap turns plain images into self links`() {
        val urls = ReadingImages.extract(html).toSet()
        val out = ReadingImages.wrapForMaximize(html, urls)

        assertTrue(out.contains("""<a class="img-link" href="https://a.com/1.png">"""))
        assertTrue(out.contains("""<a class="img-link" href="https://a.com/2.png">"""))
    }

    @Test
    fun `wrap leaves images that are already links alone`() {
        val out = ReadingImages.wrapForMaximize(html, ReadingImages.extract(html).toSet())

        // 3.png 本来就在 <a href=page> 里，不能被抢成指向自身的链接
        assertTrue(out.contains("""<a href="https://a.com/page">"""))
        assertFalse(out.contains("""<a class="img-link" href="https://a.com/3.png">"""))
    }

    @Test
    fun `wrap skips urls outside the given set`() {
        val out = ReadingImages.wrapForMaximize(html, setOf("https://a.com/2.png"))

        assertFalse(out.contains("img-link\" href=\"https://a.com/1.png"))
        assertTrue(out.contains("img-link\" href=\"https://a.com/2.png"))
    }

    @Test
    fun `empty set means maximize off and returns the html untouched`() {
        val src = """<p>x</p><img src="https://a.com/1.png">"""

        assertEquals(src, ReadingImages.wrapForMaximize(src, emptySet()))
    }
}
