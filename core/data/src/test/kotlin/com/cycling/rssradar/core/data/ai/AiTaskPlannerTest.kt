package com.cycling.rssradar.core.data.ai

import com.cycling.rssradar.core.data.db.AiTaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


/**
 * 排程的纯函数测试。
 *
 * 核心要钉死的是**「按文章轮转」而不是「按功能轮转」**：预算一旦不够，
 * 两种排法给出的结果完全不同——前者让一部分文章拿到完整分析，
 * 后者让所有文章都只有第一项功能。后者对用户几乎没用。
 */
class AiTaskPlannerTest {

    private val enabled = setOf(AiFeature.TAGS, AiFeature.KEYWORDS, AiFeature.CLASSIFY)

    @Test
    fun `只排已开启且是批处理触发的功能`() {
        val specs = AiTaskPlanner.planArticles(
            enabled = setOf(AiFeature.TAGS, AiFeature.GLOSSARY),
            candidates = listOf(1L),
            existingKeys = emptySet(),
            remainingBudget = 100,
        )
        // GLOSSARY 是 REALTIME 触发，不该被排进批处理队列
        assertEquals(listOf(AiFeature.TAGS), specs.map { it.feature })
    }

    @Test
    fun `按文章轮转而不是按功能轮转`() {
        val specs = AiTaskPlanner.planArticles(
            enabled = enabled,
            candidates = listOf(10L, 20L, 30L),
            existingKeys = emptySet(),
            remainingBudget = 4,
        )
        // 功能顺序取枚举声明序（稳定，不随传入的 Set 迭代序变），
        // 关键是同一篇文章的三项连续排完，才轮到下一篇文章。
        assertEquals(
            listOf(
                AiFeature.CLASSIFY to 10L,
                AiFeature.TAGS to 10L,
                AiFeature.KEYWORDS to 10L,
                AiFeature.CLASSIFY to 20L,
            ),
            specs.map { it.feature to it.targetId },
        )
    }

    @Test
    fun `已有产物的组合被跳过`() {
        val specs = AiTaskPlanner.planArticles(
            enabled = enabled,
            candidates = listOf(1L),
            existingKeys = setOf(
                AiTaskPlanner.artifactKey(AiFeature.TAGS, 1L),
                AiTaskPlanner.artifactKey(AiFeature.KEYWORDS, 1L),
            ),
            remainingBudget = 100,
        )
        assertEquals(listOf(AiFeature.CLASSIFY), specs.map { it.feature })
    }

    @Test
    fun `预算为零时不排任何任务`() {
        val specs = AiTaskPlanner.planArticles(
            enabled = enabled,
            candidates = listOf(1L, 2L),
            existingKeys = emptySet(),
            remainingBudget = 0,
        )
        assertTrue(specs.isEmpty())
    }

    @Test
    fun `优先级取自订阅源配置`() {
        val specs = AiTaskPlanner.planArticles(
            enabled = setOf(AiFeature.TAGS),
            candidates = listOf(1L, 2L),
            existingKeys = emptySet(),
            remainingBudget = 10,
            priorityOf = { if (it == 2L) 80 else 0 },
        )
        // 计划阶段保持候选顺序，优先级只是记录在任务上，真正按优先级排序发生在队列领取时。
        assertEquals(listOf(0, 80), specs.map { it.priority })
    }

    @Test
    fun `全局任务优先级为零且每次都重排`() {
        val specs = AiTaskPlanner.planGlobal(
            enabled = setOf(AiFeature.DAILY_BRIEF),
            remainingBudget = 10,
        )
        assertEquals(1, specs.size)
        assertEquals(AiFeature.DAILY_BRIEF, specs[0].feature)
        assertEquals(0L, specs[0].targetId)
    }

    @Test
    fun `订阅源级任务按订阅源排布`() {
        val specs = AiTaskPlanner.planFeeds(
            enabled = setOf(AiFeature.FEED_HEALTH),
            feedIds = listOf(7L, 8L),
            remainingBudget = 10,
        )
        assertEquals(listOf(7L, 8L), specs.map { it.targetId })
    }

    @Test
    fun `三段合并后总量不超预算`() {
        val specs = AiTaskPlanner.planAll(
            enabled = setOf(AiFeature.TAGS, AiFeature.FEED_HEALTH, AiFeature.DAILY_BRIEF),
            articleIds = listOf(1L, 2L, 3L, 4L),
            feedIds = listOf(7L, 8L),
            existingKeys = emptySet(),
            remainingBudget = 5,
        )
        assertTrue(specs.size <= 5)
    }

    @Test
    fun `退避随尝试次数增长且第三次不再重试`() {
        assertTrue(AiTaskPlanner.backoffMs(1) < AiTaskPlanner.backoffMs(2))
        assertTrue(AiTaskPlanner.shouldRetry(1))
        assertTrue(AiTaskPlanner.shouldRetry(2))
        org.junit.Assert.assertFalse(AiTaskPlanner.shouldRetry(AiTaskEntity.MAX_ATTEMPTS))
    }

    @Test
    fun `产物键与任务去重键同形`() {
        // 两者必须可对照，否则排程去重与队列去重会各说各话。
        assertEquals(
            AiTaskPlanner.artifactKey(AiFeature.TAGS, 42L),
            AiTaskEntity.dedupeKey(AiFeature.TAGS.dbValue, 42L),
        )
    }
}
