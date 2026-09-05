package com.cycling.rssradar.core.data.ai

import com.cycling.rssradar.core.data.ai.AiText.truncationNote


/**
 * 模型输入：一切 prompt 的公共上下文。
 *
 * @param body 已按 [AiText.truncateForPrompt] 截断的正文纯文本。
 *             统计类功能（阅读习惯、每日报告、订阅源健康）没有"正文"这一说，
 *             它们的输入全在 [extra] 里，body 留空。
 * @param truncated 是否截断过。截断事实必须告诉模型，否则它会以为文章就这么短。
 * @param companions 伴生上下文：聚合/去重/事件合并时同时送进模型的其他文章。
 */
data class AiPromptContext(
    val title: String,
    val feedTitle: String,
    val author: String? = null,
    val body: String = "",
    val truncated: Boolean = false,
    val question: String? = null,
    val extra: String = "",
    val companions: List<AiPromptCompanion> = emptyList(),
)

/** 伴生文章：只带 id 与标题（正文太长，多篇文章塞不下），模型据此做跨文章判断。 */
data class AiPromptCompanion(
    val id: Long,
    val title: String,
    val feedTitle: String = "",
)

/** 提示词覆盖：订阅源级摘要提示词在此注入，为空则走内置模板。 */
data class AiPromptOverrides(
    val summaryPrompt: String? = null,
)

/** 一次调用所需的全部模型入参。 */
data class AiPrompt(
    val system: String,
    val user: String,
    val temperature: Double,
)


/**
 * 35 项功能的提示词模板。
 *
 * 三条贯穿全部模板的原则：
 * 1. **不捏造**——每条要求里都写明"只使用给定文本中出现的信息，没有就说没有"。
 *    模型最危险的失败模式不是答错，而是自信地编出原文没有的细节，而用户无从分辨。
 * 2. **忠实类用低温、创作类用高温**——摘要/翻译/提取取 0.2~0.4 压住发散，
 *    分享文案/简报/破壁建议取 0.7 允许组织与润色，但润色不改变事实。
 * 3. **结构化输出给 JSON schema 且只输出 JSON**——解析侧（[AiParsers]）做了大量容错，
 *    但约束写得越死，容错路径走得越少。
 */
object AiPrompts {

    /** 摘要变量的替换表：订阅源级自定义提示词支持这些占位符。 */
    private val SUMMARY_VARIABLES = listOf("{title}", "{feed}", "{author}", "{content}")

    /**
     * 构建某项功能的 prompt。
     *
     * @return null 表示这项功能不由大模型产出（如用量看板、任务队列），调用方不应发起请求。
     */
    fun build(
        feature: AiFeature,
        context: AiPromptContext,
        overrides: AiPromptOverrides = AiPromptOverrides(),
    ): AiPrompt? {
        if (!feature.needsLlm) return null
        return when (feature) {
            AiFeature.SUMMARY -> summary(context, overrides.summaryPrompt)
            AiFeature.TRANSLATE -> translate(context)
            AiFeature.CLASSIFY -> classify(context)
            AiFeature.TAGS -> tags(context)
            AiFeature.SENTIMENT -> sentiment(context)
            AiFeature.KEYWORDS -> keywords(context)
            AiFeature.OPINION -> opinion(context)
            AiFeature.QA -> qa(context)
            AiFeature.FULLTEXT -> fulltext(context)
            AiFeature.DEDUPE -> dedupe(context)
            AiFeature.QUALITY -> quality(context)
            AiFeature.NOISE -> noise(context)
            AiFeature.OUTLINE -> outline(context)
            AiFeature.CREDIBILITY -> credibility(context)
            AiFeature.GLOSSARY -> glossary(context)

            AiFeature.FEED_RECOMMEND -> feedRecommend(context)
            AiFeature.DISCOVER -> discover(context)
            AiFeature.BUBBLE_BREAK -> bubbleBreak(context)
            AiFeature.AGGREGATE -> aggregate(context)
            AiFeature.INTEREST_RANK -> interestRank(context)
            AiFeature.EVENT_MERGE -> eventMerge(context)
            AiFeature.COLD_START -> coldStart(context)

            AiFeature.DAILY_BRIEF -> dailyBrief(context)
            AiFeature.SHARE_COPY -> shareCopy(context)
            AiFeature.SMART_NOTIFY -> importance(context)
            AiFeature.FEED_HEALTH -> feedHealth(context)
            AiFeature.HABIT -> habit(context)
            AiFeature.DAILY_REPORT -> dailyReport(context)
            AiFeature.FILTER_RULE -> filterRule(context)

            // 非 LLM 功能：本地计算，不进模型。
            AiFeature.PERSONAL_FEED,
            AiFeature.TOPIC_GALAXY,
            AiFeature.RELATED,
            AiFeature.USAGE,
            AiFeature.TASK_QUEUE,
            AiFeature.PROMPT_TEMPLATE,
            -> null
        }
    }

