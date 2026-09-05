package com.cycling.rssradar.core.domain.recommendation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RelatedScoring 的排序正确性：相关阅读（AiFeature.RELATED）的纯函数核心。
 * 打错了序，横滑条就是一排无关文章——比没有还糟。
 */
class RelatedScoringTest {

    private fun candidate(id: Long, title: String, summary: String? = null) =
        RecommendationCandidate(
            id = id, feedId = 1, title = title, summary = summary,
            publishedAt = null, fetchedAt = 0,
        )

    @Test
    fun `词面重合多的候选排在前面`() {
        val hits = RelatedScoring.rank(
            focusTitle = "苹果发布新一代芯片性能大幅提升",
            focusSummary = null,
            candidates = listOf(
                candidate(1, "苹果芯片性能测试实机跑分曝光"),
                candidate(2, "某球队夺得联赛冠军比赛精彩回顾"),
                candidate(3, "新款苹果芯片代工厂产能爬坡顺利"),
            ),
            limit = 8,
        )
        // 零分候选（体育文章）应被过滤掉而不是陪跑
        val order = hits.map { it.articleId }
        assertTrue("order=$order", 2L !in order)
        assertTrue("order=$order", 1L in order && 3L in order)
    }

    @Test
    fun `候选为空或焦点无词时返回空表`() {
        assertTrue(RelatedScoring.rank("标题", null, emptyList(), limit = 8).isEmpty())
        assertTrue(
            "焦点切不出任何词时应返回空而不是全体零分",
            RelatedScoring.rank("!!!", null, listOf(candidate(1, "苹果芯片")), limit = 8).isEmpty(),
        )
    }

    @Test
    fun `limit 生效且结果按分数降序`() {
        val candidates = (1..20).map {
            candidate(it.toLong(), if (it <= 5) "苹果芯片性能提升" else "完全无关的话题文章之$it")
        }
        val hits = RelatedScoring.rank("苹果芯片性能", null, candidates, limit = 8)
        // 零分（完全无关）的候选被过滤，只有 5 篇真正相关的留下
        assertEquals(5, hits.size)
        assertEquals(hits.sortedByDescending { it.score }.map { it.articleId }, hits.map { it.articleId })
        // 5 篇真正相关的必须全部入选且分数严格大于其余
        val top5 = hits.take(5).map { it.articleId }.toSet()
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), top5)
    }
}
