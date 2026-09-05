package com.cycling.rssradar.core.data.ai

import com.cycling.rssradar.core.data.db.ArticleDao
import com.cycling.rssradar.core.data.db.AiSupportDao
import com.cycling.rssradar.core.data.db.FeedAiProfileDao
import com.cycling.rssradar.core.data.db.FeedDao
import com.cycling.rssradar.core.data.store.AiFeatureStore
import kotlinx.coroutines.CancellationException


/**
 * 35 项功能的执行入口。
 *
 * 职责边界很清楚：**只负责"给一篇文章（或一批上下文）跑某一项 AI 分析"**，
 * 不管排队、不管调度、不管 UI。队列由 [AiTaskQueue] 管，调度由 Worker 管，
 * 这样同一份执行逻辑既服务手动点击（阅读页按钮）也服务后台批处理。
 *
 * 上下文分两种来源：
 * - [run]：文章级功能，runner 自己从数据库取正文组装（绝大多数功能走这条）。
 * - [runWithContext]：跨文章/统计类功能，上下文要聚合多篇文章或真实统计数字，
 *   由调用方（Worker / ViewModel）组装后传入——runner 不认识画像、不认识抓取日志，
 *   把这些依赖塞进来会让它变成第二个上帝类。
 */
class AiFeatureRunner(
    private val client: DeepSeekClient,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val supportDao: AiSupportDao,
    private val profileDao: FeedAiProfileDao,
    private val artifacts: AiArtifactRepository,
    private val limiter: AiRateLimiter,
    private val featureStore: AiFeatureStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    sealed interface Outcome {
        /** 生成成功并已入库。payload 是模型原文（不是解析后的对象）。 */
        data class Success(val feature: AiFeature, val payload: String) : Outcome

        /** 今日额度用尽。**不是失败**，调用方应把任务留在队列等明天。 */
        data object OutOfBudget : Outcome

        /** 主动跳过：功能未开启、或这项功能压根不调模型。不需要重试。 */
        data class Skipped(val reason: String) : Outcome

        /** 生成失败：可直接展示给用户的中文原因。 */
        data class Failed(val message: String) : Outcome
    }

    /**
     * 跑一项功能。
     *
     * @param targetId 文章 id / 订阅源 id / 全局任务传 0，语义由 [AiFeature.scope] 决定。
     * @param question 问答与划词解释的附加输入。
     */
    suspend fun run(
        feature: AiFeature,
        targetId: Long,
        question: String? = null,
    ): Outcome {
        if (!feature.needsLlm) return Outcome.Skipped("${feature.label}不调用模型")
        if (!featureStore.isEnabled(feature)) return Outcome.Skipped("${feature.label}未开启")

        val context = when (feature) {
            AiFeature.FULLTEXT -> fulltextContext(targetId)
            in CROSS_ARTICLE_FEATURES -> return Outcome.Skipped("${feature.label}需要调用方提供多篇文章上下文")
            in STATS_FEATURES -> return Outcome.Skipped("${feature.label}需要调用方提供统计上下文")
            else -> articleContext(targetId, question)
        } ?: return Outcome.Failed("没有可用于分析的内容")

        return execute(feature, targetId, context)
    }

    /** 调用方自备上下文（跨文章、统计类功能走这条）。 */
    suspend fun runWithContext(
        feature: AiFeature,
        targetId: Long,
        context: AiPromptContext,
    ): Outcome {
        if (!feature.needsLlm) return Outcome.Skipped("${feature.label}不调用模型")
        if (!featureStore.isEnabled(feature)) return Outcome.Skipped("${feature.label}未开启")
        return execute(feature, targetId, context)
    }

    // ── 执行主体 ────────────────────────────────────────────────────────────

    private suspend fun execute(
        feature: AiFeature,
        subjectId: Long,
        context: AiPromptContext,
    ): Outcome {
        val overrides = AiPromptOverrides(summaryPrompt = summaryPromptFor(feature, subjectId))
        val prompt = AiFeatureSpecs.buildPrompt(feature, context, overrides)
            ?: return Outcome.Skipped("${feature.label}不调用模型")

        val raw = try {
            when (val result = limiter.withPermit {
                client.chat(prompt.system, prompt.user, prompt.temperature)
            }) {
                is AiCallResult.Ok -> result.value
                AiCallResult.OutOfBudget -> {
                    // 没发起就不记账：额度已经满了，再记一次会让"已用"超过"上限"，看着像 bug。
                    return Outcome.OutOfBudget
                }
            }
        } catch (e: AiException) {
            // 失败的调用同样占额度——否则一个反复失败的任务能把当天额度无限次烧穿。
            limiter.record(prompt.user.length, 0, success = false)
            return Outcome.Failed(e.userMessage)
        } catch (_: Exception) {
            limiter.record(prompt.user.length, 0, success = false)
            return Outcome.Failed("网络失败，请检查网络后重试")
        }

        // 模型已经返回了内容，但"解析 → 收口 → 记账 → 存库"这段任何一步都可能抛
        // （Room 写入失败、序列化异常……）。这段没有保护的话，异常会一路冒到调用方的
        // launch 块，把 loading 状态永远留在那——表现就是按钮一直转圈。
        return try {
            val parsed = AiFeatureSpecs.parse(feature, raw)
            if (!AiFeatureSpecs.isMeaningful(feature, parsed)) {
                limiter.record(prompt.user.length, raw.length, success = false)
                return Outcome.Failed("AI 没有返回有效结果，请重试")
            }

            // 涉及文章 id 的产物要按本次真实候选集收口，防止模型给出列表里不存在的 id。
            val payload = AiFeatureSpecs.restrictIds(feature, raw, context.companions.map { it.id }.toSet())
            limiter.record(prompt.user.length, payload.length, success = true)
            artifacts.save(
                feature = feature,
                subjectId = subjectId,
                payload = payload,
                model = DeepSeekClient.MODEL,
                inputChars = prompt.user.length,
                outputChars = payload.length,
                now = clock(),
            )
            Outcome.Success(feature, payload)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.Failed("结果处理失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    // ── 上下文组装 ──────────────────────────────────────────────────────────

    /** 单篇文章：优先正文纯文本，退回摘要，两者皆无则拒绝（不拿标题编故事）。 */
    private suspend fun articleContext(articleId: Long, question: String?): AiPromptContext? {
        val row = articleDao.getWithFeed(articleId) ?: return null
        val article = row.article
        val source = article.contentText?.takeIf { it.isNotBlank() }
            ?: article.summary?.takeIf { it.isNotBlank() }
            ?: return null
        val (body, truncated) = AiText.truncateForPrompt(source)
        return AiPromptContext(
            title = article.title,
            feedTitle = row.feedTitle,
            author = article.author,
            body = body,
            truncated = truncated,
            question = question,
        )
    }

    /** 全文提取喂的是原始 HTML——要的就是标签结构，用纯文本反而把结构信息丢了。 */
    private suspend fun fulltextContext(articleId: Long): AiPromptContext? {
        val row = articleDao.getWithFeed(articleId) ?: return null
        val html = row.article.content?.takeIf { it.isNotBlank() }
            ?: row.article.summary?.takeIf { it.isNotBlank() }
            ?: return null
        val (body, truncated) = AiText.truncateForPrompt(html)
        return AiPromptContext(
            title = row.article.title,
            feedTitle = row.feedTitle,
            body = body,
            truncated = truncated,
        )
    }

    /**
     * 「为每个订阅源配置不同的摘要提示词」的落点：
     * 只有摘要功能读订阅源级覆盖，其余功能一律用内置模板。
     */
    private suspend fun summaryPromptFor(feature: AiFeature, subjectId: Long): String? {
        if (feature != AiFeature.SUMMARY) return null
        if (feature.scope != AiScope.ARTICLE) return null
        val feedId = articleDao.feedIdOf(subjectId) ?: return null
        return profileDao.get(feedId)?.summaryPrompt?.takeIf { it.isNotBlank() }
    }

    /**
     * 取一批文章的要点，供调用方组装跨文章上下文。
     *
     * 单独暴露出来是因为组 companion 列表这活儿在 Worker 与 ViewModel 里都要干，
     * 各写一遍就会出现两处不同的 limit 与排序，批处理结果对不上手动结果。
     */
    suspend fun briefsOf(articleIds: List<Long>): List<AiPromptCompanion> {
        if (articleIds.isEmpty()) return emptyList()
        val rows = supportDao.briefsOf(articleIds)
        if (rows.isEmpty()) return emptyList()
        // 一次查全部订阅源换标题，而不是逐行查——几十条 companion 逐行查库比查询本身慢得多。
        val feedTitles = feedDao.getAll().associate { it.id to it.title }
        return rows.map {
            AiPromptCompanion(
                id = it.id,
                title = it.title,
                feedTitle = feedTitles[it.feedId].orEmpty(),
            )
        }
    }

    /**
     * 取文章正文（已截断），供跨文章功能组装上下文。
     *
     * 单独暴露是因为"取正文并截断"这件事在批处理与手动触发里必须完全一致——
     * 若一边截断一边不截断，同一篇文章的批处理结果与手动结果会对不上，
     * 而这类不一致几乎无法复现排查。
     */
    suspend fun bodyOf(articleId: Long): String? {
        val article = articleDao.getWithFeed(articleId)?.article ?: return null
        val source = article.contentText?.takeIf { it.isNotBlank() }
            ?: article.summary?.takeIf { it.isNotBlank() }
            ?: return null
        return AiText.truncateForPrompt(source).first
    }

    /** 按订阅源取该源的 AI 配置（已合并全局开关）。 */
    suspend fun profileOf(feedId: Long): FeedAiProfile =
        FeedAiProfile.resolve(profileDao.get(feedId), featureStore.state.value)

    /** 订阅源标题，健康监控等按源功能组 prompt 用。 */
    suspend fun feedTitleOf(feedId: Long): String = feedDao.getById(feedId)?.title.orEmpty()

    companion object {
        /** 需要多篇文章作为输入的功能——上下文必须由调用方组装。 */
        private val CROSS_ARTICLE_FEATURES = setOf(
            AiFeature.DEDUPE,
            AiFeature.AGGREGATE,
            AiFeature.EVENT_MERGE,
            AiFeature.DAILY_BRIEF,
            AiFeature.DISCOVER,
        )

        /** 需要真实统计数字作为输入的功能——数字必须来自数据库，模型不参与统计。 */
        private val STATS_FEATURES = setOf(
            AiFeature.HABIT,
            AiFeature.DAILY_REPORT,
            AiFeature.FEED_HEALTH,
            AiFeature.INTEREST_RANK,
            AiFeature.BUBBLE_BREAK,
            AiFeature.FEED_RECOMMEND,
            AiFeature.COLD_START,
        )
    }
}
