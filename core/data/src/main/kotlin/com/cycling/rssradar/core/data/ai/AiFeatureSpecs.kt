package com.cycling.rssradar.core.data.ai

/**
 * 每项 AI 功能一份 [AiFeatureSpec]：prompt 构建、解析、「空壳判定」、文章 id 收口
 * 全部登记在一处。此前这四份知识按技术层散布在 [AiPrompts.build]、[AiParsers.parse]、
 * [AiParsers.isMeaningful] 与 AiFeatureRunner.restrictIdsIfNeeded 四个大 when 里——
 * 加一项功能要改 8 个文件、四处补分支，漏一处就是编译器抓不住的运行期缺口
 * （isMeaningful 的 `else -> true` 分支会放行空壳产物）。
 *
 * 现在加一项功能的动作收敛为：AiFeature 加枚举 → AiPayloads 加载荷 →
 * AiParsers 加解析函数 → 在这里登记一行 → app 侧 AiResultRenders 登记渲染。
 * 每项功能的全部行为知识在一行里可读，且各环节都可独立单测。
 */
class AiFeatureSpec(
    /** 构建模型入参。返回 null 表示这项功能不由大模型产出（本地计算）。 */
    val prompt: (context: AiPromptContext, overrides: AiPromptOverrides) -> AiPrompt?,
    /** 解析模型原文，返回对应载荷对象（或纯文本 String）。 */
    val parse: (raw: String) -> Any,
    /**
     * 这次生成**值不值得存**——解析没报错，但主要字段全空等于白跑一次。
     * 判空后不入库、记为失败，队列按退避重试；缺省视为有意义。
     */
    val isMeaningful: (parsed: Any) -> Boolean = { true },
    /**
     * 把产物里引用的文章 id 限制在本次真实候选集内（防模型捏造 id）。
     * 只有引用了 id 的载荷需要登记；null 表示无 id 字段、原样返回。
     */
    val restrictIds: ((raw: String, allowed: Set<Long>) -> String)? = null,
)

object AiFeatureSpecs {

