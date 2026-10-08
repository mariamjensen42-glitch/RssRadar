package com.cycling.rssradar.core.domain.recommendation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 冷启动种子的守门测试。
 *
 * 锁的是这条链路上最容易静默失效的两件事：**种子词与正文用的是不是同一个分词器**
 * （不同形就永远匹配不上，界面上却看不出异常），以及**勾选会不会压过真实阅读**
 * （压过就等于把推荐流变成一个用户自己写死的关键词订阅）。
 */
class RecommendationSeedsTest {

    private val now = 1_700_000_000_000L

    private fun candidate(id: Long, title: String) = RecommendationCandidate(
        id = id,
        feedId = 1,
        title = title,
        summary = null,
        publishedAt = now,
        fetchedAt = now,
    )

    private val corpus = listOf(
        candidate(1, "科技行业观察"),
        candidate(2, "美食推荐"),
        candidate(3, "无关条目甲"),
        candidate(4, "无关条目乙"),
        candidate(5, "无关条目丙"),
        candidate(6, "无关条目丁"),
        candidate(7, "无关条目戊"),
        candidate(8, "无关条目己"),
    )

    private fun weightsOf(profile: InterestProfile): Map<String, Double> =
        profile.terms.associate { it.term to it.weight }

    /** 某个词在画像里的权重；找不到返回 0。 */
    private fun weightOf(profile: InterestProfile, term: String): Double =
        weightsOf(profile)[term] ?: 0.0

    @Test
    fun `只勾领域也能让画像不再是空的`() {
        val profile = RecommendationScoring.buildProfile(
            samples = emptyList(),
            candidates = corpus,
            feedTotals = emptyMap(),
            now = now,
            seeds = listOf("科技"),
        )
        assertFalse(profile.isEmpty)
        assertTrue(weightOf(profile, "科技") > 0.0)
    }

    @Test
    fun `种子按与正文相同的分词器切成二字片段`() {
        val profile = RecommendationScoring.buildProfile(
            samples = emptyList(),
            candidates = corpus,
            feedTotals = emptyMap(),
            now = now,
            seeds = listOf("人工智能"),
        )
        // 整串"人工智能"永远不会出现在文章的词袋里，必须切成相邻二字
        assertEquals(0.0, weightOf(profile, "人工智能"), 0.0)
        assertTrue(weightOf(profile, "人工") > 0.0)
        assertTrue(weightOf(profile, "智能") > 0.0)
    }

    @Test
    fun `真实阅读压过勾选的领域`() {
        val profile = RecommendationScoring.buildProfile(
            samples = listOf(
                EngagementSample(
                    feedId = 1,
                    title = "科技行业观察",
                    summary = null,
                    lastOpenedAt = now,
                    starred = false,
                    bookmarked = false,
                ),
            ),
            candidates = corpus,
            feedTotals = emptyMap(),
            now = now,
            seeds = listOf("美食"),
        )
        // 勾了"美食"但只读过"科技"：被真实读过的那个词必须更重
        assertTrue(
            "读过的话题(${weightOf(profile, "科技")}) 应当重于勾选的领域(${weightOf(profile, "美食")})",
            weightOf(profile, "科技") > weightOf(profile, "美食"),
        )
    }

    @Test
    fun `不勾选时画像与从前完全一致`() {
        val samples = listOf(
            EngagementSample(
                feedId = 1,
                title = "科技行业观察",
                summary = null,
                lastOpenedAt = now,
                starred = false,
                bookmarked = false,
            ),
        )
        val withDefault = RecommendationScoring.buildProfile(samples, corpus, emptyMap(), now)
        val withEmpty = RecommendationScoring.buildProfile(samples, corpus, emptyMap(), now, emptyList())
        assertEquals(withDefault.terms, withEmpty.terms)
    }

    @Test
    fun `命中所选领域的文章话题分更高`() {
        val profile = RecommendationScoring.buildProfile(
            samples = emptyList(),
            candidates = corpus,
            feedTotals = emptyMap(),
            now = now,
            seeds = listOf("美食"),
        )
        val scores = RecommendationScoring.score(corpus, profile, now = now).associateBy { it.id }
        assertTrue(
            "命中领域的文章话题分应高于无关文章",
            scores.getValue(2).topic > scores.getValue(3).topic,
        )
    }

    @Test
    fun `存档里的陌生领域会被剔除`() {
        // 词表改过一次之后，旧档里可能留着已经删掉的领域；留着就会出现一条
        // 用户既看不到、也取消不掉的选项
        val stored = listOf("科技", "已删除的领域", " 美食 ", "科技")
        assertEquals(listOf("科技", "美食"), RecommendationSeeds.sanitize(stored))
    }

    @Test
    fun `可勾选的领域表自身是自洽的`() {
        assertEquals(RecommendationSeeds.TOPICS.size, RecommendationSeeds.TOPICS.distinct().size)
        // 表里的每一项都必须能通过自己的过滤：否则界面上会出现一个勾了就被丢掉的选项
        assertEquals(RecommendationSeeds.TOPICS, RecommendationSeeds.sanitize(RecommendationSeeds.TOPICS))
    }
}
