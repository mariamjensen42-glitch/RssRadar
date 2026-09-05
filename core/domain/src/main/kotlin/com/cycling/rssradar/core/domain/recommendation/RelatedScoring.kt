package com.cycling.rssradar.core.domain.recommendation

/**
 * 文章关联推荐（AiFeature.RELATED）的打分：纯 JVM，不依赖 Room / Android。
 *
 * 与 [RecommendationScoring] 的「人对文章」打分不同，这里是「文章对文章」：
 * 找与当前文章**内容最相近**的其他文章，跟用户的兴趣画像无关——
 * 用户在读 A，相关阅读该回答的是"还有哪些文章在讲 A 讲的事"，
 * 而不是"你平时喜欢什么"。因此不用画像，只用词面重合。
 *
 * 打分口径与 [RecommendationScoring.topicScore] 同族：IDF 加权的词面覆盖率。
 * IDF 的语料 = 焦点文章 + 全部候选，常见词（"这个""表示"）自然被压低，
 * 不需要停用词表；切词复用同模块的 [Bigrams]（中文 bigram + 拉丁词）。
 *
 * needsLlm = false：纯本地计算，零额度成本，进阅读页即算。
 */
object RelatedScoring {

    /**
     * 给候选打相关分并排序。
     *
     * @param focusTitle / focusSummary 焦点文章的标题与摘要（正文太长不进词袋，
     *   标题 + 摘要的区分度已足够，且与候选池的口径一致）
     * @param candidates 候选池，调用方负责排除焦点文章本身
     * @return 按相关度降序的文章 id（最多 [limit] 条）；分数一并带出供调试与"相似理由"展示
     */
    fun rank(
        focusTitle: String,
        focusSummary: String?,
        candidates: List<RecommendationCandidate>,
        limit: Int,
    ): List<RelatedHit> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()

        // 语料 = 焦点 + 候选；文档数很少时 IDF 区分度有限，但排序方向仍然正确。
        val docs = ArrayList<Set<String>>(candidates.size + 1)
        docs += Bigrams.of(focusTitle, focusSummary).toSet()
        candidates.forEach { docs += Bigrams.of(it.title, it.summary).toSet() }
        val df = HashMap<String, Int>()
        docs.forEach { terms -> terms.forEach { df[it] = (df[it] ?: 0) + 1 } }
        val idf = df.mapValues { kotlin.math.ln(1.0 + docs.size.toDouble() / (1.0 + it.value)) }

        val focusTerms = docs.first()
        if (focusTerms.isEmpty()) return emptyList()
        val focusTotal = focusTerms.sumOf { idf[it] ?: 1.0 }
        if (focusTotal <= 0.0) return emptyList()

        val hits = ArrayList<RelatedHit>(candidates.size)
        candidates.forEachIndexed { index, candidate ->
            val terms = docs[index + 1]
            if (terms.isEmpty()) return@forEachIndexed
            var hit = 0.0
            for (term in terms) {
                if (term in focusTerms) hit += idf[term] ?: 1.0
            }
            val score = (hit / focusTotal).coerceIn(0.0, 1.0)
            if (score > 0.0) hits += RelatedHit(candidate.id, score)
        }
        return hits.sortedWith(compareByDescending<RelatedHit> { it.score }.thenBy { it.articleId }).take(limit)
    }
}

/** 一条相关推荐：文章 id + 相关度（0~1，IDF 加权词面覆盖率）。 */
data class RelatedHit(val articleId: Long, val score: Double)
