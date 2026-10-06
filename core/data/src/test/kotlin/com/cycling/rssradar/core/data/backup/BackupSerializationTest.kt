package com.cycling.rssradar.core.data.backup

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `订阅源往返一致`() {
        val feed = BackupFeed(
            url = "https://example.com/feed",
            title = "示例站点",
            group = "技术",
            iconUrl = "https://example.com/favicon.ico",
            contentType = 1,
            sourceType = 1,
            notificationsEnabled = false,
        )
        assertEquals(feed, json.decodeFromString<BackupFeed>(json.encodeToString(feed)))
    }

    @Test
    fun `文章往返一致含用户状态`() {
        val article = BackupArticle(
            feedUrl = "https://example.com/feed",
            link = "https://example.com/a/1",
            title = "标题",
            summary = "摘要",
            publishedAt = 1_700_000_000_000L,
            fetchedAt = 1_700_000_100_000L,
            isRead = true,
            isStarred = true,
            starredAt = 1_700_000_200_000L,
            mediaKind = 2,
            mediaUrl = "https://example.com/a.mp3",
        )
        assertEquals(article, json.decodeFromString<BackupArticle>(json.encodeToString(article)))
    }

    @Test
    fun `标注往返一致`() {
        val annotation = BackupAnnotation(
            feedUrl = "https://example.com/feed",
            articleLink = "https://example.com/a/1",
            kind = 1,
            quote = "被划的重点",
            prefix = "前文",
            suffix = "后文",
            startOffset = 120,
            endOffset = 125,
            color = 2,
            note = "备注内容",
        )
        assertEquals(annotation, json.decodeFromString<BackupAnnotation>(json.encodeToString(annotation)))
    }

    @Test
    fun `过滤规则往返一致`() {
        val rule = BackupFilterRule(
            name = "屏蔽广告",
            matchType = 1,
            fieldMask = 5,
            pattern = "第\\d+期",
            scopeType = 2,
            scopeId = "技术",
            action = 1,
            priority = 3,
        )
        assertEquals(rule, json.decodeFromString<BackupFilterRule>(json.encodeToString(rule)))
    }

    @Test
    fun `全局 AI 产物往返一致`() {
        val artifact = BackupAiArtifact(
            subjectKind = 2,
            subjectId = 0L,
            kind = 31,
            payload = """{"summary":"今天读了 3 篇"}""",
            model = "deepseek-chat",
            inputChars = 128,
            outputChars = 42,
            createdAt = 1_700_000_000_000L,
        )
        assertEquals(artifact, json.decodeFromString<BackupAiArtifact>(json.encodeToString(artifact)))
    }

    @Test
    fun `缺失字段回落默认值`() {
        val restored = json.decodeFromString<BackupFilterRule>("""{"name":"n","pattern":"p"}""")
        assertTrue(restored.enabled)
        assertEquals(0, restored.priority)
        assertEquals(false, restored.caseSensitive)
    }

    @Test
    fun `头部自动带上格式版本`() {
        val header = BackupHeader(exportedAt = 1L, appVersion = "1.1.0")
        val restored = json.decodeFromString<BackupHeader>(json.encodeToString(header))
        assertEquals(BACKUP_FORMAT_VERSION, restored.formatVersion)
    }

    @Test
    fun `未知字段被忽略以容忍更高版本备份`() {
        val restored = json.decodeFromString<BackupFeed>(
            """{"url":"u","title":"t","futureField":"x"}""",
        )
        assertEquals("u", restored.url)
        assertEquals("t", restored.title)
    }

    @Test
    fun `导入报告可判定空结果`() {
        assertTrue(ImportReport().isEmpty)
        assertTrue(!ImportReport(feedsAdded = 1).isEmpty)
    }
}
