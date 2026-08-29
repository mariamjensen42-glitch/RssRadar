package com.cycling.rssradar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            assertEquals("Hello world", contentText)
            assertEquals(1787565600000L, publishedAt)
        }
        assertNull(feed.articles[1].publishedAt)
        assertNull(feed.articles[1].summary)
        // 没有全文字段的条目：正文为空，contentSource 语义上是无正文
        assertNull(feed.articles[1].contentHtml)
        assertNull(feed.articles[1].contentText)
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
    fun `summary is truncated to 300 chars while full text stays intact`() {
        // 样本调查：全文中位 3359 字，88.2% ≥ 500 字。旧实现截 500 字直接毁掉正文。
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

        val article = parser.parse(ByteArrayInputStream(xml.toByteArray())).articles[0]

        assertEquals(300, article.summary!!.length)
        // 全文不再被截断
        assertEquals(800, article.contentText!!.length)
    }

    @Test
    fun `content_encoded wins over shorter description`() {
        val xml = """
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/"><channel><title>T</title>
                <item>
                    <title>Post</title>
                    <link>https://example.com/1</link>
                    <description>short summary line</description>
                    <content:encoded><![CDATA[<p>full article body here with much more text than the short description could ever hold</p>]]></content:encoded>
                </item>
            </channel></rss>
        """.trimIndent()

        val article = parser.parse(ByteArrayInputStream(xml.toByteArray())).articles[0]

        // 正文来自 content:encoded
        assertTrue(article.contentText!!.startsWith("full article body"))
        // 摘要来自较短的 description
        assertEquals("short summary line", article.summary)
    }

    @Test
    fun `description carrying full article is used as content (RSSHub RSS behavior)`() {
        // 样本结论：RSSHub 的 RSS 渲染器不输出 content:encoded，全文一律在 description
        val longBody = "<p>" + "正文段落。".repeat(80) + "</p>"
        val xml = """
            <rss version="2.0"><channel><title>T</title>
                <item>
                    <title>Post</title>
                    <link>https://example.com/1</link>
                    <description><![CDATA[$longBody]]></description>
                </item>
            </channel></rss>
        """.trimIndent()

        val article = parser.parse(ByteArrayInputStream(xml.toByteArray())).articles[0]

        assertTrue(article.contentText!!.startsWith("正文段落。"))
        assertTrue(article.summary!!.length <= RssParser.SUMMARY_MAX_LENGTH)
    }

    @Test
    fun `content encoded without prefix but with content namespace is recognized (meituan variant)`() {
        // 样本结论：美团技术团队用无 content: 前缀的 <encoded xmlns> 变体，字符串匹配会漏，
        // rome 的 ContentModule 按 namespace URI 识别
        val xml = """
            <rss version="2.0"><channel><title>T</title>
                <item>
                    <title>Post</title>
                    <link>https://example.com/1</link>
                    <description>short</description>
                    <encoded xmlns="http://purl.org/rss/1.0/modules/content/"><![CDATA[<p>namespace variant full body text that is clearly longer than the short description</p>]]></encoded>
                </item>
            </channel></rss>
        """.trimIndent()

        val article = parser.parse(ByteArrayInputStream(xml.toByteArray())).articles[0]

        assertTrue(article.contentText!!.contains("namespace variant full body"))
    }

    @Test
    fun `atom empty self-closing content is not mistaken for full text`() {
        // 样本结论：RSSHub Atom 渲染器总输出 <content>，正文为空时退化成 <content src=.../>
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom"><title>T</title>
                <entry>
                    <title>Entry</title>
                    <link href="https://example.org/a"/>
                    <content src="https://example.org/a"/>
                    <summary>only a summary</summary>
                </entry>
            </feed>
        """.trimIndent()

        val article = parser.parse(ByteArrayInputStream(xml.toByteArray())).articles[0]

        assertEquals("only a summary", article.summary)
        assertNull(article.contentHtml)
    }

    @Test
    fun `strips script style and inline handlers from content`() {
        // 样本结论：酷壳 15/15 条目含 <script>，46% 含内联 style
        val xml = """
            <rss version="2.0"><channel><title>T</title>
                <item>
                    <title>Post</title>
                    <link>https://example.com/1</link>
                    <description><![CDATA[<p>safe paragraph</p><script>alert('xss')</script><style>.x{color:red}</style><p onclick="evil()">second</p>]]></description>
                </item>
            </channel></rss>
        """.trimIndent()

        val article = parser.parse(ByteArrayInputStream(xml.toByteArray())).articles[0]
        val html = article.contentHtml!!

        assertFalse(html.contains("script"))
        assertFalse(html.contains("alert"))
        assertFalse(html.contains("style"))
        assertFalse(html.contains("color:red"))
        assertFalse(html.contains("onclick"))
        assertTrue(html.contains("safe paragraph"))
    }

    @Test
    fun `future publication date is dropped`() {
        val farFuture = System.currentTimeMillis() + 90L * 24 * 60 * 60 * 1000
        val xml = """
            <rss version="2.0"><channel><title>T</title>
                <item>
                    <title>Future post</title>
                    <link>https://example.com/1</link>
                    <pubDate>${java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("GMT") }.format(java.util.Date(farFuture))}</pubDate>
                </item>
            </channel></rss>
        """.trimIndent()

        val article = parser.parse(ByteArrayInputStream(xml.toByteArray())).articles[0]

        assertNull(article.publishedAt)
    }

    @Test
    fun `extracts cover from enclosure and falls back to first img in content`() {
        val xml = """
            <rss version="2.0"><channel><title>T</title>
                <item>
                    <title>With enclosure</title>
                    <link>https://example.com/1</link>
                    <description><![CDATA[<p>body</p><img src="https://example.com/in-content.jpg"/>]]></description>
                    <enclosure url="https://example.com/cover.jpg" type="image/jpeg" length="100"/>
                </item>
                <item>
                    <title>Without enclosure</title>
                    <link>https://example.com/2</link>
                    <description><![CDATA[<p>body</p><img src="/relative.jpg"/><img src="https://example.com/second.jpg"/>]]></description>
                </item>
            </channel></rss>
        """.trimIndent()

        val articles = parser.parse(ByteArrayInputStream(xml.toByteArray())).articles

        assertEquals("https://example.com/cover.jpg", articles[0].coverUrl)
        // 相对路径必须转绝对；取第一个
        assertEquals("https://example.com/relative.jpg", articles[1].coverUrl)
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
