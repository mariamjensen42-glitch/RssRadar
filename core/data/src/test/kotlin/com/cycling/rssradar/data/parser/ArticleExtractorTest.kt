package com.cycling.rssradar.core.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ArticleExtractor] 的提取契约（纯 JVM）。
 *
 * 这里锁的是「正文不完整」的几类真实成因：噪声没剔干净、容器误判、过短、
 * JS 动态渲染、付费墙，以及元数据与图片的处理。
 */
class ArticleExtractorTest {

    private val config = ExtractConfig(minContentChars = 200)

    private fun longText(repeat: Int = 12): String =
        ("这是一段用于测试的中文正文，包含足够多的字符以通过完整性阈值。" +
            "真实文章通常会有多个段落，这里用重复文本来模拟足够的长度。").repeat(repeat)

    // ———————————————————————————————————————————————
    // 正常文章
    // ———————————————————————————————————————————————

    @Test
    fun `extracts body and drops navigation ads comments and recommendations`() {
        val html = """
            <html><head><title>标题</title></head><body>
              <nav><a href="/home">首页</a><a href="/news">新闻</a></nav>
              <div class="advertisement">广告位招租</div>
              <article>
                <h1>真实标题</h1>
                <p>${longText()}</p>
                <p>第二段正文内容，同样足够长以通过阈值检测。</p>
              </article>
              <aside class="sidebar">侧栏推荐</aside>
              <div class="related">相关推荐文章一 相关推荐文章二</div>
              <div class="comments" id="comments">评论区：沙发、板凳、地板</div>
              <footer>版权所有 违法必究</footer>
            </body></html>
        """.trimIndent()

        val article = ArticleExtractor.extract("https://example.com/a", html, config)

        assertNotNull(article)
        val body = article!!.contentText
        assertTrue("正文应保留", body.contains("第二段正文内容"))
        assertFalse("导航不该进正文", body.contains("首页"))
        assertFalse("广告不该进正文", body.contains("广告位招租"))
        assertFalse("推荐位不该进正文", body.contains("相关推荐文章一"))
        assertFalse("评论区不该进正文", body.contains("沙发"))
        assertFalse("页脚不该进正文", body.contains("版权所有"))
        assertTrue(article.quality.isComplete)
        assertEquals(ExtractionIssue.NONE, article.quality.issue)
    }

    @Test
    fun `extracts title author and publish time`() {
        val html = """
            <html><head>
              <meta property="og:title" content="Open Graph 标题">
              <meta name="author" content="张三">
              <meta property="article:published_time" content="2026-08-30T10:15:00Z">
            </head><body><article><p>${longText()}</p></article></body></html>
        """.trimIndent()

        val article = ArticleExtractor.extract("https://example.com/a", html, config)!!

        assertEquals("Open Graph 标题", article.title)
        assertEquals("张三", article.author)
        assertNotNull(article.publishedAt)
    }

    @Test
    fun `falls back to json ld author and h1 title`() {
        val html = """
            <html><head>
              <script type="application/ld+json">
                {"@type":"NewsArticle","headline":"JSON-LD 标题",
                 "author":{"@type":"Person","name":"李四"},
                 "datePublished":"2026-08-01T08:00:00+08:00"}
              </script>
            </head><body><article><h1>页面 H1</h1><p>${longText()}</p></article></body></html>
        """.trimIndent()

        val article = ArticleExtractor.extract("https://example.com/a", html, config)!!

        assertEquals("JSON-LD 标题", article.title)
        assertEquals("李四", article.author)
        assertNotNull(article.publishedAt)
    }

    @Test
    fun `images are absolutized and placeholders removed`() {
        val html = """
            <html><body><article>
              <p>${longText()}</p>
              <img src="/img/real.png" alt="真实配图">
              <img src="//cdn.example.com/other.png" alt="协议相对">
              <img src="https://example.com/tracker/1x1.gif" width="1" height="1">
              <img src="https://example.com/assets/spacer.gif">
            </article></body></html>
        """.trimIndent()

        val article = ArticleExtractor.extract("https://example.com/news/a", html, config)!!
        val contentHtml = article.contentHtml

        assertTrue("相对路径要转绝对", contentHtml.contains("https://example.com/img/real.png"))
        assertTrue("协议相对要补 https", contentHtml.contains("https://cdn.example.com/other.png"))
        assertFalse("1x1 追踪像素要剔除", contentHtml.contains("1x1.gif"))
        assertFalse("占位图要剔除", contentHtml.contains("spacer.gif"))
    }

    // ———————————————————————————————————————————————
    // 不完整：过短 / 动态渲染 / 付费墙 / 容器误判
    // ———————————————————————————————————————————————

