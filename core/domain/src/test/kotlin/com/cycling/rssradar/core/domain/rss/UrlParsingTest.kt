package com.cycling.rssradar.core.domain.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.MalformedURLException

/** URL 解析缝单测：JDK 20 起 URL(String) 废弃、改走 URI 后，宽容度与异常契约都必须与旧实现一致。 */
class UrlParsingTest {

    @Test
    fun `normalize 补 https 前缀并 trim 两端空白`() {
        assertEquals("https://example.com/feed?a=1", normalizeHttpUrl("  example.com/feed?a=1  "))
        assertEquals("https://example.com/feed", normalizeHttpUrl("example.com/feed"))
        assertEquals("http://example.com/feed", normalizeHttpUrl("http://example.com/feed"))
    }

    @Test
    fun `normalize 保留中文域名与中文路径原样`() {
        assertEquals("https://例.com/标签/rss", normalizeHttpUrl("https://例.com/标签/rss"))
        assertEquals("https://example.com/x?q=中文", normalizeHttpUrl("https://example.com/x?q=中文"))
    }

    @Test
    fun `normalize 空串与纯空白返回 null`() {
        assertNull(normalizeHttpUrl(""))
        assertNull(normalizeHttpUrl("   "))
    }

    @Test
    fun `normalize 把空格与方括号编码而不是原样透传`() {
        assertEquals("https://example.com/feed%20xml", normalizeHttpUrl("https://example.com/feed xml"))
        assertEquals("https://example.com/a%5Bb%5Dc", normalizeHttpUrl("https://example.com/a[b]c"))
    }

    @Test
    fun `normalize 非法百分号转义与伪协议判无效`() {
        assertNull(normalizeHttpUrl("https://example.com/50%off"))
        assertNull(normalizeHttpUrl("javascript:alert(1)"))
    }

    @Test
    fun `parseUrl 畸形地址抛 MalformedURLException 而非 RuntimeException`() {
        assertThrows(MalformedURLException::class.java) { parseUrl("https://example.com/50%off") }
    }

    @Test
    fun `parseUri trim 首尾空白 Location 头常见形态`() {
        assertEquals("/z", parseUri(" /z ").toString())
        assertEquals("https://b.com/y", parseUri(" https://b.com/y ").toString())
    }

    @Test
    fun `重定向解析与旧 URL 构造器逐字一致`() {
        assertEquals("https://a.com/z", redirect("https://a.com/x/y", "/z"))
        assertEquals("https://a.com/z", redirect("https://a.com/x/y", " /z "))
        assertEquals("https://a.com/z", redirect("https://a.com/x/y", "../z"))
        assertEquals("https://b.com/y", redirect("http://a.com/x", "https://b.com/y"))
        assertEquals("https://b.com/y", redirect("https://a.com", "//b.com/y"))
        assertEquals("https://a.com/y", redirect("https://a.com/x", "y"))
    }

    private fun redirect(base: String, location: String): String =
        parseUri(base).resolve(parseUri(location)).toURL().toString()
}
