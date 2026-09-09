package com.cycling.rssradar.ui.intent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 外部文本 → 链接的 JVM 证明（#34）。 */
class SharedTextTest {

    @Test
    fun `picks the first link out of a shared paragraph`() {
        assertEquals(
            "https://example.com/feed.xml",
            SharedText.extractUrl("快看这个 https://example.com/feed.xml 更新很勤"),
        )
    }

    @Test
    fun `trims trailing punctuation but keeps the path`() {
        assertEquals("https://a.com/rss/atom", SharedText.extractUrl("https://a.com/rss/atom"))
        assertEquals("https://a.com/rss", SharedText.extractUrl("https://a.com/rss。"))
        assertEquals("https://a.com/rss", SharedText.extractUrl("（https://a.com/rss）"))
    }

    @Test
    fun `stops at whitespace and angle brackets`() {
        assertEquals(
            "https://a.com/rss",
            SharedText.extractUrl("https://a.com/rss 后面还有一句"),
        )
        assertEquals(
            "https://a.com/rss",
            SharedText.extractUrl("<a href=\"https://a.com/rss\">点我</a>"),
        )
    }

    @Test
    fun `returns null when there is no link at all`() {
        assertNull(SharedText.extractUrl("这段话里一个链接都没有"))
        assertNull(SharedText.extractUrl("example.com/feed.xml"))
        assertNull(SharedText.extractUrl(null))
        assertNull(SharedText.extractUrl("   "))
        assertNull(SharedText.extractUrl("https://"))
    }
}
