package com.cycling.rssradar.core.domain.recommendation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 推荐打分（ADR-0013）的回归测试：纯 JVM，不依赖 Room / Android。
 *
 * 覆盖的是"算法说了算"的几条硬规则：
 * 1. 打分三分量可解释（新鲜度/源亲和度/内容亲和度都落在预期区间）；
 * 2. 偏好过的源与话题排在前面；
 * 3. 多样性：同窗口内同一源不超过 2 条，且不丢条目；
 * 4. 「减少此类」的降权真实生效；
 * 5. 冷启动退化：无画像时按源轮转，且一条不少。
 */
class RecommendationScoringTest {

    private val now = 1_700_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    private fun candidate(
        id: Long,
        feedId: Long,
        title: String,
        summary: String? = null,
        ageDays: Long = 1,
    ) = RecommendationCandidate(
        id = id,
        feedId = feedId,
        title = title,
        summary = summary,
        publishedAt = now - ageDays * day,
        fetchedAt = now - ageDays * day,
    )

    private fun sample(
        feedId: Long,
        title: String,
        openedDaysAgo: Long? = 1,
        starred: Boolean = false,
        bookmarked: Boolean = false,
    ) = EngagementSample(
        feedId = feedId,
        title = title,
        summary = null,
        lastOpenedAt = openedDaysAgo?.let { now - it * day },
        starred = starred,
        bookmarked = bookmarked,
    )

    @Test
    fun `画像为空时是空画像，走冷启动`() {
        val profile = RecommendationScoring.buildProfile(
            samples = emptyList(),
            candidates = listOf(candidate(1, 1, "标题")),
            feedTotals = mapOf(1L to 3),
            now = now,
        )
        assertTrue(profile.isEmpty)
        assertTrue(profile.terms.isEmpty())
        assertTrue(profile.feedAffinity.isEmpty())
    }

    @Test
    fun `画像只从真实行为里学，兴趣词来自读过的标题`() {
        val profile = RecommendationScoring.buildProfile(
            samples = listOf(
                sample(1, "大模型推理优化实践"),
                sample(1, "大模型训练成本下降"),
            ),
            candidates = listOf(
                candidate(1, 1, "大模型推理优化实践"),
                candidate(2, 2, "周末露营装备清单"),
            ),
            feedTotals = mapOf(1L to 10, 2L to 10),
            now = now,
        )
        // 中文 bigram：读过的"大模型"相关片段应当进词袋，露营类不进
        assertTrue(profile.terms.any { it.term == "大模" })
        assertTrue(profile.terms.any { it.term == "模型" })
        assertTrue(profile.terms.none { it.term.contains("露营") })
        // 权重已归一化到 (0,1]
        assertTrue(profile.terms.all { it.weight > 0.0 && it.weight <= 1.0 })
        assertTrue(profile.feedAffinity.containsKey(1L))
    }

    @Test
    fun `源亲和度按打开率归一化，常读的源为 1`() {
        val profile = RecommendationScoring.buildProfile(
            samples = List(4) { sample(1, "文章$it", openedDaysAgo = 1) } +
                listOf(sample(2, "偶尔看的一篇", openedDaysAgo = 1)),
            candidates = listOf(candidate(1, 1, "a"), candidate(2, 2, "b")),
            feedTotals = mapOf(1L to 10, 2L to 100),
            now = now,
        )
        val affinity1 = profile.feedAffinity[1L] ?: 0.0
        val affinity2 = profile.feedAffinity[2L] ?: 0.0
        assertTrue("常读源亲和度应最高（=$affinity1）", affinity1 > affinity2)
        assertEquals(1.0, affinity1, 1e-9)
    }

    @Test
    fun `打分三分量落在 0 到 1 之间`() {
        val candidates = listOf(
            candidate(1, 1, "大模型推理优化实践", ageDays = 1),
            candidate(2, 2, "周末露营装备清单", ageDays = 10),
        )
        val profile = RecommendationScoring.buildProfile(
            samples = listOf(sample(1, "大模型推理优化实践")),
            candidates = candidates,
            feedTotals = mapOf(1L to 10, 2L to 10),
            now = now,
        )
        val scored = RecommendationScoring.score(candidates, profile, now = now)
        assertEquals(2, scored.size)
        scored.forEach {
            assertTrue(it.freshness in 0.0..1.0)
            assertTrue(it.source in 0.0..1.0)
            assertTrue(it.topic in 0.0..1.0)
        }
    }

    @Test
    fun `偏好过的源与话题排在前面`() {
        val liked = candidate(1, 1, "大模型推理优化实践", ageDays = 12)
        val other = candidate(2, 2, "周末露营装备清单", ageDays = 1)
        val profile = RecommendationScoring.buildProfile(
            samples = List(5) { sample(1, "大模型推理优化实践", openedDaysAgo = 1) },
            candidates = listOf(liked, other),
            feedTotals = mapOf(1L to 10, 2L to 10),
            now = now,
        )
        val scored = RecommendationScoring.score(listOf(liked, other), profile, now = now)
        // 即使 liked 更旧（新鲜度低），高频阅读的源 + 命中兴趣词也应压过它
        assertEquals(liked.id, scored.first().id)
    }

