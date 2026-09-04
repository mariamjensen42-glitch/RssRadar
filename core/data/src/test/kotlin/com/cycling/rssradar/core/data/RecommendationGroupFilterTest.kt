package com.cycling.rssradar.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 推荐流按分组过滤推荐序的纯规则（issue #74）：默认组必须同时命中空串。 */
class RecommendationGroupFilterTest {

    private val groupOf = mapOf(
        1L to "科技",
        2L to "",
        3L to "默认",
        4L to "生活",
    )

    @Test
    fun `筛选普通分组只保留命中行`() {
        val result = filterRankedIdsByGroup(listOf(1L, 3L, 4L), groupOf, "科技", "默认")
        assertEquals(listOf(1L), result)
    }

    @Test
    fun `筛选默认组时空串行也命中——与 DB 端谓词语义一致`() {
        val result = filterRankedIdsByGroup(listOf(1L, 2L, 3L, 4L), groupOf, "默认", "默认")
        assertEquals(listOf(2L, 3L), result)
    }

    @Test
    fun `映射缺失的 id 直接丢弃——打分后文章可能已被删`() {
        val result = filterRankedIdsByGroup(listOf(99L, 1L), groupOf, "科技", "默认")
        assertEquals(listOf(1L), result)
    }

    @Test
    fun `顺序保持输入推荐序`() {
        val result = filterRankedIdsByGroup(listOf(1L, 4L, 2L, 3L), groupOf, "默认", "默认")
        assertEquals(listOf(2L, 3L), result)
    }

    @Test
    fun `空推荐序过滤后仍为空`() {
        val result = filterRankedIdsByGroup(emptyList(), groupOf, "科技", "默认")
        assertEquals(emptyList<Long>(), result)
    }
}
