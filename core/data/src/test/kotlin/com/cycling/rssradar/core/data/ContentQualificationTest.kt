package com.cycling.rssradar.core.data

import com.cycling.rssradar.core.data.db.ArticleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「够格正文」判定唯一落点的规则测试（写侧归类 + 读侧可用性）。 */
class ContentQualificationTest {

    @Test
    fun `write side qualifies long content and rejects summary`() {
        val body = "这是正文内容，".repeat(200) // 约 1400 字
        val summary = "这是一篇文章摘要，".repeat(20) // 约 180 字

        assertTrue(ContentQualification.qualifies("<p>$body</p>", body))
        assertFalse(ContentQualification.qualifies("<p>$summary</p>", summary))
        assertFalse(ContentQualification.qualifies(null, null))
        assertFalse(ContentQualification.qualifies("", ""))
        assertFalse(ContentQualification.qualifies("   ", "  "))
    }

    @Test
    fun `write side maps qualification to content source`() {
        val body = "这是正文内容，".repeat(200)
        val summary = "这是一篇文章摘要，".repeat(20)

        assertEquals(
            ArticleEntity.CONTENT_SOURCE_FEED,
            ContentQualification.contentSourceFor("<p>$body</p>", body),
        )
        assertEquals(
            ArticleEntity.CONTENT_SOURCE_NONE,
            ContentQualification.contentSourceFor("<p>$summary</p>", summary),
        )
    }

    @Test
    fun `read side only accepts stored content with a source`() {
        assertTrue(
            ContentQualification.hasUsableContent(
                article(content = "<p>full</p>", contentSource = ArticleEntity.CONTENT_SOURCE_FEED),
            ),
        )
        assertTrue(
            ContentQualification.hasUsableContent(
                article(content = "<p>fetched</p>", contentSource = ArticleEntity.CONTENT_SOURCE_WEB),
            ),
        )
        // 摘要级内容虽然躺在 content 列，但记 NONE → 不算有可用正文
        assertFalse(
            ContentQualification.hasUsableContent(
                article(content = "<p>summary</p>", contentSource = ArticleEntity.CONTENT_SOURCE_NONE),
            ),
        )
        assertFalse(
            ContentQualification.hasUsableContent(
                article(content = null, contentSource = ArticleEntity.CONTENT_SOURCE_FEED),
            ),
        )
    }

    private fun article(content: String?, contentSource: Int) = ArticleEntity(
        feedId = 1L,
        link = "https://example.com/a",
        title = "t",
        summary = null,
        publishedAt = null,
        fetchedAt = 0L,
        content = content,
        contentSource = contentSource,
    )
}
