package com.cycling.rssradar.core.data.ai

import com.cycling.rssradar.core.data.db.AiArtifactDao
import com.cycling.rssradar.core.data.db.AiArtifactEntity
import com.cycling.rssradar.core.data.db.AiSupportDao
import com.cycling.rssradar.core.data.db.FeedDao
import com.cycling.rssradar.core.data.store.AiFeatureSettings


/**
 * 订阅源级 AI 配置的**解析结果**：三态（覆盖/跟随）已经在这里合并完毕。
 *
 * 数据库里存的是「这个值有没有被单独设置」（可空布尔），UI 与执行器要的是「最终该不该做」。
 * 把合并逻辑放在这里而不是散落各处，是为了让"没配过 = 跟随全局"这条语义只有一处实现——
 * 曾经在三个地方各写一遍 `?: 全局值`，漏一处就是一个"关不掉"的 bug。
 */
data class FeedAiProfile(
    /** 覆盖全局的摘要提示词，null = 用内置模板。 */
    val summaryPrompt: String? = null,
    val autoSummary: Boolean = false,
    val autoTags: Boolean = false,
    val autoClassify: Boolean = false,
    val autoScore: Boolean = false,
    val watchHealth: Boolean = false,
    val priority: Int = 0,
) {
    companion object {
        /**
         * 合并：未配置的项跟随全局开关。
         *
         * 注意 `autoScore` 同时受「文章质量分析」和「智能降噪」两个全局开关影响——
         * 用户在设置页开任一项，该源若未单独配置就跟着跑。
         */
        fun resolve(
            entity: com.cycling.rssradar.core.data.db.FeedAiProfileEntity?,
            global: AiFeatureSettings,
        ): FeedAiProfile {
            if (entity == null) {
                return FeedAiProfile(
                    autoSummary = global.isEnabled(AiFeature.SUMMARY),
                    autoTags = global.isEnabled(AiFeature.TAGS),
                    autoClassify = global.isEnabled(AiFeature.CLASSIFY),
                    autoScore = global.isEnabled(AiFeature.QUALITY) || global.isEnabled(AiFeature.NOISE),
                    watchHealth = global.isEnabled(AiFeature.FEED_HEALTH),
                )
            }
            return FeedAiProfile(
                summaryPrompt = entity.summaryPrompt?.takeIf { it.isNotBlank() },
                autoSummary = entity.autoSummary ?: global.isEnabled(AiFeature.SUMMARY),
                autoTags = entity.autoTags ?: global.isEnabled(AiFeature.TAGS),
                autoClassify = entity.autoClassify ?: global.isEnabled(AiFeature.CLASSIFY),
                autoScore = entity.autoScore
                    ?: (global.isEnabled(AiFeature.QUALITY) || global.isEnabled(AiFeature.NOISE)),
                watchHealth = entity.watchHealth ?: global.isEnabled(AiFeature.FEED_HEALTH),
                priority = entity.priority.coerceIn(0, 100),
            )
        }

        /** 没有配置行时的默认值（列表渲染与测试用）。 */
        fun defaultOf(global: AiFeatureSettings): FeedAiProfile = resolve(null, global)
    }
}


/**
 * 产物中心的一行产物。
 *
 * 与 [AiArtifactEntity] 的区别只在 [subjectTitle]：数据库里只有 subjectId，
 * 而用户看不懂一串数字。标题由仓储层按 scope 一次性批量补全，
 * 不让 UI 侧为了显示一个标题去逐个查库。
 */
data class AiArtifactItem(
    val feature: AiFeature,
    val scope: AiScope,
    val subjectId: Long,
    /** 文章标题 / 订阅源名；全局产物为 null（UI 改用生成日期区分）。 */
    val subjectTitle: String?,
    val payload: String,
    val model: String,
    val inputChars: Int,
    val outputChars: Int,
    val createdAt: Long,
)


/** 产物中心的按功能聚合：某项功能产出了多少、最近一次是什么时候。 */
data class AiArtifactGroup(
    val feature: AiFeature,
    val total: Int,
    val latestAt: Long,
    val outputChars: Long,
)


