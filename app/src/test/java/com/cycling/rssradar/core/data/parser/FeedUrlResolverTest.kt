package com.cycling.rssradar.core.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val BING_FEED = "https://www.bing.com/HPImageArchive.aspx?format=rss&idx=0&n=7&mkt=zh-CN"
private const val BING_LINK_RELATIVE =
    "/th?id=OHR.AdelieTeacher_ZH-CN2201820679_1920x1080.jpg&rf=LaDigue_1920x1080.jpg&pid=hp"
private const val BING_LINK_ABSOLUTE =
    "https://www.bing.com/th?id=OHR.AdelieTeacher_ZH-CN2201820679_1920x1080.jpg&rf=LaDigue_1920x1080.jpg&pid=hp"

/** 条目链接补全与「链接即图片」兜底（必应每日壁纸那类源）的守门测试。 */
class FeedUrlResolverTest {

    @Test
    fun `相对链接按 feed 地址补成绝对地址`() {
        assertEquals(BING_LINK_ABSOLUTE, FeedUrlResolver.absolutize(BING_LINK_RELATIVE, BING_FEED))
    }

    @Test
    fun `绝对链接原样返回——绝大多数源走这条，行为不能变`() {
        val url = "https://sspai.com/post/12345"
        assertEquals(url, FeedUrlResolver.absolutize(url, BING_FEED))
    }

    @Test
    fun `没有基准或链接为空时不硬拼`() {
        assertEquals(BING_LINK_RELATIVE, FeedUrlResolver.absolutize(BING_LINK_RELATIVE, ""))
        assertEquals("", FeedUrlResolver.absolutize("", BING_FEED))
    }

    @Test
    fun `路径后缀是图片扩展名时认作图片`() {
        assertEquals(
            "https://example.com/a/photo.jpg",
            FeedUrlResolver.imageUrlOrNull("https://example.com/a/photo.jpg"),
        )
        assertEquals(
            "https://example.com/a/photo.webp?w=100",
            FeedUrlResolver.imageUrlOrNull("https://example.com/a/photo.webp?w=100"),
        )
    }

    @Test
    fun `查询串里带图片扩展名也认作图片——必应就是这种形态`() {
        assertEquals(BING_LINK_ABSOLUTE, FeedUrlResolver.imageUrlOrNull(BING_LINK_ABSOLUTE))
    }

    @Test
    fun `普通文章链接不当作图片`() {
        assertNull(FeedUrlResolver.imageUrlOrNull("https://sspai.com/post/12345"))
        assertNull(FeedUrlResolver.imageUrlOrNull("https://example.com/article.html"))
        assertNull(FeedUrlResolver.imageUrlOrNull("https://example.com/th?id=123&pid=hp"))
    }

    @Test
    fun `相对链接进不了图片判定——必须先补全`() {
        assertNull(FeedUrlResolver.imageUrlOrNull(BING_LINK_RELATIVE))
    }

    @Test
    fun `resolveFeed 同时补链接与封面`() {
        val article = RssParser.ParsedArticle(
            link = BING_LINK_RELATIVE,
            title = "南极洲的阿德利企鹅",
            summary = null,
            contentHtml = null,
            contentText = null,
            author = null,
            publishedAt = null,
            coverUrl = null,
        )
        val feed = RssParser.ParsedFeed(title = "必应图片", articles = listOf(article))

        val resolved = FeedUrlResolver.resolveFeed(feed, BING_FEED).articles.single()

        assertEquals(BING_LINK_ABSOLUTE, resolved.link)
        assertEquals(BING_LINK_ABSOLUTE, resolved.coverUrl)
    }

    @Test
    fun `解析器已经给出封面时不被覆盖`() {
        val article = RssParser.ParsedArticle(
            link = "https://example.com/post/1",
            title = "标题",
            summary = null,
            contentHtml = null,
            contentText = null,
            author = null,
            publishedAt = null,
            coverUrl = "https://cdn.example.com/og.png",
        )
        val feed = RssParser.ParsedFeed(title = "源", articles = listOf(article))

        val resolved = FeedUrlResolver.resolveFeed(feed, BING_FEED).articles.single()

        assertEquals("https://cdn.example.com/og.png", resolved.coverUrl)
    }
}