    /** 订阅源级摘要提示词支持的变量说明，设置页渲染给用户看。 */
    fun summaryVariableHelp(): String =
        "可用变量：${SUMMARY_VARIABLES.joinToString("、")}；留空则使用内置摘要提示词。"

    /** 内置摘要提示词原文（提示词管理页预览用）。与 [summary] 实际生效的是同一份。 */
    fun builtInSummaryPrompt(): String = DEFAULT_SUMMARY_SYSTEM

    /** 把自定义模板里的变量替换成实际内容。未知变量原样保留（用户看得见才会改）。 */
    fun renderTemplate(template: String, context: AiPromptContext): String =
        template
            .replace("{title}", context.title)
            .replace("{feed}", context.feedTitle)
            .replace("{author}", context.author.orEmpty())
            .replace("{content}", context.body)

    // ── 内容处理 ────────────────────────────────────────────────────────────

    /**
     * 摘要。**唯一支持订阅源级提示词覆盖的功能**——不同源的信息密度差得远：
     * 技术博客要结论与要点，新闻快讯只要一句话，用同一套提示词必然有一边不合适。
     * 自定义模板只替换 system，正文包装与截断标注仍由本方法负责，用户模板写不坏输出。
     */
    fun summary(context: AiPromptContext, customSystem: String? = null): AiPrompt {
        val system = customSystem?.takeIf { it.isNotBlank() }?.let { renderTemplate(it, context) }
            ?: DEFAULT_SUMMARY_SYSTEM
        return AiPrompt(system, articleBlock(context), 0.4)
    }

    fun translate(context: AiPromptContext): AiPrompt =
        AiPrompt(TRANSLATE_SYSTEM, context.body, 0.3)

    fun classify(context: AiPromptContext): AiPrompt =
        AiPrompt(CLASSIFY_SYSTEM, articleBlock(context), 0.2)

    fun tags(context: AiPromptContext): AiPrompt =
        AiPrompt(TAGS_SYSTEM, articleBlock(context), 0.3)

    fun sentiment(context: AiPromptContext): AiPrompt =
        AiPrompt(SENTIMENT_SYSTEM, articleBlock(context), 0.2)

    fun keywords(context: AiPromptContext): AiPrompt =
        AiPrompt(KEYWORDS_SYSTEM, articleBlock(context), 0.2)

    fun opinion(context: AiPromptContext): AiPrompt =
        AiPrompt(OPINION_SYSTEM, articleBlock(context), 0.3)

    fun qa(context: AiPromptContext): AiPrompt =
        AiPrompt(QA_SYSTEM, articleBlock(context) + questionBlock(context), 0.3)

    /** 全文提取喂的是原始 HTML 而不是纯文本——要的就是标签结构。 */
    fun fulltext(context: AiPromptContext): AiPrompt =
        AiPrompt(FULLTEXT_SYSTEM, context.body, 0.2)

    fun dedupe(context: AiPromptContext): AiPrompt =
        AiPrompt(DEDUPE_SYSTEM, articleBlock(context) + companionBlock(context), 0.2)

    fun quality(context: AiPromptContext): AiPrompt =
        AiPrompt(QUALITY_SYSTEM, articleBlock(context), 0.3)

    fun noise(context: AiPromptContext): AiPrompt =
        AiPrompt(NOISE_SYSTEM, articleBlock(context), 0.3)

    fun outline(context: AiPromptContext): AiPrompt =
        AiPrompt(OUTLINE_SYSTEM, articleBlock(context), 0.3)

    fun credibility(context: AiPromptContext): AiPrompt =
        AiPrompt(CREDIBILITY_SYSTEM, articleBlock(context), 0.3)

