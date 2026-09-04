package com.cycling.rssradar.core.data.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [FeedDiscovery] 的纯函数单测：候选提取与兜底路径生成（发现链路的测试缝）。 */
class FeedDiscoveryTest {

    @Test
    fun `candidateLinks - picks rss and atom alternates in document order`() {
        val html = """
            <html><head>
              <link rel="alternate" type="application/rss+xml" title="RSS" href="/feed.xml">
              <link rel="alternate" type="application/atom+xml" href="https://b.com/atom.xml">
              <link rel="stylesheet" href="/style.css">
              <link rel="alternate" type="text/html" hreflang="en" href="/en/">
            </head><body></body></html>
        """.trimIndent()
        val links = FeedDiscovery.candidateLinks("https://a.com/post/1", html)
        assertEquals(
            listOf("https://a.com/feed.xml", "https://b.com/atom.xml"),
            links,
        )
    }

    @Test
    fun `candidateLinks - resolves relative hrefs against base url`() {
        val html = """<link rel="alternate" type="application/rss+xml" href="feed/">"""
        assertEquals(
            listOf("https://a.com/blog/feed/"),
            FeedDiscovery.candidateLinks("https://a.com/blog/", html),
        )
    }

    @Test
    fun `candidateLinks - ignores non feed mime types`() {
        // application/xml 太宽泛（sitemap 也用它），不认
        val html = """<link rel="alternate" type="application/xml" href="/sitemap.xml">"""
        assertTrue(FeedDiscovery.candidateLinks("https://a.com", html).isEmpty())
    }

    @Test
    fun `candidateLinks - dedupes and caps the result`() {
        val repeated = (1..20).joinToString("") { i ->
            """<link rel="alternate" type="application/rss+xml" href="/f$i.xml">"""
        }
        val links = FeedDiscovery.candidateLinks("https://a.com", repeated)
        assertEquals(FeedDiscovery.MAX_CANDIDATES, links.size)
        assertEquals(links.toSet().size, links.size)
    }

    @Test
    fun `candidateLinks - blank or broken html yields nothing`() {
        assertTrue(FeedDiscovery.candidateLinks("https://a.com", "").isEmpty())
        assertTrue(FeedDiscovery.candidateLinks("https://a.com", "   ").isEmpty())
    }

    @Test
    fun `guessedLinks - appends common feed paths to the site root`() {
        val guessed = FeedDiscovery.guessedLinks("https://a.com/")
        assertTrue(guessed.contains("https://a.com/feed"))
        assertTrue(guessed.contains("https://a.com/rss.xml"))
        assertTrue(guessed.all { it.startsWith("https://a.com/") })
    }

    @Test
    fun `guessedLinks - non http input yields nothing`() {
        assertTrue(FeedDiscovery.guessedLinks("not-a-url").isEmpty())
    }
}
