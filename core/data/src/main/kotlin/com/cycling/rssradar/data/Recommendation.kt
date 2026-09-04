package com.cycling.rssradar.core.data

import com.cycling.rssradar.core.domain.recommendation.EngagementSample
import com.cycling.rssradar.core.domain.recommendation.InterestProfile
import com.cycling.rssradar.core.domain.recommendation.RecommendationCandidate
import com.cycling.rssradar.core.domain.recommendation.RecommendationScoring
import com.cycling.rssradar.core.data.db.AppDatabase
import com.cycling.rssradar.core.data.db.ArticleWithFeed
import com.cycling.rssradar.core.data.db.EngagementRow
import com.cycling.rssradar.core.data.db.RecommendationFeedbackEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 推荐流的家（ADR-0013）：加载候选池 → 建画像 → 打分 → 打散 → 按序还原文章。
 *
 * 计算时机是**进 tab 实时算**（候选池受「未读 + 时间窗」约束，规模可控），
 * 不落库、不做快照——快照的失效维护（刷新/已读变化都要重算）是个无底洞。
 * 打分本身是纯函数，见 [RecommendationScoring]。
 */
class Recommendation(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * 算一次推荐序，返回文章 id 的有序列表。调用方按页切片即可。
     * 画像为空（冷启动）时退化成「按订阅源轮转的最近未读」，列表永不为空
     * ——除了真的没有候选（没订阅或全读完）。
     */
    suspend fun rank(now: Long = System.currentTimeMillis()): List<Long> = withContext(ioDispatcher) {
        val dao = database.articleDao()
        val since = now - WINDOW_DAYS * DAY_MILLIS
        val candidates = dao.loadRecommendationCandidates(since, CANDIDATE_LIMIT).map { it.toCandidate() }
        if (candidates.isEmpty()) return@withContext emptyList()

        val samples = dao.loadEngagementSamples(SAMPLE_LIMIT).map { it.toSample() }
        val feedTotals = dao.countByFeedSince(since).associate { it.feedId to it.cnt }
        val penalties = feedback()

        val profile = RecommendationScoring.buildProfile(
            samples = samples,
            candidates = candidates,
            feedTotals = feedTotals,
            now = now,
        )
        if (profile.isEmpty) return@withContext RecommendationScoring.coldStartRank(candidates)

        val scored = RecommendationScoring.score(
            candidates = candidates,
            profile = profile,
            penalties = penalties,
            now = now,
        )
        val feedOf = candidates.associate { it.id to it.feedId }
        RecommendationScoring.diversify(scored.map { it.id to (feedOf[it.id] ?: -1L) })
    }

    /**
     * 把有序 id 还原成文章列表（顺序以 [ids] 为准）。
     * SQL 的 `IN` 不保证顺序，这里显式按 id 序还原——否则打散白做。
     */
    suspend fun loadOrdered(ids: List<Long>): List<ArticleWithFeed> = withContext(ioDispatcher) {
        if (ids.isEmpty()) return@withContext emptyList()
        val byId = database.articleDao().loadByIds(ids).associateBy { it.article.id }
        ids.mapNotNull { byId[it] }
    }

    /** 兴趣画像（诊断页只读展示，ADR-0013 可解释性）。 */
    suspend fun profile(now: Long = System.currentTimeMillis()): InterestProfile = withContext(ioDispatcher) {
        val dao = database.articleDao()
        val since = now - WINDOW_DAYS * DAY_MILLIS
        val candidates = dao.loadRecommendationCandidates(since, CANDIDATE_LIMIT).map { it.toCandidate() }
        val samples = dao.loadEngagementSamples(SAMPLE_LIMIT).map { it.toSample() }
        val feedTotals = dao.countByFeedSince(since).associate { it.feedId to it.cnt }
        RecommendationScoring.buildProfile(samples, candidates, feedTotals, now)
    }

    /** 每个订阅源的降权系数（缺条目 = 1.0 不降权）。 */
    suspend fun feedback(): Map<Long, Double> = withContext(ioDispatcher) {
        database.recommendationFeedbackDao().getAll().associate { it.feedId to it.penalty }
    }

    /**
     * 「减少此类」（ADR-0013）：文章所属订阅源在推荐流里降权。
     * 只影响推荐流，不动常规信息流与订阅本身；同一源多点几次逐级降权（有下限）。
     * 返回被降权的 feedId，供调用方做撤销；文章不存在返回 null。
     */
    suspend fun reduceSuch(articleId: Long, now: Long = System.currentTimeMillis()): Long? =
        withContext(ioDispatcher) {
            val feedId = database.articleDao().feedIdOf(articleId) ?: return@withContext null
            val current = database.recommendationFeedbackDao().getAll()
                .firstOrNull { it.feedId == feedId }?.penalty ?: 1.0
            database.recommendationFeedbackDao().upsert(
                RecommendationFeedbackEntity(
                    feedId = feedId,
                    penalty = (current * PENALTY_STEP).coerceAtLeast(MIN_PENALTY),
                    updatedAt = now,
                ),
            )
            feedId
        }

    /** 撤销「减少此类」：降权整条删除（归 1.0）。 */
    suspend fun undoReduce(feedId: Long) = withContext(ioDispatcher) {
        database.recommendationFeedbackDao().clear(feedId)
    }

    companion object {
        /** 候选池时间窗口（天）。已读不进池，更老的文章也不进池。 */
        const val WINDOW_DAYS = 14L

        /** 候选池上限：未读 + 14 天窗，千级已足够，再多是浪费内存。 */
        const val CANDIDATE_LIMIT = 2000

        /** 画像样本上限：最近 500 条有效行为，足够代表近期兴趣。 */
        const val SAMPLE_LIMIT = 500

        /** 每点一次「减少此类」的降权步长。 */
        private const val PENALTY_STEP = 0.6

        /** 降权下限：再不喜欢也留一线（用户还能靠滚动看到）。 */
        private const val MIN_PENALTY = 0.2

        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        private fun ArticleWithFeed.toCandidate(): RecommendationCandidate = RecommendationCandidate(
            id = article.id,
            feedId = article.feedId,
            title = article.title,
            summary = article.summary,
            publishedAt = article.publishedAt,
            fetchedAt = article.fetchedAt,
        )

        private fun EngagementRow.toSample(): EngagementSample = EngagementSample(
            feedId = feedId,
            title = title,
            summary = summary,
            lastOpenedAt = lastOpenedAt,
            starred = isStarred,
            bookmarked = isBookmarked,
        )
    }
}