    fun glossary(context: AiPromptContext): AiPrompt =
        AiPrompt(GLOSSARY_SYSTEM, articleBlock(context) + questionBlock(context), 0.3)

    // ── 推荐发现 ────────────────────────────────────────────────────────────

    fun feedRecommend(context: AiPromptContext): AiPrompt =
        AiPrompt(FEED_RECOMMEND_SYSTEM, context.extra.ifBlank { context.body }, 0.7)

    fun discover(context: AiPromptContext): AiPrompt =
        AiPrompt(DISCOVER_SYSTEM, context.extra.ifBlank { companionBlock(context) }, 0.6)

    fun bubbleBreak(context: AiPromptContext): AiPrompt =
        AiPrompt(BUBBLE_BREAK_SYSTEM, context.extra.ifBlank { context.body }, 0.7)

    fun aggregate(context: AiPromptContext): AiPrompt =
        AiPrompt(AGGREGATE_SYSTEM, companionBlock(context), 0.5)

    fun interestRank(context: AiPromptContext): AiPrompt =
        AiPrompt(INTEREST_RANK_SYSTEM, context.extra.ifBlank { context.body }, 0.5)

    fun eventMerge(context: AiPromptContext): AiPrompt =
        AiPrompt(EVENT_MERGE_SYSTEM, companionBlock(context), 0.4)

    fun coldStart(context: AiPromptContext): AiPrompt =
        AiPrompt(COLD_START_SYSTEM, context.extra.ifBlank { context.body }, 0.4)

    // ── 辅助推送 ────────────────────────────────────────────────────────────

    fun dailyBrief(context: AiPromptContext): AiPrompt =
        AiPrompt(DAILY_BRIEF_SYSTEM, companionBlock(context), 0.6)

    fun shareCopy(context: AiPromptContext): AiPrompt =
        AiPrompt(SHARE_COPY_SYSTEM, articleBlock(context), 0.8)

    fun importance(context: AiPromptContext): AiPrompt =
        AiPrompt(IMPORTANCE_SYSTEM, articleBlock(context), 0.2)

    fun feedHealth(context: AiPromptContext): AiPrompt =
        AiPrompt(FEED_HEALTH_SYSTEM, context.extra.ifBlank { context.body }, 0.3)

    fun habit(context: AiPromptContext): AiPrompt =
        AiPrompt(HABIT_SYSTEM, context.extra.ifBlank { context.body }, 0.6)

    fun dailyReport(context: AiPromptContext): AiPrompt =
        AiPrompt(DAILY_REPORT_SYSTEM, context.extra.ifBlank { companionBlock(context) }, 0.6)

    fun filterRule(context: AiPromptContext): AiPrompt =
        AiPrompt(FILTER_RULE_SYSTEM, articleBlock(context) + questionBlock(context), 0.4)

    // ── 输入包装 ────────────────────────────────────────────────────────────

    private fun articleBlock(context: AiPromptContext): String = buildString {
        appendLine("标题：${context.title}")
        appendLine("来源：${context.feedTitle}")
        context.author?.takeIf { it.isNotBlank() }?.let { appendLine("作者：$it") }
        appendLine()
        appendLine("正文：")
        append(context.body)
        if (context.truncated) {
            appendLine()
            append(truncationNote(context.body.length))
        }
    }

    /** 跨文章功能（去重/聚合/事件合并/简报）的输入：每篇只给 id + 来源 + 标题。 */
    private fun companionBlock(context: AiPromptContext): String = buildString {
        if (context.companions.isEmpty() && context.body.isNotBlank()) {
            appendLine("文章列表：")
            appendLine("- id=${context.extra.ifBlank { "0" }} 来源=${context.feedTitle} 标题=${context.title}")
            return@buildString
        }
        appendLine("文章列表（共 ${context.companions.size} 篇）：")
        context.companions.forEach { c ->
            appendLine("- id=${c.id} 来源=${c.feedTitle} 标题=${c.title}")
        }
        if (context.body.isNotBlank()) {
            appendLine()
            appendLine("当前重点文章正文：")
            append(context.body)
        }
    }

    private fun questionBlock(context: AiPromptContext): String = buildString {
        val q = context.question?.takeIf { it.isNotBlank() } ?: return@buildString
        appendLine()
        appendLine()
        appendLine("用户问题：$q")
    }
}
