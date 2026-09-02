package com.cycling.rssradar.data

import kotlin.math.ln
import kotlin.math.pow

/**
 * 推荐打分（ADR-0013）：纯 JVM 实现，不依赖 Room / Android，可直接单测。
 *
 * 三个分量，全部可解释：
 * - **新鲜度**：发布时间的指数衰减（半衰期 [FRESHNESS_HALF_LIFE_DAYS] 天）。
 * - **源亲和度**：该源文章的历史打开率（打开次数按 [OPEN_HALF_LIFE_DAYS] 天半衰期衰减，
 *   除以窗口内该源的文章总数，拉普拉斯平滑），归一化到 [0,1]。
 * - **内容亲和度**：从用户真实读过的文章（打开/收藏/稍后读）抽取 bigram 词袋，
 *   用 IDF 加权后的覆盖率给候选打分，落在 [0,1]。
 *
 * 不做真分词（不引分词库）：中文取相邻二字片段，拉丁文按词。标题/摘要粒度上
 * bigram 的区分度足够，且零依赖。
 */

/** 推荐候选（打分侧的纯数据，由 DB 行映射而来）。 */
data class RecommendationCandidate(
    val id: Long,
    val feedId: Long,
    val title: String,
    val summary: String?,
    val publishedAt: Long?,
    val fetchedAt: Long,
)

/** 画像样本：用户真实表达过兴趣的一篇文章。 */
data class EngagementSample(
    val feedId: Long,
    val title: String,
    val summary: String?,
    val lastOpenedAt: Long?,
    val starred: Boolean,
    val bookmarked: Boolean,
)

/** 三个分量的权重（内置，不暴露给用户调）。 */
data class RecommendationWeights(
    val freshness: Double = 1.0,
    val source: Double = 1.2,
    val topic: Double = 1.0,
)

/** 单篇的打分明细：诊断与单测都能看到"为什么是它"。 */
data class ArticleScore(
    val id: Long,
    val total: Double,
    val freshness: Double,
    val source: Double,
    val topic: Double,
)

/** 画像里的一个兴趣词。 */
data class ProfileTerm(val term: String, val weight: Double)

/**
 * 兴趣画像：top bigram 词袋 + 每个源的亲和度。
 * 只由真实行为驱动，不预置任何兴趣类别。
 */
data class InterestProfile(
    val terms: List<ProfileTerm>,
    val feedAffinity: Map<Long, Double>,
) {
    /** 画像是否有效（学过东西）。空画像走冷启动退化路径。 */
    val isEmpty: Boolean get() = terms.isEmpty() && feedAffinity.isEmpty()
}

object RecommendationScoring {

    /** 兴趣词袋容量（ADR-0013：top 200，控制内存与噪声）。 */
    const val TERM_LIMIT = 200

    /** 打开行为的时间衰减半衰期（天）。 */
    const val OPEN_HALF_LIFE_DAYS = 14.0

    /** 新鲜度衰减半衰期（天）。 */
    const val FRESHNESS_HALF_LIFE_DAYS = 3.0

    /** 多样性：滑动窗口大小。 */
    const val DIVERSITY_WINDOW = 20

    /** 多样性：同一订阅源在一个窗口内最多出现几条。 */
    const val MAX_PER_FEED = 2

    /** 只收藏/稍后读但从未打开过的样本，按这个固定权重参与画像（低于真实打开）。 */
    private const val NEVER_OPENED_WEIGHT = 0.4

    /** 打开率的分母兜底：窗口内该源文章数为 0 时不至于除零。 */
    private const val AFFINITY_SMOOTHING = 0.5

    private const val DAY_MILLIS = 24 * 60 * 60 * 1000.0

    /**
     * 建画像：[samples] 是用户表达过兴趣的文章，[candidates] 同时充当 IDF 的语料，
     * [feedTotals] 是窗口内每个源的文章总数（源亲和度的分母）。
     */
    fun buildProfile(
        samples: List<EngagementSample>,
        candidates: List<RecommendationCandidate>,
        feedTotals: Map<Long, Int>,
        now: Long,
    ): InterestProfile {
        val idf = idf(candidates, samples)
        val terms = HashMap<String, Double>()
        val opens = HashMap<Long, Double>()

        for (sample in samples) {
            val weight = sampleWeight(sample, now)
            if (weight <= 0.0) continue
            if (sample.lastOpenedAt != null) {
                opens[sample.feedId] = (opens[sample.feedId] ?: 0.0) + recency(sample.lastOpenedAt, now)
            }
            for (term in Bigrams.of(sample.title, sample.summary).toSet()) {
                terms[term] = (terms[term] ?: 0.0) + weight * (idf[term] ?: 1.0)
            }
        }

        val topTerms = terms.entries
            .sortedByDescending { it.value }
            .take(TERM_LIMIT)
            .let { entries ->
                val max = entries.firstOrNull()?.value ?: 0.0
                if (max <= 0.0) emptyList() else entries.map { ProfileTerm(it.key, it.value / max) }
            }
        return InterestProfile(terms = topTerms, feedAffinity = feedAffinity(opens, feedTotals))
    }

