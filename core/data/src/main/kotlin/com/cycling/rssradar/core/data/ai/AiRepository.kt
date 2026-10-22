package com.cycling.rssradar.core.data.ai

import com.cycling.rssradar.core.model.AiFeature
import com.cycling.rssradar.core.data.db.dao.ArticleDao
import com.cycling.rssradar.core.data.store.prefs.AiFeatureStore
import kotlinx.coroutines.CancellationException

/** 译文缓存上限（篇）。 */
private const val TRANSLATION_CACHE_MAX = 20

/** AI 摘要入库长度上限（字符）。列表流会带 aiSummary，防异常输出撑爆 CursorWindow 单行。 */
private const val AI_SUMMARY_MAX_LENGTH = 2_000

/** 译文会话缓存条目：整篇的分段译文，与分段切分结果按序对齐。 */
private data class TranslationCacheEntry(
    val originalsHash: Int,
    val translated: List<String>,
)

/**
 * 渐进式翻译的进度快照（翻译功能 v2）：
 * [chunks] 恒定（两级切分一次成型，块边界随分段带到渲染侧）；[translated] 与之按序对齐，
 * 元素为 null 表示该段尚未翻完。完成段数 = translated.count { it != null }。
 */
data class TranslationProgress(
    val chunks: List<TranslationChunk>,
    val translated: List<String?>,
) {
    /** 各分段的原文（API 往返单位）。 */
    val originals: List<String> get() = chunks.map { it.html }

    val doneCount: Int get() = translated.count { it != null }
    val total: Int get() = chunks.size
}

/**
 * AI 能力的领域门面（issue #44）：
 * - AI 摘要：基于正文生成，持久化（articles.aiSummary），刷新永不覆盖；
 * - AI 翻译：按 [TranslationSegments] 分段逐段译为简体中文，不落盘，
 *   会话内整篇缓存；经 [onProgress] 回调实现渐进显示（翻译功能 v2）。
 * 语言预检 / 截断 / 响应解析都在 [AiText] 纯函数层。
 */