/**
 * AI 产物的读写门面。
 *
 * **存模型原文而不是解析后的规范 JSON**：模型输出是唯一的原始证据，
 * 一旦存成经过清洗的形态，将来想排查"模型到底说了什么"就没有了。
 * 清洗在读取时由 [AiParsers] 做，改清洗规则不用重跑模型（重跑要花钱）。
 *
 * scope 由 [AiFeature.scope] 推出，调用方不用再传一遍——少一个参数就少一处传错的可能。
 */
class AiArtifactRepository(
    private val dao: AiArtifactDao,
    /** 产物中心要把订阅源 id 翻成源名。 */
    private val feedDao: FeedDao,
    /** 产物中心要把文章 id 翻成标题。 */
    private val supportDao: AiSupportDao,
) {

    suspend fun rawOf(feature: AiFeature, subjectId: Long): String? =
        dao.of(feature.scope.dbValue, subjectId, feature.dbValue)?.payload

    /** 读并解析。解析失败返回 null（调用方按"尚未生成"处理，触发一次生成）。 */
    suspend fun <T> read(feature: AiFeature, subjectId: Long, decode: (String) -> T): T? {
        val raw = rawOf(feature, subjectId) ?: return null
        return runCatching { decode(raw) }.getOrNull()
    }

    suspend fun exists(feature: AiFeature, subjectId: Long): Boolean =
        rawOf(feature, subjectId) != null

    suspend fun save(
        feature: AiFeature,
        subjectId: Long,
        payload: String,
        model: String,
        inputChars: Int,
        outputChars: Int,
        now: Long = System.currentTimeMillis(),
    ) {
        dao.upsert(
            AiArtifactEntity(
                subjectKind = feature.scope.dbValue,
                subjectId = subjectId,
                kind = feature.dbValue,
                payload = payload,
                model = model,
                inputChars = inputChars,
                outputChars = outputChars,
                createdAt = now,
            ),
        )
    }

    suspend fun delete(feature: AiFeature, subjectId: Long) =
        dao.delete(feature.scope.dbValue, subjectId, feature.dbValue)

    /** 关掉某项功能时清掉它的全部产物——"关了却还显示 AI 结果"最难解释。 */
    suspend fun clearFeature(feature: AiFeature) = dao.deleteKind(feature.dbValue)

    /** 文章被删除前清掉它身上的全部产物（ai_artifacts 没加外键，这一步由调用方负责）。 */
    suspend fun clearSubject(scope: AiScope, subjectId: Long) = dao.deleteSubject(scope.dbValue, subjectId)

    /** 批量取：列表页渲染一批文章的标签/情感角标，避免逐篇查库。 */
    suspend fun rawMapFor(features: List<AiFeature>, articleIds: List<Long>): Map<Long, Map<Int, String>> {
        if (articleIds.isEmpty() || features.isEmpty()) return emptyMap()
        val wanted = features.map { it.dbValue }.toSet()
        val rows = dao.ofArticles(articleIds)
        val result = HashMap<Long, MutableMap<Int, String>>(articleIds.size)
        rows.forEach { row ->
            if (row.subjectKind == AiScope.ARTICLE.dbValue && row.kind in wanted) {
                result.getOrPut(row.subjectId) { HashMap() }[row.kind] = row.payload
            }
        }
        return result
    }

    /** 这批文章已有哪些产物，供排程去重：`kind:articleId` 集合。 */
    suspend fun existingKeys(articleIds: List<Long>): Set<String> {
        if (articleIds.isEmpty()) return emptySet()
        return dao.ofArticles(articleIds)
            .filter { it.subjectKind == AiScope.ARTICLE.dbValue }
            .mapTo(HashSet()) { "${it.kind}:${it.subjectId}" }
    }

    suspend fun countOf(feature: AiFeature): Int = dao.countOfKind(feature.dbValue)

    /** 全局产物（简报/报告）保留期滚动清理。 */
    suspend fun pruneGlobal(keepAfter: Long) = dao.deleteBefore(AiScope.GLOBAL.dbValue, keepAfter)

    /** 清理文章/订阅源被删除后残留的孤儿产物。 */
    suspend fun pruneOrphans() = dao.deleteOrphans()

    /** 全局类产物（每日简报、阅读报告）的固定 subjectId。 */
    fun globalSubjectId(): Long = AiArtifactEntity.GLOBAL_SUBJECT_ID

    // ── 产物中心 ─────────────────────────────────────────────────────────
    // 这一组方法的存在理由：35 项功能里只有一部分有专属 UI，其余功能跑完就落进
    // ai_artifacts，用户无从判断"到底跑了没跑、结果是什么"。产物中心按功能把全部
    // 产物摊开，让每一项功能至少有一个能看见结果的地方。

    /**
     * 按功能聚合的概览，按最近生成时间倒序。
     *
     * 只列**真的产出过**的功能——把 35 项全列出来、其中 20 项是空的，
     * 用户扫一眼只会得到"一半功能是坏的"这个错误印象。没产出的功能，
     * 用户该去看功能开关与任务队列，而不是来产物中心数空行。
     */
    suspend fun overview(): List<AiArtifactGroup> =
        dao.kindOverview().mapNotNull { row ->
            val feature = AiFeature.fromDbValue(row.kind) ?: return@mapNotNull null
            AiArtifactGroup(
                feature = feature,
                total = row.total,
                latestAt = row.latestAt,
                outputChars = row.outputChars,
            )
        }

    /**
     * 最近产物列表，按生成时间倒序。
     *
     * @param kind 限定某个 [AiFeature.dbValue]；null = 全部功能。
     * @param limit 上限。产物表能到数万行，必须有上限，否则"看看结果"的页面自己会先 OOM。
     */
    suspend fun browse(kind: Int? = null, limit: Int = BROWSE_LIMIT): List<AiArtifactItem> {
        val rows = if (kind == null) dao.recentAll(limit) else dao.recentOfKindAll(kind, limit)
        val titles = resolveTitles(rows)
        return rows.mapNotNull { row ->
            val feature = AiFeature.fromDbValue(row.kind) ?: return@mapNotNull null
            val scope = AiScope.fromDbValue(row.subjectKind) ?: return@mapNotNull null
            AiArtifactItem(
                feature = feature,
                scope = scope,
                subjectId = row.subjectId,
                subjectTitle = titles["${row.subjectKind}:${row.subjectId}"],
                payload = row.payload,
                model = row.model,
                inputChars = row.inputChars,
                outputChars = row.outputChars,
                createdAt = row.createdAt,
            )
        }
    }

    /**
     * 把 subjectId 批量翻成标题，返回 `subjectKind:subjectId → 标题`。
     *
     * 批量而不是逐条：一个功能动辄几十上百条产物，逐条查库在这个页面上
     * 会变成几十上百次查询，滑动时肉眼可见地卡。
     */
    private suspend fun resolveTitles(rows: List<AiArtifactEntity>): Map<String, String> {
        val articleIds = rows.filter { it.subjectKind == AiScope.ARTICLE.dbValue }.map { it.subjectId }
        val feedIds = rows.filter { it.subjectKind == AiScope.FEED.dbValue }.map { it.subjectId }
        val out = HashMap<String, String>(articleIds.size + feedIds.size)
        if (articleIds.isNotEmpty()) {
            supportDao.briefsOf(articleIds).forEach { brief ->
                out["${AiScope.ARTICLE.dbValue}:${brief.id}"] = brief.title
            }
        }
        if (feedIds.isNotEmpty()) {
            val wanted = feedIds.toSet()
            feedDao.getAll().forEach { feed ->
                if (feed.id in wanted) out["${AiScope.FEED.dbValue}:${feed.id}"] = feed.title
            }
        }
        return out
    }

    companion object {
        /** 产物中心一次最多取多少条。够看很久，又不会把几万行读进内存。 */
        const val BROWSE_LIMIT = 300
    }
}