    /** 打开率 → 亲和度：拉普拉斯平滑后按最大值归一化到 [0,1]（全 0 时返回空表）。 */
    private fun feedAffinity(opens: Map<Long, Double>, feedTotals: Map<Long, Int>): Map<Long, Double> {
        if (opens.isEmpty()) return emptyMap()
        val raw = HashMap<Long, Double>()
        for ((feedId, opened) in opens) {
            val total = feedTotals[feedId] ?: 0
            raw[feedId] = (opened + AFFINITY_SMOOTHING) / (total + 1.0)
        }
        val max = raw.values.maxOrNull() ?: return emptyMap()
        if (max <= 0.0) return emptyMap()
        return raw.mapValues { (it.value / max).coerceIn(0.0, 1.0) }
    }

    /**
     * 打分并排序（不分页）：返回按 total 降序的 [ArticleScore] 明细。
     * [penalties] 是「减少此类」的降权系数（缺条目 = 1.0 不降权）。
     */
    fun score(
        candidates: List<RecommendationCandidate>,
        profile: InterestProfile,
        penalties: Map<Long, Double> = emptyMap(),
        now: Long,
        weights: RecommendationWeights = RecommendationWeights(),
    ): List<ArticleScore> {
        if (candidates.isEmpty()) return emptyList()
        val idf = idf(candidates, emptyList())
        val termWeights = profile.terms.associate { it.term to it.weight }

        val scored = ArrayList<ArticleScore>(candidates.size)
        for (candidate in candidates) {
            val base = candidate.publishedAt ?: candidate.fetchedAt
            val ageDays = ((now - base).coerceAtLeast(0)).toDouble() / DAY_MILLIS
            val freshness = 0.5.pow(ageDays / FRESHNESS_HALF_LIFE_DAYS)
            val source = profile.feedAffinity[candidate.feedId] ?: 0.0
            val topic = topicScore(candidate, termWeights, idf)
            val penalty = penalties[candidate.feedId] ?: 1.0
            val total = (weights.freshness * freshness + weights.source * source + weights.topic * topic) * penalty
            scored += ArticleScore(
                id = candidate.id,
                total = total,
                freshness = freshness,
                source = source,
                topic = topic,
            )
        }
        return scored.sortedWith(compareByDescending<ArticleScore> { it.total }.thenBy { it.id })
    }

    /**
     * 内容亲和度 = 候选词命中的画像权重（按 IDF 加权）占候选全部词 IDF 之和的比例。
     * 天然落在 [0,1]："你读过的词在这篇里占多大比重"，不依赖跨候选比较，结果稳定。
     */
    private fun topicScore(
        candidate: RecommendationCandidate,
        termWeights: Map<String, Double>,
        idf: Map<String, Double>,
    ): Double {
        if (termWeights.isEmpty()) return 0.0
        // 标题算两遍：标题的词比摘要更能代表这篇文章在讲什么
        val terms = Bigrams.of(candidate.title, candidate.title, candidate.summary).toSet()
        if (terms.isEmpty()) return 0.0
        var hit = 0.0
        var all = 0.0
        for (term in terms) {
            val weight = idf[term] ?: 1.0
            all += weight
            hit += weight * (termWeights[term] ?: 0.0)
        }
        return if (all <= 0.0) 0.0 else (hit / all).coerceIn(0.0, 1.0)
    }

    /**
     * 冷启动退化（ADR-0013）：画像为空时，按订阅源分组轮转取最近未读。
     * 退化结果本身有用（等于按源均衡的未读流），所以推荐 tab 永远有内容。
     */
    fun coldStartRank(candidates: List<RecommendationCandidate>): List<Long> {
        if (candidates.isEmpty()) return emptyList()
        val byFeed = LinkedHashMap<Long, ArrayDeque<RecommendationCandidate>>()
        // 候选已按时间倒序，直接按 feed 分桶即可
        candidates.forEach { byFeed.getOrPut(it.feedId) { ArrayDeque() }.addLast(it) }
        val ranked = ArrayList<Long>(candidates.size)
        while (ranked.size < candidates.size) {
            var progressed = false
            for (queue in byFeed.values) {
                if (queue.isEmpty()) continue
                ranked += queue.removeFirst().id
                progressed = true
            }
            if (!progressed) break
        }
        return ranked
    }

