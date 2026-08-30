package com.cycling.rssradar.data.ai

import com.cycling.rssradar.data.db.ArticleDao

/** 译文缓存上限（篇）。 */
private const val TRANSLATION_CACHE_MAX = 20

/** AI 摘要入库长度上限（字符）。列表流会带 aiSummary，防异常输出撑爆 CursorWindow 单行。 */
private const val AI_SUMMARY_MAX_LENGTH = 2_000

/**
 * AI 能力的领域门面（issue #44，ADR-0005）：
 * - AI 摘要：基于正文生成，持久化（articles.aiSummary），刷新永不覆盖；
 * - AI 翻译：替换式翻译为简体中文，不落盘，仅会话内内存缓存。
 * 语言预检 / 截断 / 响应解析都在 [AiText] 纯函数层。
 */
class AiRepository(
    private val articleDao: ArticleDao,
    private val client: DeepSeekClient,
) {

    /**
     * 译文会话级缓存：key = articleId，进程内有效，不落盘（ADR-0005）。
     * LRU 上限 20 篇（OOM 防线）：每条译文是整篇正文 HTML，无上限的 Map 会
     * 随翻译篇数线性吃堆。按访问序淘汰最旧的。
     */
    private val translationCache = object : LinkedHashMap<Long, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, String>): Boolean =
            size > TRANSLATION_CACHE_MAX
    }

    fun cachedTranslation(articleId: Long): String? = translationCache[articleId]

    fun clearTranslationCache(articleId: Long) {
        translationCache.remove(articleId)
    }

    sealed interface SummaryOutcome {
        data class Success(val summary: String) : SummaryOutcome
        data class Failure(val userMessage: String) : SummaryOutcome
    }

    sealed interface TranslationOutcome {
        data class Success(val html: String) : TranslationOutcome
        data object AlreadyChinese : TranslationOutcome
        data class Failure(val userMessage: String) : TranslationOutcome
    }

    /**
     * 生成 AI 摘要并入库。来源优先正文纯文本、退回摘要；两者皆无则拒绝生成——不编造。
     */
    suspend fun generateSummary(articleId: Long): SummaryOutcome {
        val article = articleDao.getWithFeed(articleId)?.article
            ?: return SummaryOutcome.Failure("文章不存在")
        val source = article.contentText?.takeIf { it.isNotBlank() }
            ?: article.summary?.takeIf { it.isNotBlank() }
            ?: return SummaryOutcome.Failure("本文没有可用于摘要的内容")
        return try {
            val (input, truncated) = AiText.truncateForPrompt(source)
            val text = client.chat(SUMMARY_SYSTEM, input)
            val summary = if (truncated) text + AiText.truncationNote(input.length) else text
            // 入库前截断（CursorWindow 防线）：列表流会连 aiSummary 一起查出，
            // 异常长的模型输出会把单行撑爆 2MB 窗口（数万行查询里一粒老鼠屎坏一锅粥）
            articleDao.updateAiSummary(articleId, summary.take(AI_SUMMARY_MAX_LENGTH))
            SummaryOutcome.Success(summary.take(AI_SUMMARY_MAX_LENGTH))
        } catch (e: AiException) {
            SummaryOutcome.Failure(e.userMessage)
        } catch (e: Exception) {
            SummaryOutcome.Failure("网络失败，请检查网络后重试")
        }
    }

    /**
     * 替换式翻译：正文 HTML 整体译为简体中文（保留标签结构），会话内缓存。
     * 中文文章预检直接拦截，不调 API。
     */
    suspend fun translate(articleId: Long): TranslationOutcome {
        translationCache[articleId]?.let { return TranslationOutcome.Success(it) }
        val article = articleDao.getWithFeed(articleId)?.article
            ?: return TranslationOutcome.Failure("文章不存在")
        val html = article.content?.takeIf { it.isNotBlank() }
            ?: article.summary?.takeIf { it.isNotBlank() }
            ?: return TranslationOutcome.Failure("本文没有可翻译的内容")
        if (AiText.isMostlyChinese(article.contentText ?: html)) {
            return TranslationOutcome.AlreadyChinese
        }
        return try {
            val (input, _) = AiText.truncateForPrompt(html)
            val translated = client.chat(TRANSLATE_SYSTEM, input)
            translationCache[articleId] = translated
            TranslationOutcome.Success(translated)
        } catch (e: AiException) {
            TranslationOutcome.Failure(e.userMessage)
        } catch (e: Exception) {
            TranslationOutcome.Failure("网络失败，请检查网络后重试")
        }
    }

    companion object {
        private const val SUMMARY_SYSTEM =
            "你是中文阅读助手。基于用户提供的文章内容，用简体中文写一段 3 到 5 句的内容概括。" +
                "只概括文中真实出现的信息，禁止编造、禁止添加文中没有的数字或事实。" +
                "直接输出概括本身，不要任何前缀、标题或解释。"

        private const val TRANSLATE_SYSTEM =
            "把用户提供的 HTML 内容整体翻译为简体中文。" +
                "严格保留全部 HTML 标签与结构原样，只翻译标签之间的文本内容；" +
                "不新增、不删除、不改写任何标签；代码块与专有名词保留原文。" +
                "直接输出翻译后的完整 HTML，不要任何解释或代码围栏。"
    }
}