    val all: Map<AiFeature, AiFeatureSpec> = buildMap {
        fun spec(
            feature: AiFeature,
            prompt: (AiPromptContext, AiPromptOverrides) -> AiPrompt?,
            parse: (String) -> Any = { it },
            isMeaningful: (Any) -> Boolean = { true },
            restrictIds: ((String, Set<Long>) -> String)? = null,
        ) = put(feature, AiFeatureSpec(prompt, parse, isMeaningful, restrictIds))

        // ── 内容处理 ──
        spec(AiFeature.SUMMARY, prompt = { c, o -> AiPrompts.summary(c, o.summaryPrompt) }, isMeaningful = { (it as String).isNotBlank() })
        spec(AiFeature.TRANSLATE, prompt = { c, _ -> AiPrompts.translate(c) }, isMeaningful = { (it as String).isNotBlank() })
        spec(AiFeature.CLASSIFY, prompt = { c, _ -> AiPrompts.classify(c) }, parse = AiParsers::classify, isMeaningful = { (it as AiClassifyPayload).topic.isNotBlank() })
        spec(AiFeature.TAGS, prompt = { c, _ -> AiPrompts.tags(c) }, parse = AiParsers::tags, isMeaningful = { (it as AiTagsPayload).tags.isNotEmpty() })
        spec(AiFeature.SENTIMENT, prompt = { c, _ -> AiPrompts.sentiment(c) }, parse = AiParsers::sentiment)
        spec(AiFeature.KEYWORDS, prompt = { c, _ -> AiPrompts.keywords(c) }, parse = AiParsers::keywords, isMeaningful = { (it as AiKeywordsPayload).keywords.isNotEmpty() })
        spec(AiFeature.OPINION, prompt = { c, _ -> AiPrompts.opinion(c) }, parse = AiParsers::opinion)
        spec(AiFeature.QA, prompt = { c, _ -> AiPrompts.qa(c) }, parse = AiParsers::qa, isMeaningful = { (it as AiQaPayload).answer.isNotBlank() })
        spec(AiFeature.FULLTEXT, prompt = { c, _ -> AiPrompts.fulltext(c) }, parse = AiParsers::fulltext, isMeaningful = { (it as AiFulltextPayload).ok })
        spec(AiFeature.DEDUPE, prompt = { c, _ -> AiPrompts.dedupe(c) }, parse = AiParsers::dedupe)
        spec(AiFeature.QUALITY, prompt = { c, _ -> AiPrompts.quality(c) }, parse = AiParsers::quality)
        spec(AiFeature.NOISE, prompt = { c, _ -> AiPrompts.noise(c) }, parse = AiParsers::noise)
        spec(AiFeature.OUTLINE, prompt = { c, _ -> AiPrompts.outline(c) }, parse = AiParsers::outline)
        spec(AiFeature.CREDIBILITY, prompt = { c, _ -> AiPrompts.credibility(c) }, parse = AiParsers::credibility)
        spec(AiFeature.GLOSSARY, prompt = { c, _ -> AiPrompts.glossary(c) }, parse = AiParsers::glossary, isMeaningful = { (it as AiGlossaryPayload).explanation.isNotBlank() })

        // ── 推荐发现 ──
        spec(AiFeature.FEED_RECOMMEND, prompt = { c, _ -> AiPrompts.feedRecommend(c) }, parse = AiParsers::feedRecommend, isMeaningful = { (it as AiFeedRecommendPayload).suggestions.isNotEmpty() })
        spec(AiFeature.DISCOVER, prompt = { c, _ -> AiPrompts.discover(c) }, parse = AiParsers::discover, isMeaningful = { (it as AiDiscoverPayload).articleIds.isNotEmpty() }, restrictIds = ::restrictDiscover)
        spec(AiFeature.BUBBLE_BREAK, prompt = { c, _ -> AiPrompts.bubbleBreak(c) }, parse = AiParsers::bubble, isMeaningful = { (it as AiBubblePayload).blindSpots.isNotEmpty() || (it as AiBubblePayload).articleIds.isNotEmpty() }, restrictIds = ::restrictBubble)
        spec(AiFeature.AGGREGATE, prompt = { c, _ -> AiPrompts.aggregate(c) }, parse = AiParsers::aggregate, isMeaningful = { (it as AiAggregatePayload).consensus.isNotEmpty() || (it as AiAggregatePayload).divergence.isNotEmpty() }, restrictIds = ::restrictAggregate)
        spec(AiFeature.INTEREST_RANK, prompt = { c, _ -> AiPrompts.interestRank(c) }, parse = AiParsers::interestRank, isMeaningful = { (it as AiInterestRankPayload).interests.isNotEmpty() })
        spec(AiFeature.EVENT_MERGE, prompt = { c, _ -> AiPrompts.eventMerge(c) }, parse = AiParsers::event, isMeaningful = { (it as AiEventPayload).event.isNotBlank() && (it as AiEventPayload).timeline.isNotEmpty() }, restrictIds = ::restrictEvent)
        spec(AiFeature.COLD_START, prompt = { c, _ -> AiPrompts.coldStart(c) }, parse = AiParsers::coldStart, isMeaningful = { (it as AiColdStartPayload).seeds.isNotEmpty() })
        // 纯本地：不进模型，产物不落 ai_artifacts（相关阅读走实时计算）。
        spec(AiFeature.PERSONAL_FEED, prompt = { _, _ -> null })
        spec(AiFeature.TOPIC_GALAXY, prompt = { _, _ -> null })
        spec(AiFeature.RELATED, prompt = { _, _ -> null })

        // ── 辅助推送 ──
        spec(AiFeature.DAILY_BRIEF, prompt = { c, _ -> AiPrompts.dailyBrief(c) }, parse = AiParsers::dailyBrief, isMeaningful = { (it as AiBriefPayload).items.isNotEmpty() || (it as AiBriefPayload).headline.isNotBlank() }, restrictIds = ::restrictBrief)
        spec(AiFeature.SHARE_COPY, prompt = { c, _ -> AiPrompts.shareCopy(c) }, parse = AiParsers::shareCopy, isMeaningful = { (it as AiSharePayload).variants.isNotEmpty() })
        spec(AiFeature.SMART_NOTIFY, prompt = { c, _ -> AiPrompts.importance(c) }, parse = AiParsers::importance)
        spec(AiFeature.FEED_HEALTH, prompt = { c, _ -> AiPrompts.feedHealth(c) }, parse = AiParsers::feedHealth)
        spec(AiFeature.HABIT, prompt = { c, _ -> AiPrompts.habit(c) }, parse = AiParsers::habit)
        spec(AiFeature.DAILY_REPORT, prompt = { c, _ -> AiPrompts.dailyReport(c) }, parse = AiParsers::dailyReport, isMeaningful = { (it as AiReportPayload).summary.isNotBlank() || (it as AiReportPayload).highlights.isNotEmpty() }, restrictIds = ::restrictReport)
        spec(AiFeature.FILTER_RULE, prompt = { c, _ -> AiPrompts.filterRule(c) }, parse = AiParsers::filterRule, isMeaningful = { (it as AiFilterRulePayload).rules.isNotEmpty() })
        spec(AiFeature.USAGE, prompt = { _, _ -> null })
        spec(AiFeature.TASK_QUEUE, prompt = { _, _ -> null })
        spec(AiFeature.PROMPT_TEMPLATE, prompt = { _, _ -> null })
    }

