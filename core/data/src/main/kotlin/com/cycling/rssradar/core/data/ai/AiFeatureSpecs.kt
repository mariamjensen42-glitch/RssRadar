package com.cycling.rssradar.core.data.ai

import com.cycling.rssradar.core.model.AiFeature

/**
 * 每项 AI 功能一份 [AiFeatureSpec]：prompt 构建、解析、「空壳判定」
 * 全部登记在一处。此前这三份知识按技术层散布在 [AiPrompts.build]、[AiParsers.parse]
 * 与 [AiParsers.isMeaningful] 三个大 when 里——
 * 加一项功能要改 8 个文件、三处补分支，漏一处就是编译器抓不住的运行期缺口
 * （isMeaningful 的 `else -> true` 分支会放行空壳产物）。
 *
 * 现在加一项功能的动作收敛为：AiFeature 加枚举 → AiPayloads 加载荷 →
 * AiParsers 加解析函数 → 在这里登记一行 → app 侧 AiArticleSheet 的 AI_RESULT_RENDERS 登记渲染。
 * 每项功能的全部行为知识在一行里可读，且各环节都可独立单测。
 */
class AiFeatureSpec(
    /** 构建模型入参。返回 null 表示这项功能不由大模型产出（本地计算）。 */
    val prompt: (context: AiPromptContext) -> AiPrompt?,
    /** 解析模型原文，返回对应载荷对象（或纯文本 String）。 */
    val parse: (raw: String) -> Any,
    /**
     * 这次生成**值不值得存**——解析没报错，但主要字段全空等于白跑一次。
     * 判空后不入库、记为失败，队列按退避重试；缺省视为有意义。
     */
    val isMeaningful: (parsed: Any) -> Boolean = { true },
    /**
     * 从**已解析的载荷**里取一个 0~100 的可排序分数；null = 这项功能没有可排序的量。
     *
     * 落进 `ai_artifacts.score`，供文章列表按「信息价值」排序与卡片角标使用。
     * 目前只有降噪（信息价值）登记。
     */
    val score: (parsed: Any) -> Int? = { null },
)

object AiFeatureSpecs {

    val all: Map<AiFeature, AiFeatureSpec> = buildMap {
        fun spec(
            feature: AiFeature,
            prompt: (AiPromptContext) -> AiPrompt?,
            parse: (String) -> Any = { it },
            isMeaningful: (Any) -> Boolean = { true },
            score: (Any) -> Int? = { null },
        ) = put(feature, AiFeatureSpec(prompt, parse, isMeaningful, score))

        // ── 内容处理 ──
        spec(AiFeature.SUMMARY, prompt = { c -> AiPrompts.summary(c, c.summaryPrompt) }, isMeaningful = { (it as String).isNotBlank() })
        spec(AiFeature.TRANSLATE, prompt = { c -> AiPrompts.translate(c) }, isMeaningful = { (it as String).isNotBlank() })
        spec(AiFeature.KEYWORDS, prompt = { c -> AiPrompts.keywords(c) }, parse = AiParsers::keywords, isMeaningful = { (it as AiKeywordsPayload).keywords.isNotEmpty() })
        spec(AiFeature.QA, prompt = { c -> AiPrompts.qa(c) }, parse = AiParsers::qa, isMeaningful = { (it as AiQaPayload).answer.isNotBlank() })
        spec(AiFeature.NOISE, prompt = { c -> AiPrompts.noise(c) }, parse = AiParsers::noise, score = { (it as AiNoisePayload).value.coerceIn(0, 100) })
        spec(AiFeature.OUTLINE, prompt = { c -> AiPrompts.outline(c) }, parse = AiParsers::outline)
        spec(AiFeature.GLOSSARY, prompt = { c -> AiPrompts.glossary(c) }, parse = AiParsers::glossary, isMeaningful = { (it as AiGlossaryPayload).explanation.isNotBlank() })

        // ── 推荐发现 ──
        spec(AiFeature.FEED_RECOMMEND, prompt = { c -> AiPrompts.feedRecommend(c) }, parse = AiParsers::feedRecommend, isMeaningful = { (it as AiFeedRecommendPayload).suggestions.isNotEmpty() })
        // 纯本地：不进模型，产物不落 ai_artifacts（相关阅读走实时计算）。
        spec(AiFeature.PERSONAL_FEED, prompt = { _ -> null })
        spec(AiFeature.RELATED, prompt = { _ -> null })

        // ── 辅助推送 ──
        spec(AiFeature.SHARE_COPY, prompt = { c -> AiPrompts.shareCopy(c) }, parse = AiParsers::shareCopy, isMeaningful = { (it as AiSharePayload).variants.isNotEmpty() })
        spec(AiFeature.FEED_HEALTH, prompt = { c -> AiPrompts.feedHealth(c) }, parse = AiParsers::feedHealth)
        spec(AiFeature.FILTER_RULE, prompt = { c -> AiPrompts.filterRule(c) }, parse = AiParsers::filterRule, isMeaningful = { (it as AiFilterRulePayload).rules.isNotEmpty() })
        spec(AiFeature.USAGE, prompt = { _ -> null })
        spec(AiFeature.TASK_QUEUE, prompt = { _ -> null })
        spec(AiFeature.PROMPT_TEMPLATE, prompt = { _ -> null })
    }

    /** 构建模型入参。null 表示这项功能不由大模型产出，调用方不应发起请求。 */
    fun buildPrompt(feature: AiFeature, context: AiPromptContext): AiPrompt? =
        all[feature]?.prompt?.invoke(context)

    /** 按功能解析模型原文。 */
    fun parse(feature: AiFeature, raw: String): Any =
        all[feature]?.parse?.invoke(raw) ?: raw

    fun isMeaningful(feature: AiFeature, parsed: Any): Boolean =
        all[feature]?.isMeaningful?.invoke(parsed) ?: true

    /** 落库用的可排序分数；没有可排序量的功能返回 null。 */
    fun score(feature: AiFeature, parsed: Any): Int? =
        all[feature]?.score?.invoke(parsed)

}
