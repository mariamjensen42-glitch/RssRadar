package com.cycling.rssradar.core.data.parser

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
        // 摘要留在 content 列（列表检索与详情页兜底要用），但**不够格当正文**——
        // contentSource 因此记 NONE，详情页才会去抓原文。这是"正文只有摘要"的根因修复
        // （ADR-0012 / RssParser.FULL_TEXT_MIN_CHARS）。
        assertFalse(RssParser.isFullText(article.contentHtml, article.contentText))
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

    @Test
    fun `extracts site url from RSS channel link`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel>
                <title>Sample</title>
                <link>https://example.com/</link>
                <item><title>One</title><link>https://example.com/1</link></item>
            </channel></rss>
        """.trimIndent()

        val feed = parser.parse(ByteArrayInputStream(xml.toByteArray()))

        assertEquals("https://example.com/", feed.siteUrl)
    }

    @Test
    fun `extracts site url from Atom feed alternate link`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <title>Atom Example</title>
                <link href="https://example.org/atom.xml" rel="self" type="application/atom+xml"/>
                <link href="https://example.org/" rel="alternate" type="text/html"/>
                <entry><title>E</title><link href="https://example.org/a"/></entry>
            </feed>
        """.trimIndent()

        val feed = parser.parse(ByteArrayInputStream(xml.toByteArray()))

        assertEquals("https://example.org/", feed.siteUrl)
    }

    @Test
    fun `tolerates missing site url`() {
        val xml = """
            <rss version="2.0"><channel><title>No Link</title>
                <item><title>One</title><link>https://example.com/1</link></item>
            </channel></rss>
        """.trimIndent()

        val feed = parser.parse(ByteArrayInputStream(xml.toByteArray()))

        assertEquals("", feed.siteUrl)
    }

    @Test
    fun `sanitize replaces iframe with media card`() {
        val html = """<p>前文</p><iframe src="https://www.youtube.com/embed/abc123"></iframe><p>后文</p>"""

        val out = RssParser.sanitizeHtml(html)

        assertTrue(out.contains("media-card"))
        assertTrue(out.contains("href=\"https://www.youtube.com/embed/abc123\""))
        assertTrue(out.contains("嵌入内容 · www.youtube.com"))
        assertFalse(out.contains("<iframe"))
    }

    @Test
    fun `sanitize replaces video with src with media card`() {
        val html = """<video src="https://cdn.example.com/clip.mp4"></video>"""

        val out = RssParser.sanitizeHtml(html)

        assertTrue(out.contains("media-card"))
        assertTrue(out.contains("视频 · cdn.example.com"))
        assertFalse(out.contains("<video"))
    }

    @Test
    fun `sanitize uses nested source element of video`() {
        val html = """<video><source src="https://cdn.example.com/clip.mp4" type="video/mp4"></video>"""

        val out = RssParser.sanitizeHtml(html)

        assertTrue(out.contains("media-card"))
        assertFalse(out.contains("<video"))
    }

    @Test
    fun `sanitize drops iframe without http src entirely`() {
        val html = """<iframe src="/local/embed"></iframe><p>ok</p>"""

        val out = RssParser.sanitizeHtml(html)

        assertFalse(out.contains("<iframe"))
        assertFalse(out.contains("media-card"))
        assertTrue(out.contains("<p>ok</p>"))
    }

    @Test
    fun `sanitize normalizes protocol relative iframe src`() {
        val html = """<iframe src="//player.bilibili.com/player.html?bvid=BV1"></iframe>"""

        val out = RssParser.sanitizeHtml(html)

        assertTrue(out.contains("href=\"https://player.bilibili.com/player.html?bvid=BV1\""))
    }

    @Test
    fun `sanitize strips foreign class but keeps media card class`() {
        val html = """<a class="evil" href="https://example.com">x</a>"""

        val out = RssParser.sanitizeHtml(html)

        assertFalse(out.contains("evil"))
    }

    // ———————————————————————————————————————————————
    // style 声明级白名单（行内样式放行，原生渲染器 + WebView 共用）
    // ———————————————————————————————————————————————

    @Test
    fun `sanitize keeps whitelisted style declarations`() {
        val html = """<span style="color:#ff0000; font-weight:bold; text-align:center">x</span>"""

        val out = RssParser.sanitizeHtml(html)

        assertTrue(out.contains("color:#ff0000"))
        assertTrue(out.contains("font-weight:bold"))
        assertTrue(out.contains("text-align:center"))
    }

    @Test
    fun `sanitize strips unsafe style declarations`() {
        val html = """<span style="position:fixed; background:url(https://evil.example.com/x.png); color:red">x</span>"""

        val out = RssParser.sanitizeHtml(html)

        // url()/position 剥掉，安全 color 保留
        assertFalse(out.contains("position"))
        assertFalse(out.contains("url"))
        assertTrue(out.contains("color:red"))
    }

    @Test
    fun `sanitize drops style attribute entirely when nothing survives`() {
        val html = """<span style="position:fixed">x</span>"""

        val out = RssParser.sanitizeHtml(html)

        assertFalse(out.contains("style"))
    }

    // ———————————————————————————————————————————————
    // 全文门槛（ADR-0012）：摘要级 feed 内容不能挡住按需抓原文
    // ———————————————————————————————————————————————

    @Test
    fun `summary length content is not treated as full text`() {
        // 只给摘要的 feed（RSSHub 大量路由如此）：描述不到 300 字
        val summary = "这是一篇文章摘要，".repeat(20) // 约 180 字

        assertFalse(RssParser.isFullText("<p>$summary</p>", summary))
    }

    @Test
    fun `long content is treated as full text`() {
        val body = "这是正文内容，".repeat(200) // 约 1400 字

        assertTrue(RssParser.isFullText("<p>$body</p>", body))
    }

    @Test
    fun `null or blank content is never full text`() {
        assertFalse(RssParser.isFullText(null, null))
        assertFalse(RssParser.isFullText("", ""))
        assertFalse(RssParser.isFullText("   ", "  "))
    }

    @Test
    fun `summary only feed still stores the summary but is not marked as sourced`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel>
                <title>Summary Only</title>
                <item>
                    <title>只有摘要的文章</title>
                    <link>https://example.com/only-summary</link>
                    <description>短摘要，长度远低于正文阈值。</description>
                </item>
            </channel></rss>
        """.trimIndent()

        val article = parser.parse(ByteArrayInputStream(xml.toByteArray())).articles.single()

        // 摘要仍要留下（列表与检索用），但这段内容不够格当正文
        assertFalse(RssParser.isFullText(article.contentHtml, article.contentText))
        assertTrue(article.summary.orEmpty().contains("短摘要"))
    }

    @Test
    fun `strip markdown residue from list preview`() {
        val raw = "## AI资讯日报 2026/9/5\n> `AI资讯` 「每日早读」 [全文链接](https://x.com/a) ![封面](https://x.com/i.png)\n**加粗** *斜体* 正文"
        val cleaned = RssParser.stripMarkdown(raw)
        // 语法符号一个不留；锚文本与正文保留
        assertFalse(cleaned.contains("##"))
        assertFalse(cleaned.contains("`"))
        assertFalse(cleaned.contains("](http"))
        assertFalse(cleaned.contains("**"))
        assertFalse(cleaned.contains("!["))
        assertTrue(cleaned.contains("AI资讯日报 2026/9/5"))
        assertTrue(cleaned.contains("全文链接"))
        assertTrue(cleaned.contains("加粗 斜体 正文"))
        // 连续空白收敛为单空格
        assertFalse(Regex("\\s{2,}").containsMatchIn(cleaned))
    }

    @Test
    fun `markdown stripped in parsed summary`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel>
                <title>MD Feed</title>
                <item>
                    <title>markdown 源</title>
                    <link>https://example.com/md</link>
                    <description>## 标题行\n**正文** 内容</description>
                </item>
            </channel></rss>
        """.trimIndent()

        val article = parser.parse(ByteArrayInputStream(xml.toByteArray())).articles.single()
        val summary = article.summary.orEmpty()
        assertFalse("摘要不得残留 markdown 标记: $summary", summary.contains("##") || summary.contains("**"))
        assertTrue(summary.contains("标题行"))
    }
}
