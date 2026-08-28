package com.cycling.rssradar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class RssParserTest {

    private val parser = RssParser()

    @Test
    fun `parses RSS 2_0 feed`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel>
                <title>Sample Tech Blog</title>
                <item>
                    <title>First post</title>
                    <link>https://example.com/1</link>
                    <description>Hello &lt;b&gt;world&lt;/b&gt;</description>
                    <pubDate>Mon, 24 Aug 2026 10:00:00 GMT</pubDate>
                </item>
                <item>
                    <title>Second post</title>
                    <link>https://example.com/2</link>
                </item>
            </channel></rss>
        """.trimIndent()

        val feed = parser.parse(ByteArrayInputStream(xml.toByteArray()))

        assertEquals("Sample Tech Blog", feed.title)
        assertEquals(2, feed.articles.size)
        with(feed.articles[0]) {
            assertEquals("First post", title)
            assertEquals("https://example.com/1", link)
            assertEquals("Hello world", summary)
            assertEquals(1787565600000L, publishedAt)
        }
        assertNull(feed.articles[1].publishedAt)
        assertNull(feed.articles[1].summary)
    }

    @Test
    fun `parses Atom feed`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <title>Atom Example</title>
                <entry>
                    <title>Entry one</title>
                    <link href="https://example.org/a"/>
                    <summary>Plain text summary</summary>
                    <updated>2026-08-25T08:30:00Z</updated>
                </entry>
            </feed>
        """.trimIndent()

        val feed = parser.parse(ByteArrayInputStream(xml.toByteArray()))

        assertEquals("Atom Example", feed.title)
        assertEquals(1, feed.articles.size)
        with(feed.articles[0]) {
            assertEquals("Entry one", title)
            assertEquals("https://example.org/a", link)
            assertEquals("Plain text summary", summary)
            assertTrue(publishedAt != null)
        }
    }

    @Test
    fun `throws on invalid content`() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(ByteArrayInputStream("<html><body>not a feed</body></html>".toByteArray()))
        }
    }

    @Test
    fun `uses feed url as fallback title when entry title missing`() {
        val xml = """
            <rss version="2.0"><channel><title>T</title>
                <item><link>https://example.com/x</link></item>
            </channel></rss>
        """.trimIndent()

        val feed = parser.parse(ByteArrayInputStream(xml.toByteArray()))

        assertEquals("https://example.com/x", feed.articles[0].title)
    }

    @Test
    fun `truncates long summary to 500 chars`() {
        val longText = "a".repeat(800)
        val xml = """
            <rss version="2.0"><channel><title>T</title>
                <item>
                    <title>Post</title>
                    <link>https://example.com/1</link>
                    <description><![CDATA[$longText]]></description>
                </item>
            </channel></rss>
        """.trimIndent()

        val feed = parser.parse(ByteArrayInputStream(xml.toByteArray()))

        assertEquals(500, feed.articles[0].summary!!.length)
    }

    @Test
    fun `collapses whitespace and strips nested tags`() {
        val xml = """
            <rss version="2.0"><channel><title>T</title>
                <item>
                    <title>Post</title>
                    <link>https://example.com/1</link>
                    <description><![CDATA[<p>Line   one</p>
            <p>Line two</p>]]></description>
                </item>
            </channel></rss>
        """.trimIndent()

        val feed = parser.parse(ByteArrayInputStream(xml.toByteArray()))

        assertEquals("Line one Line two", feed.articles[0].summary)
    }

    @Test
    fun `falls back to Untitled feed when channel title missing`() {
        val xml = """
            <rss version="2.0"><channel>
                <item><title>Post</title><link>https://example.com/1</link></item>
            </channel></rss>
        """.trimIndent()

        val feed = parser.parse(ByteArrayInputStream(xml.toByteArray()))

        assertEquals("Untitled feed", feed.title)
    }

    @Test
    fun `returns empty articles for feed without items`() {
        val xml = """
            <rss version="2.0"><channel><title>Empty channel</title></channel></rss>
        """.trimIndent()

        val feed = parser.parse(ByteArrayInputStream(xml.toByteArray()))

        assertEquals("Empty channel", feed.title)
        assertTrue(feed.articles.isEmpty())
    }

    @Test
    fun `skips entry that has neither link nor title`() {
        val xml = """
            <rss version="2.0"><channel><title>T</title>
                <item><description>orphan</description></item>
                <item><title>Real</title><link>https://example.com/1</link></item>
            </channel></rss>
        """.trimIndent()

        val feed = parser.parse(ByteArrayInputStream(xml.toByteArray()))

        assertEquals(1, feed.articles.size)
        assertEquals("Real", feed.articles[0].title)
    }
}