    /** 构建模型入参。null 表示这项功能不由大模型产出，调用方不应发起请求。 */
    fun buildPrompt(feature: AiFeature, context: AiPromptContext, overrides: AiPromptOverrides = AiPromptOverrides()): AiPrompt? =
        all[feature]?.prompt?.invoke(context, overrides)

    /** 按功能解析模型原文。 */
    fun parse(feature: AiFeature, raw: String): Any =
        all[feature]?.parse?.invoke(raw) ?: raw

    fun isMeaningful(feature: AiFeature, parsed: Any): Boolean =
        all[feature]?.isMeaningful?.invoke(parsed) ?: true

    /**
     * 把产物里引用的文章 id 收口到本次候选集。
     * 模型「顺着语义」给一个不在列表里的 id 是高频失败，UI 照单全收后用户会点进
     * 完全不相关的文章且毫无察觉；候选集为空时跳过（单文章功能本就没有 id 字段语义）。
     */
    fun restrictIds(feature: AiFeature, raw: String, allowed: Set<Long>): String {
        val restrict = all[feature]?.restrictIds ?: return raw
        if (allowed.isEmpty()) return raw
        return restrict(raw, allowed)
    }

    // ── 各载荷的 id 收口实现：重新解析 → 过滤 → 重新序列化 ──

    private fun restrictDiscover(raw: String, allowed: Set<Long>) =
        AiJson.encodeToString(AiDiscoverPayload.serializer(), AiParsers.discover(raw).let {
            it.copy(articleIds = AiParsers.restrictIds(it.articleIds, allowed))
        })

    private fun restrictBrief(raw: String, allowed: Set<Long>) =
        AiJson.encodeToString(AiBriefPayload.serializer(), AiParsers.dailyBrief(raw).let {
            it.copy(
                items = it.items.filter { item -> item.articleId == 0L || item.articleId in allowed },
                skippable = AiParsers.restrictIds(it.skippable, allowed),
            )
        })

    private fun restrictAggregate(raw: String, allowed: Set<Long>) =
        AiJson.encodeToString(AiAggregatePayload.serializer(), AiParsers.aggregate(raw).let {
            it.copy(sourceIds = AiParsers.restrictIds(it.sourceIds, allowed))
        })

    private fun restrictBubble(raw: String, allowed: Set<Long>) =
        AiJson.encodeToString(AiBubblePayload.serializer(), AiParsers.bubble(raw).let {
            it.copy(articleIds = AiParsers.restrictIds(it.articleIds, allowed))
        })

    private fun restrictEvent(raw: String, allowed: Set<Long>) =
        AiJson.encodeToString(AiEventPayload.serializer(), AiParsers.event(raw).let {
            it.copy(timeline = it.timeline.filter { node -> node.articleId in allowed })
        })

    private fun restrictReport(raw: String, allowed: Set<Long>) =
        AiJson.encodeToString(AiReportPayload.serializer(), AiParsers.dailyReport(raw).let {
            it.copy(missed = AiParsers.restrictIds(it.missed, allowed))
        })
}
