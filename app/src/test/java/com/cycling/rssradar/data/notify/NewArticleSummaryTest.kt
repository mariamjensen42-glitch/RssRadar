package com.cycling.rssradar.data.notify

import com.cycling.rssradar.data.db.ArticleEntity
import com.cycling.rssradar.data.db.ArticleWithFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [NewArticleSummary] 单测：数字必须真实（标题条数 = 列表长度），不多报也不少报。 */
class NewArticleSummaryTest {

    private fun item(feed: String, title: String) = ArticleWithFeed(
        article = ArticleEntity(
            id = 1,
            feedId = 1,
            link = "https://a/1",
            title = title,
            summary = null,
            publishedAt = 1_700_000_000_000L,
            fetchedAt = 1_700_000_000_000L,
        ),
        feedTitle = feed,
        feedGroup = "默认",
        feedIconUrl = null,
    )

    @Test
    fun `empty list yields no notification`() {
        assertNull(NewArticleSummary.build(emptyList()))
    }

    @Test
    fun `single article uses feed and title directly`() {
        val summary = NewArticleSummary.build(listOf(item("阮一峰", "科技爱好者周刊")))!!
        assertEquals("1 篇新文章", summary.title)
        assertEquals("阮一峰 · 科技爱好者周刊", summary.contentText)
        assertEquals("阮一峰 · 科技爱好者周刊", summary.bigText)
    }

    @Test
    fun `multiple articles report real total and cap the expanded lines`() {
        val articles = (1..5).map { item("源$it", "标题$it") }
        val summary = NewArticleSummary.build(articles)!!
        assertEquals("5 篇新文章", summary.title)
        // 展开只放前 3 条，剩下的如实报个数
        assertEquals(3, summary.bigText.lines().count { it.startsWith("源") })
        assertEquals(true, summary.bigText.endsWith("还有 2 篇…"))
        assertEquals(true, summary.contentText.endsWith("等 5 篇"))
    }

    @Test
    fun `blank title and feed fall back without fabricating`() {
        val summary = NewArticleSummary.build(listOf(item("", "")))!!
        assertEquals("（无标题）", summary.contentText)
        assertEquals("（无标题）", summary.bigText)
    }
}