    /**
     * 多样性打散：滑动窗口内同一订阅源最多 [maxPerFeed] 条。
     * 高产源（聚合类）否则会刷屏。窗口外的计数会随窗口滑动释放，因此不会永久压制某个源。
     */
    fun diversify(
        ordered: List<Pair<Long, Long>>,
        window: Int = DIVERSITY_WINDOW,
        maxPerFeed: Int = MAX_PER_FEED,
    ): List<Long> {
        val result = ArrayList<Long>(ordered.size)
        val windowQueue = ArrayDeque<Long>()
        val counts = HashMap<Long, Int>()
        val pending = ArrayDeque(ordered)
        var deferredInPass = 0
        while (pending.isNotEmpty()) {
            val (id, feedId) = pending.removeFirst()
            val used = counts[feedId] ?: 0
            // 窗口里全是超限的源时强制放行（保底：不多不少地把候选排完）
            if (used < maxPerFeed || deferredInPass >= pending.size) {
                result += id
                windowQueue += feedId
                counts[feedId] = used + 1
                deferredInPass = 0
                if (windowQueue.size > window) {
                    val evicted = windowQueue.removeFirst()
                    counts[evicted] = (counts[evicted] ?: 0) - 1
                }
            } else {
                pending.addLast(id to feedId)
                deferredInPass++
            }
        }
        return result
    }

    /** 样本权重：真实打开按时间衰减，只收藏/稍后读没打开过的按固定低权重。 */
    private fun sampleWeight(sample: EngagementSample, now: Long): Double {
        val openedAt = sample.lastOpenedAt
        val recency = if (openedAt != null) recency(openedAt, now) else NEVER_OPENED_WEIGHT
        val engagement = 1.0 + (if (sample.starred) 0.8 else 0.0) + (if (sample.bookmarked) 0.5 else 0.0)
        return recency * engagement
    }

    private fun recency(openedAt: Long, now: Long): Double {
        val ageDays = ((now - openedAt).coerceAtLeast(0)).toDouble() / DAY_MILLIS
        return 0.5.pow(ageDays / OPEN_HALF_LIFE_DAYS)
    }

    /**
     * IDF：语料 = 候选 + 样本（都只在内存里，不落库）。
     * 常见词（"这个""一篇"）IDF 低，自然被压下去，不需要停用词表。
     */
    private fun idf(
        candidates: List<RecommendationCandidate>,
        samples: List<EngagementSample>,
    ): Map<String, Double> {
        val df = HashMap<String, Int>()
        var docs = 0
        fun count(terms: Set<String>) {
            docs++
            terms.forEach { df[it] = (df[it] ?: 0) + 1 }
        }
        candidates.forEach { count(Bigrams.of(it.title, it.summary).toSet()) }
        samples.forEach { count(Bigrams.of(it.title, it.summary).toSet()) }
        if (docs == 0) return emptyMap()
        return df.mapValues { ln(1.0 + docs.toDouble() / (1.0 + it.value)) }
    }
}

/**
 * 中文 bigram + 拉丁词：不引分词库的最小可用切分。
 * 中文取相邻二字片段（单字也保留，否则单字标题无词可用）；
 * 拉丁文/数字按词，长度 ≥ 2 且含字母才算（纯数字串噪声大）。
 */
internal object Bigrams {

    fun of(vararg texts: String?): List<String> {
        val out = ArrayList<String>()
        for (text in texts) {
            if (text.isNullOrBlank()) continue
            appendTo(out, text)
        }
        return out
    }

    private fun appendTo(out: ArrayList<String>, text: String) {
        val cjk = StringBuilder()
        val word = StringBuilder()

        fun flushCjk() {
            when {
                cjk.length >= 2 -> for (i in 0 until cjk.length - 1) out += cjk.substring(i, i + 2)
                cjk.length == 1 -> out += cjk.toString()
            }
            cjk.setLength(0)
        }

        fun flushWord() {
            if (word.length >= 2 && word.any { it.isLetter() }) out += word.toString()
            word.setLength(0)
        }

        for (char in text) {
            when {
                isCjk(char) -> {
                    flushWord()
                    cjk.append(char)
                }
                char.isLetterOrDigit() -> {
                    flushCjk()
                    word.append(char.lowercaseChar())
                }
                else -> {
                    flushCjk()
                    flushWord()
                }
            }
        }
        flushCjk()
        flushWord()
    }

    /** CJK 统一表意文字及扩展区（含中日韩兼容与扩展 A）。 */
    private fun isCjk(char: Char): Boolean {
        val code = char.code
        return code in 0x3400..0x4DBF ||   // 扩展 A
            code in 0x4E00..0x9FFF ||      // 主区
            code in 0xF900..0xFAFF ||      // 兼容表意文字
            code in 0x20000..0x2A6DF       // 扩展 B
    }
}
