package com.cycling.rssradar.core.data.ai

import com.cycling.rssradar.core.data.db.AiArtifactEntity
import com.cycling.rssradar.core.data.db.AiTaskEntity


/**
 * 决定"该把哪些任务放进队列"——纯函数，不碰数据库，因此可单测。
 *
 * 三个决策点每个都对应一条真实的代价：
 * 1. **只排已开启且是批处理触发的功能**——关掉的功能不排，省得用户关了开关还在烧额度。
 * 2. **跳过已有产物的组合**——重跑要花钱，产物在就用产物。
 * 3. **总量卡在剩余预算内**——宁可少排几个，也不要排一堆注定跑不动的任务在队列里腐烂。
 */
object AiTaskPlanner {

    /** 一条待入队的任务。 */
    data class Spec(
        val feature: AiFeature,
        val targetId: Long,
        val priority: Int,
    )

    /** 产物存在性标记：`kind:subjectId`，与 [AiTaskEntity.dedupeKey] 同形便于对照。 */
    fun artifactKey(feature: AiFeature, subjectId: Long): String = "${feature.dbValue}:$subjectId"

    /**
     * 文章级批处理任务的排布。
     *
     * **按文章轮转而不是按功能轮转**：先给文章 A 排完它缺的所有功能，再排文章 B。
     * 理由是产物挂在文章上、UI 也按文章展示——预算中途耗尽时，
     * 「一部分文章有完整分析」比「所有文章都只有标签」对用户更有用。
     *
     * @param enabled 当前开启的功能集合
     * @param candidates 候选文章 id，已按调用方希望的优先顺序排好
     * @param existingKeys 已存在产物的 `kind:subjectId` 集合
     * @param remainingBudget 今日剩余额度；Int.MAX_VALUE 表示不限
     * @param priorityOf 文章 → 优先级（取自所属订阅源的配置）
     */
    fun planArticles(
        enabled: Set<AiFeature>,
        candidates: List<Long>,
        existingKeys: Set<String>,
        remainingBudget: Int,
        priorityOf: (Long) -> Int = { 0 },
    ): List<Spec> {
        val features = AiFeature.BATCH_FEATURES
            .filter { it in enabled && it.scope == AiScope.ARTICLE }
        if (features.isEmpty() || candidates.isEmpty() || remainingBudget <= 0) return emptyList()

        val specs = ArrayList<Spec>(minOf(candidates.size * features.size, remainingBudget))
        for (articleId in candidates) {
            val priority = priorityOf(articleId)
            for (feature in features) {
                if (specs.size >= remainingBudget) return specs
                if (artifactKey(feature, articleId) in existingKeys) continue
                specs += Spec(feature, articleId, priority)
            }
        }
        return specs
    }

    /**
     * 订阅源级批处理任务（目前只有「订阅源健康监控」）。
     * 每次都重跑——健康状态是随时间变化的，缓存昨天的结果没有意义。
     */
    fun planFeeds(
        enabled: Set<AiFeature>,
        feedIds: List<Long>,
        remainingBudget: Int,
        priorityOf: (Long) -> Int = { 0 },
    ): List<Spec> {
        val features = AiFeature.BATCH_FEATURES
            .filter { it in enabled && it.scope == AiScope.FEED }
        if (features.isEmpty() || feedIds.isEmpty() || remainingBudget <= 0) return emptyList()

        val specs = ArrayList<Spec>(feedIds.size * features.size)
        for (feedId in feedIds) {
            val priority = priorityOf(feedId)
            for (feature in features) {
                if (specs.size >= remainingBudget) return specs
                specs += Spec(feature, feedId, priority)
            }
        }
        return specs
    }

    /**
     * 全局批处理任务（每日简报、阅读报告、破壁、聚合、兴趣排序、订阅源推荐）。
     *
     * **这些任务每天都重跑**，因此不查产物是否存在——它们的语义就是"今天的"，
     * 复用昨天的产物等于给用户看过期内容。去重由 [AiTaskEntity.dedupeKey] 保证
     * 同一天不会堆两条。
     */
    fun planGlobal(
        enabled: Set<AiFeature>,
        remainingBudget: Int,
    ): List<Spec> {
        val features = AiFeature.BATCH_FEATURES
            .filter { it in enabled && it.scope == AiScope.GLOBAL }
        if (features.isEmpty() || remainingBudget <= 0) return emptyList()

        // 全局任务优先级最低：先把文章级分析跑完，再生产汇总类产物——
        // 简报与报告的质量取决于底下有多少篇文章已经分析完。
        return features.take(remainingBudget).map { Spec(it, AiArtifactEntity.GLOBAL_SUBJECT_ID, 0) }
    }

    /** 三段合一：文章 → 订阅源 → 全局，统一卡在预算内。 */
    fun planAll(
        enabled: Set<AiFeature>,
        articleIds: List<Long>,
        feedIds: List<Long>,
        existingKeys: Set<String>,
        remainingBudget: Int,
        articlePriorityOf: (Long) -> Int = { 0 },
        feedPriorityOf: (Long) -> Int = { 0 },
    ): List<Spec> {
        val articles = planArticles(enabled, articleIds, existingKeys, remainingBudget, articlePriorityOf)
        var left = remainingBudget - articles.size
        val feeds = planFeeds(enabled, feedIds, left, feedPriorityOf)
        left -= feeds.size
        val global = planGlobal(enabled, left)
        return articles + feeds + global
    }

    /**
     * 失败退避：1 分钟 → 5 分钟 → 终止。
     *
     * 只重试两次而不是更多：AI 任务的失败绝大多数是**内容问题**（正文为空、超长、
     * 模型拒答），这类失败重试一百次结果一样；真正值得重试的是网络抖动与限流，
     * 而这两类在 1~5 分钟量级内基本都能恢复。
     */
    /**
     * 失败退避：1 分钟 → 5 分钟。
     *
     * 刻意**不用** `Long.MAX_VALUE` 表示「不再重试」：调用方写的是 `now + backoffMs(n)`，
     * MAX_VALUE 会溢出成负数，反而让任务立刻到点、被无限重试。
     * 「该不该重试」由 [shouldRetry] 单独判定，两个语义不要挤在同一个返回值里。
     */
    fun backoffMs(attempts: Int): Long = when (attempts) {
        1 -> 60_000L
        else -> 300_000L
    }

    /** 是否还值得重试。 */
    fun shouldRetry(attempts: Int): Boolean = attempts < AiTaskEntity.MAX_ATTEMPTS
}