class AiRepository(
    private val articleDao: ArticleDao,
    private val client: DeepSeekClient,
    private val limiter: AiRateLimiter,
    private val featureStore: AiFeatureStore,
) {

    /**
     * 译文会话级缓存：key = articleId，进程内有效，不落盘。
     * LRU 上限 20 篇（OOM 防线）：每条译文是整篇正文的分段集合，无上限的 Map 会
     * 随翻译篇数线性吃堆。按访问序淘汰最旧的。originalsHash 防正文变化后错位恢复。
     */
    private val translationCache = object : LinkedHashMap<Long, TranslationCacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, TranslationCacheEntry>): Boolean =
            size > TRANSLATION_CACHE_MAX
    }

    fun clearTranslationCache(articleId: Long) {
        translationCache.remove(articleId)
    }

    sealed interface SummaryOutcome {
        data class Success(val summary: String) : SummaryOutcome
        data class Failure(val userMessage: String) : SummaryOutcome

        /** 今日额度用尽。**不是失败**：不重试，等明天。 */
        data object OutOfBudget : SummaryOutcome
    }

    sealed interface TranslationOutcome {
        /** 全部分段完成（含缓存秒出）。分段内容经 onProgress 交付，这里只报状态。 */
        data object Success : TranslationOutcome
        data object AlreadyChinese : TranslationOutcome
        data class Failure(val userMessage: String) : TranslationOutcome

        /** 今日额度用尽（翻译中途耗尽也算）：已完成段落已上屏，但不入缓存。 */
        data object OutOfBudget : TranslationOutcome
    }

    /**
     * 走闸调用模型：**预算 / 最小间隔 / 记账**三件事收在这一处，与 [AiFeatureRunner] 同源。
     *
     * 这个类曾经直接 `client.chat(...)`，于是阅读页的摘要与翻译两条路径完全绕过了
     * [AiRateLimiter]——额度用尽后还能无限调、用量页也看不见这些调用。所有真实付费调用
     * 必须经此方法，不得再直连 [client]。
     *
     * @return 模型原文；null 表示今日额度用尽（未发起请求）。
     */
    private suspend fun chatGuarded(system: String, input: String, temperature: Double?): String? =
        try {
            when (val call = limiter.withPermit { client.chat(system, input, temperature) }) {
                is AiCallResult.Ok -> {
                    limiter.record(input.length, call.value.length, success = true)
                    call.value
                }

                AiCallResult.OutOfBudget -> null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 失败的调用同样占额度——否则反复失败能把当天额度无限次烧穿。
            limiter.record(input.length, 0, success = false)
            throw e
        }

    /**
     * 生成 AI 摘要并入库（阅读页「生成摘要」走这条）。来源优先正文纯文本、退回摘要；
     * 两者皆无则拒绝生成——不编造。
     *
     * 提示词取 [DEFAULT_SUMMARY_SYSTEM]，与 [AiFeatureRunner] 那条自动摘要路径**共用同一份**：
     * 这里原先带一份内容重复的私有副本，改 prompt 时极易只改一处、另一处照旧生效。
     */
    suspend fun generateSummary(articleId: Long): SummaryOutcome {
        if (!featureStore.isEnabled(AiFeature.SUMMARY)) {
            return SummaryOutcome.Failure("「AI 摘要」未开启，可在设置里打开")
        }
        val article = articleDao.getWithFeed(articleId)?.article
            ?: return SummaryOutcome.Failure("文章不存在")
        val source = article.contentText?.takeIf { it.isNotBlank() }
            ?: article.summary?.takeIf { it.isNotBlank() }
            ?: return SummaryOutcome.Failure("本文没有可用于摘要的内容")
        val (input, truncated) = AiText.truncateForPrompt(source)
        return try {
            // 低 temperature 压发散：摘要要忠实原文，不要模型自由发挥
            val text = chatGuarded(DEFAULT_SUMMARY_SYSTEM, input, 0.4)
                ?: return SummaryOutcome.OutOfBudget
            val summary = if (truncated) text + AiText.truncationNote(input.length) else text
            // 入库前截断（CursorWindow 防线）：列表流会连 aiSummary 一起查出，
            // 异常长的模型输出会把单行撑爆 2MB 窗口（数万行查询里一粒老鼠屎坏一锅粥）
            articleDao.updateAiSummary(articleId, summary.take(AI_SUMMARY_MAX_LENGTH))
            SummaryOutcome.Success(summary.take(AI_SUMMARY_MAX_LENGTH))
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiException) {
            SummaryOutcome.Failure(e.userMessage)
        } catch (e: Exception) {
            SummaryOutcome.Failure("网络失败，请检查网络后重试")
        }
    }

    /**
     * 渐进式翻译：正文按 [TranslationSegments] 分段，逐段调 API 译为简体中文，
     * 每完成一段经 [onProgress] 上抛最新快照（缓存命中时首次回调即全量，秒出）。
     *
     * 失败策略：单段输出为空 → 该段回退显示原文，不中断整篇；
     * 网络/Key 等异常 → 中断并返回 Failure（已完成段经 onProgress 已可见，但不入缓存）。
     * 协程取消会自然中断，无副作用。
     */
    suspend fun translate(
        articleId: Long,
        onProgress: (TranslationProgress) -> Unit,
    ): TranslationOutcome {
        if (!featureStore.isEnabled(AiFeature.TRANSLATE)) {
            return TranslationOutcome.Failure("「文章翻译」未开启，可在设置里打开")
        }
        val article = articleDao.getWithFeed(articleId)?.article
            ?: return TranslationOutcome.Failure("文章不存在")
        val html = article.content?.takeIf { it.isNotBlank() }
            ?: article.summary?.takeIf { it.isNotBlank() }
            ?: return TranslationOutcome.Failure("本文没有可翻译的内容")
        if (AiText.isMostlyChinese(article.contentText ?: html)) {
            return TranslationOutcome.AlreadyChinese
        }
        // 两级一次切好：chunk 送 API，块边界随进度带到渲染侧（渲染侧不再二次切分）
        val chunks = TranslationSegments.chunk(html)
        if (chunks.isEmpty()) return TranslationOutcome.Failure("本文没有可翻译的内容")

        // 缓存秒出：同一段序的整篇译文直接回调全量进度
        translationCache[articleId]?.let { entry ->
            if (entry.originalsHash == chunks.hashCode() && entry.translated.size == chunks.size) {
                onProgress(TranslationProgress(chunks, entry.translated))
                return TranslationOutcome.Success
            }
            translationCache.remove(articleId) // 正文变了，旧缓存作废
        }

        val translated = arrayOfNulls<String>(chunks.size)
        onProgress(TranslationProgress(chunks, translated.toList()))
        return try {
            for (i in chunks.indices) {
                val (input, _) = AiText.truncateForPrompt(chunks[i].html)
                val raw = chatGuarded(TRANSLATE_SYSTEM, input, 0.3)
                    ?: return TranslationOutcome.OutOfBudget
                // 单段失败回退原文，不中断整篇
                translated[i] = raw.takeIf { it.isNotBlank() } ?: chunks[i].html
                onProgress(TranslationProgress(chunks, translated.toList()))
            }
            translationCache[articleId] = TranslationCacheEntry(
                originalsHash = chunks.hashCode(),
                translated = translated.map { it.orEmpty() },
            )
            TranslationOutcome.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiException) {
            TranslationOutcome.Failure(e.userMessage)
        } catch (e: Exception) {
            TranslationOutcome.Failure("网络失败，请检查网络后重试")
        }
    }

    companion object {
        // 逐块配对（双语一一对应）的前提：译文块数/块序必须与原文严格一致，
        // 否则 UI 侧按索引配对就会错位。因此把"不合并、不拆分、不增删块"写成硬约束。
        private const val TRANSLATE_SYSTEM =
            "把用户提供的 HTML 内容整体翻译为简体中文。\n" +
                "要求：\n" +
                "1. 严格保留全部 HTML 标签与结构原样，只翻译标签之间的文本内容；" +
                "不新增、不删除、不改写任何标签；代码块与专有名词保留原文。\n" +
                "2. 顶层块（<p>、<h1>-<h6>、<li>、<blockquote>、<pre>、<table>、<figure> 等）" +
                "的数量、类型与顺序必须与原文逐一对应：一段原文对应一段译文，" +
                "禁止合并或拆分段落，禁止增删块；空段落也要保留为对应的空块。\n" +
                "3. 直接输出翻译后的完整 HTML，不要任何解释或代码围栏。"
    }
}
