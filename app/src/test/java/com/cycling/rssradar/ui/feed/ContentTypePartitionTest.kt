package com.cycling.rssradar.ui.feed

import com.cycling.rssradar.core.data.filterRankedIdsByContentType
import com.cycling.rssradar.core.data.db.FeedEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 内容分区（issue #75）纯规则：推荐序按 contentType 保序过滤 + 分区枚举映射与文案。 */
class ContentTypePartitionTest {

    /** 文章 id → 所属源 contentType。 */
    private val typeOf = mapOf(
        1L to FeedEntity.CONTENT_TYPE_ARTICLE,
        2L to FeedEntity.CONTENT_TYPE_IMAGE,
        3L to FeedEntity.CONTENT_TYPE_VIDEO,
        4L to FeedEntity.CONTENT_TYPE_AUDIO,
    )

    @Test
    fun `按类型过滤只保留命中行`() {
        val result = filterRankedIdsByContentType(listOf(1L, 2L, 3L, 4L), typeOf, FeedEntity.CONTENT_TYPE_IMAGE)
        assertEquals(listOf(2L), result)
    }

    @Test
    fun `保序过滤——推荐序不错位`() {
        val result = filterRankedIdsByContentType(listOf(4L, 2L, 3L), typeOf, FeedEntity.CONTENT_TYPE_VIDEO)
        assertEquals(listOf(3L), result)
    }

    @Test
    fun `映射缺失的 id 直接丢弃——打分后文章可能已被删`() {
        val result = filterRankedIdsByContentType(listOf(99L, 2L), typeOf, FeedEntity.CONTENT_TYPE_IMAGE)
        assertEquals(listOf(2L), result)
    }

    @Test
    fun `空推荐序过滤后仍为空`() {
        val result = filterRankedIdsByContentType(emptyList(), typeOf, FeedEntity.CONTENT_TYPE_AUDIO)
        assertEquals(emptyList<Long>(), result)
    }

    @Test
    fun `枚举 dbValue 与 ADR-0014 常量一致`() {
        assertEquals(null, ContentTypeFilter.All.dbValue)
        assertEquals(FeedEntity.CONTENT_TYPE_IMAGE, ContentTypeFilter.Image.dbValue)
        assertEquals(FeedEntity.CONTENT_TYPE_VIDEO, ContentTypeFilter.Video.dbValue)
        assertEquals(FeedEntity.CONTENT_TYPE_AUDIO, ContentTypeFilter.Audio.dbValue)
    }

    @Test
    fun `空分区文案按类型区分`() {
        val imageCopy = ContentTypeFilter.Image.emptyCopy()
        assertTrue(imageCopy.first.contains("图片"))
        assertTrue(imageCopy.second.contains("图片"))
        val audioCopy = ContentTypeFilter.Audio.emptyCopy()
        assertTrue(audioCopy.first.contains("音频"))
        // 「全部」为空沿用 All tab 现有口径
        assertEquals("还没有订阅", ContentTypeFilter.All.emptyCopy().first)
    }
}