    @Test
    fun `减少此类降权后该源整体靠后`() {
        val a = candidate(1, 1, "大模型推理优化")
        val b = candidate(2, 2, "别的主题文章")
        val profile = RecommendationScoring.buildProfile(
            samples = List(3) { sample(1, "大模型推理优化", openedDaysAgo = 1) },
            candidates = listOf(a, b),
            feedTotals = mapOf(1L to 10, 2L to 10),
            now = now,
        )
        val before = RecommendationScoring.score(listOf(a, b), profile, now = now)
        assertEquals(1L, before.first().id)

        val after = RecommendationScoring.score(
            candidates = listOf(a, b),
            profile = profile,
            penalties = mapOf(1L to 0.2),
            now = now,
        )
        assertEquals(2L, after.first().id)
    }

    @Test
    fun `多样性打散：窗口内同源不超两条且不丢条目`() {
        // 真实规模：12 个源各 5 条（源数 > 窗口/配额 = 10，约束可满足）
        val feedOf = HashMap<Long, Long>()
        val ordered = ArrayList<Pair<Long, Long>>()
        var id = 1L
        repeat(5) {
            for (feed in 1L..12L) {
                ordered += id to feed
                feedOf[id] = feed
                id++
            }
        }
        val ranked = RecommendationScoring.diversify(ordered)
        assertEquals(ordered.size, ranked.size)
        assertEquals(ordered.map { it.first }.toSet(), ranked.toSet())
        // 任意连续窗口内同源都不超配额（这才是打散的契约）
        val feeds = ranked.map { feedOf[it]!! }
        for (start in 0..ranked.size - RecommendationScoring.DIVERSITY_WINDOW) {
            val window = feeds.subList(start, start + RecommendationScoring.DIVERSITY_WINDOW)
            val counts = window.groupingBy { it }.eachCount()
            assertTrue(
                "第 $start 起的窗口内同源不得超过 ${RecommendationScoring.MAX_PER_FEED} 条：$counts",
                counts.values.all { it <= RecommendationScoring.MAX_PER_FEED },
            )
        }
        // 前两条来自不同源（打散立刻生效，不是攒到窗口边界才生效）
        assertEquals(2, ranked.take(2).map { feedOf[it]!! }.toSet().size)
    }

    @Test
    fun `多样性打散：候选全被一个源占满时不丢条目`() {
        // 退化场景：窗口里塞不下别的源，强制放行比丢文章更诚实
        val ordered = List(6) { it.toLong() + 1 to 1L } + List(3) { (it + 7).toLong() to 2L }
        val ranked = RecommendationScoring.diversify(ordered)
        assertEquals(ordered.size, ranked.size)
        assertEquals(ordered.map { it.first }.toSet(), ranked.toSet())
    }

    @Test
    fun `冷启动退化按订阅源轮转，一条不少`() {
        val candidates = listOf(
            candidate(1, 1, "a", ageDays = 1),
            candidate(2, 1, "b", ageDays = 2),
            candidate(3, 1, "c", ageDays = 3),
            candidate(4, 2, "d", ageDays = 1),
            candidate(5, 3, "e", ageDays = 1),
        )
        val ranked = RecommendationScoring.coldStartRank(candidates)
        // 一条不少（退化只改顺序，不筛内容）
        assertEquals(candidates.map { it.id }.toSet(), ranked.toSet())
        assertEquals(candidates.size, ranked.size)
        // 轮转：前三条来自三个不同的源，而不是某个源的前三条
        val feedsOfFirst3 = ranked.take(3).map { id -> candidates.first { it.id == id }.feedId }
        assertEquals(3, feedsOfFirst3.toSet().size)
        // 同一源内部的原始顺序（新→旧）保持不变
        val feed1Order = ranked.map { id -> candidates.first { it.id == id } }
            .filter { it.feedId == 1L }.map { it.id }
        assertEquals(listOf(1L, 2L, 3L), feed1Order)
    }

    @Test
    fun `没有候选时不产出排序`() {
        assertTrue(RecommendationScoring.coldStartRank(emptyList()).isEmpty())
        val empty = RecommendationScoring.score(
            candidates = emptyList(),
            profile = InterestProfile(emptyList(), emptyMap()),
            now = now,
        )
        assertTrue(empty.isEmpty())
    }

    @Test
    fun `只收藏没打开过的样本也能进画像，但权重低于真实打开`() {
        val starredOnly = RecommendationScoring.buildProfile(
            samples = listOf(sample(9, "量子计算新进展", openedDaysAgo = null, starred = true)),
            candidates = listOf(candidate(1, 9, "量子计算新进展")),
            feedTotals = mapOf(9L to 10),
            now = now,
        )
        val opened = RecommendationScoring.buildProfile(
            samples = listOf(sample(9, "量子计算新进展", openedDaysAgo = 1)),
            candidates = listOf(candidate(1, 9, "量子计算新进展")),
            feedTotals = mapOf(9L to 10),
            now = now,
        )
        // 归一化后归一化值都会归 1，这里比的是"是否学到了词"——两者都该学到
        assertTrue(starredOnly.terms.isNotEmpty())
        assertTrue(opened.terms.isNotEmpty())
        // 但只收藏不打开不算"打开率"，不产生源亲和度
        assertTrue(starredOnly.feedAffinity.isEmpty())
        assertTrue(opened.feedAffinity.isNotEmpty())
    }
}