    @Test
    fun `short body is marked too short`() {
        val html = "<html><body><article><h1>标题</h1><p>只有一句话。</p></article></body></html>"

        val article = ArticleExtractor.extract("https://example.com/a", html, config)!!

        assertFalse(article.quality.isComplete)
        assertEquals(ExtractionIssue.TOO_SHORT, article.quality.issue)
    }

    @Test
    fun `js shell page is marked dynamic render`() {
        // 动态渲染页的真实形态：正文由 JS 填充，静态 HTML 里只有脚本与一句占位文案
        val scripts = (1..12).joinToString("\n") { "<script src=\"/chunk-$it.js\"></script>" }
        val html = """
            <html><body>
              $scripts
              <div id="app"></div>
              <article><p>内容加载中，请稍候。</p></article>
              <noscript>Please enable JavaScript to view this page.</noscript>
            </body></html>
        """.trimIndent()

        val article = ArticleExtractor.extract("https://example.com/a", html, config)!!

        assertFalse(article.quality.isComplete)
        assertEquals(ExtractionIssue.DYNAMIC_RENDER, article.quality.issue)
    }

    @Test
    fun `page with no text at all yields nothing to write`() {
        // 一个字都抓不到时返回 null（由调用方记为 EXTRACT_FAILED）：
        // 写一条空正文进库只会让用户看到空白页，不如如实降级到摘要。
        val html = "<html><head></head><body></body></html>"

        assertEquals(null, ArticleExtractor.extract("https://example.com/a", html, config))
    }

    @Test
    fun `paywall page is marked paywall`() {
        val html = """
            <html><body><article>
              <h1>付费文章</h1>
              <p>本文剩余内容需要订阅后查看。</p>
              <div class="paywall">开通会员，阅读全文</div>
            </article></body></html>
        """.trimIndent()

        val article = ArticleExtractor.extract("https://example.com/a", html, config)!!

        assertFalse(article.quality.isComplete)
        assertEquals(ExtractionIssue.PAYWALL, article.quality.issue)
    }

    @Test
    fun `link list container is not mistaken for body`() {
        // 一个「全是链接、几乎没有正文」的块：链接密度判定要把它挡掉
        val links = (1..30).joinToString("") { "<li><a href=\"/t/$it\">标题 $it</a></li>" }
        val html = """
            <html><body>
              <div class="hot-news"><ul>$links</ul></div>
            </body></html>
        """.trimIndent()

        val article = ArticleExtractor.extract("https://example.com/a", html, config)

        // 要么提不出来，要么提出来也必须判定为不完整——绝不能算「完整正文」
        if (article != null) {
            assertFalse("链接列表不该被当成完整正文", article.quality.isComplete)
        }
    }

    // ———————————————————————————————————————————————
    // 兜底路径
    // ———————————————————————————————————————————————

    @Test
    fun `jsoup fallback wins when readability only gets a fragment`() {
        // 中文站点常见形态：正文放在多层 div 里，readability 常常只捞到一小段
        val body = (1..10).joinToString("\n") { "<div><p>第 $it 段：${longText(3)}</p></div>" }
        val html = """
            <html><body>
              <div class="wrap"><div class="inner"><div id="js_content">$body</div></div></div>
            </body></html>
        """.trimIndent()

        val article = ArticleExtractor.extract("https://mp.example.com/a", html, config)!!

        assertTrue("应拿到完整正文而不是片段", article.contentText.contains("第 10 段"))
        assertTrue(article.quality.isComplete)
    }

    @Test
    fun `script and style never leak into body`() {
        val html = """
            <html><head><style>.a{color:red}</style></head><body><article>
              <p>${longText()}</p>
              <script>var x = "不该出现的脚本内容";</script>
            </article></body></html>
        """.trimIndent()

        val article = ArticleExtractor.extract("https://example.com/a", html, config)!!

        assertFalse(article.contentHtml.contains("该出现的脚本内容"))
        assertFalse(article.contentHtml.contains("color:red"))
    }

    // ———————————————————————————————————————————————
    // 日期解析
    // ———————————————————————————————————————————————

    @Test
    fun `parses common date formats`() {
        assertTrue(ArticleExtractor.parseDateTime("2026-08-30T10:15:00Z") != null)
        assertTrue(ArticleExtractor.parseDateTime("2026-08-30T10:15:00+08:00") != null)
        assertTrue(ArticleExtractor.parseDateTime("2026-08-30") != null)
        assertEquals(null, ArticleExtractor.parseDateTime(""))
        assertEquals(null, ArticleExtractor.parseDateTime("不是日期"))
    }
}
