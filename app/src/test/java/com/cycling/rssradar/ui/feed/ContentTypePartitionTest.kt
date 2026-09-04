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

    // ———— 以下为 QA 补充边界（#75 验证清单第 4 项） ————

    /** 无命中的类型：输入非空但零命中，输出必须是空列表而非异常或原序（分区空列表路径）。 */
    @Test
    fun `非空输入零命中返回空列表`() {
        val articleOnly = mapOf(1L to FeedEntity.CONTENT_TYPE_ARTICLE, 2L to FeedEntity.CONTENT_TYPE_ARTICLE)
        val result = filterRankedIdsByContentType(listOf(1L, 2L), articleOnly, FeedEntity.CONTENT_TYPE_VIDEO)
        assertTrue(result.isEmpty())
    }

    /** 按文章类型（ADR-0014 的 0）过滤：与图片/视频/音频同一条规则路径。 */
    @Test
    fun `按文章类型过滤保序命中`() {
        val result = filterRankedIdsByContentType(listOf(3L, 1L, 4L), typeOf, FeedEntity.CONTENT_TYPE_ARTICLE)
        assertEquals(listOf(1L), result)
    }

    /** 混合命中保序：多个命中行必须按原推荐序输出，不错位不丢行。 */
    @Test
    fun `混合类型多命中保序输出`() {
        val result = filterRankedIdsByContentType(listOf(4L, 3L, 2L, 1L), typeOf, FeedEntity.CONTENT_TYPE_IMAGE)
        assertEquals(listOf(2L), result)
    }

    /**
     * 方案 C 的 chip 行结构约束（PRD）：恰好 4 个 chip（全部/图片/视频/音频），
     * 不设「文章」chip——文章是默认态，多出「文章」chip 即与 PRD 不符。
     */
    @Test
    fun `分区枚举恰好四个且无文章chip`() {
        assertEquals(4, ContentTypeFilter.entries.size)
        assertEquals(listOf("全部", "图片", "视频", "音频"), ContentTypeFilter.entries.map { it.label })
        assertTrue(ContentTypeFilter.entries.none { it.dbValue == FeedEntity.CONTENT_TYPE_ARTICLE })
    }

    /** Video 的空分区文案分支（原测试未覆盖）。 */
    @Test
    fun `视频分区空文案`() {
        val videoCopy = ContentTypeFilter.Video.emptyCopy()
        assertTrue(videoCopy.first.contains("视频"))
        assertTrue(videoCopy.second.contains("视频"))
    }
}
