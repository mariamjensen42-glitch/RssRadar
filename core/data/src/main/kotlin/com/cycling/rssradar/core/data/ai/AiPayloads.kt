package com.cycling.rssradar.core.data.ai

import kotlinx.serialization.Serializable


/**
 * 35 项 AI 产物的结构化载荷。
 *
 * 全部用 `kotlinx.serialization` 且**每个字段都给默认值**——模型输出不可信，
 * 少一个字段、多一个字段、字段名拼错都可能发生；解析失败不该让整个功能崩掉，
 * 而应退化成"这项没结果"，用户看到的是"生成失败，可重试"而不是闪退。
 *
 * 存进 `ai_artifacts.payload` 的就是这些对象的 JSON；展示形态由 UI 决定，
 * 改 UI 不用重跑模型（重跑要花钱，这是 ADR-0005 定下的原则）。
 */

// ── 内容处理类 ─────────────────────────────────────────────────────────────

@Serializable
data class AiTagsPayload(
    val tags: List<String> = emptyList(),
)

@Serializable
data class AiKeywordsPayload(
    val keywords: List<String> = emptyList(),
)

@Serializable
data class AiClassifyPayload(
    val topic: String = "",
    /** 0.0~1.0，模型自评置信度。低于阈值时 UI 不展示该分类。 */
    val confidence: Double = 0.0,
    val alternatives: List<String> = emptyList(),
)

@Serializable
data class AiSentimentPayload(
    /** POSITIVE / NEUTRAL / NEGATIVE，解析时归一化为大写，未知值按 NEUTRAL 处理。 */
    val polarity: String = "NEUTRAL",
    /** 强度 0.0~1.0。 */
    val score: Double = 0.0,
    val reason: String = "",
)

@Serializable
data class AiQualityPayload(
    /** 总分 0~100。 */
    val overall: Int = 0,
    /** 信息密度 0~100。 */
    val density: Int = 0,
    /** 原创性 0~100。 */
    val originality: Int = 0,
    /** 标题党程度 0~100，**越高越糟**，展示时反向解读。 */
    val clickbait: Int = 0,
    /** 证据充分性 0~100。 */
    val evidence: Int = 0,
    /** 一句短板说明。 */
    val note: String = "",
)

@Serializable
data class AiNoisePayload(
    /** 信息价值分 0~100，与质量分的区别：这一分只关心"值不值得占用注意力"。 */
    val value: Int = 0,
    val isNoise: Boolean = false,
    val reasons: List<String> = emptyList(),
    /** 剥掉噪声后剩下的实质要点，用户可直接看这个而不读全文。 */
    val keptPoints: List<String> = emptyList(),
)

@Serializable
data class AiCredibilityPayload(
    /** HIGH / MEDIUM / LOW / UNKNOWN。 */
    val level: String = "UNKNOWN",
    val signals: List<String> = emptyList(),
    val doubts: List<String> = emptyList(),
)

@Serializable
data class AiOutlinePayload(
    val gist: String = "",
    val sections: List<Section> = emptyList(),
) {
    @Serializable
    data class Section(
        val heading: String = "",
        val summary: String = "",
        /** 用于滚动定位：原文里该节开头的一段文字（模型摘自正文，不作编号——编号一变就指错位置）。 */
        val anchor: String = "",
    )
}

@Serializable
data class AiOpinionPayload(
    val claims: List<Claim> = emptyList(),
) {
    @Serializable
    data class Claim(
        val claim: String = "",
        val basis: String = "",
        /** VIEW=作者观点 / FACT=引用事实 / DATA=数据支撑。 */
        val kind: String = "VIEW",
    )
}

@Serializable
data class AiDedupePayload(
    /** 同一事件的组标识，同组内共享。 */
    val groupKey: String = "",
    /** 是否为该组主篇（信息最全、来源最权威的那篇）。 */
    val isPrimary: Boolean = false,
    val reason: String = "",
)

@Serializable
data class AiFulltextPayload(
    val ok: Boolean = false,
    /** 提取出的正文 HTML。ok=false 时为空，UI 如实提示而不是显示空白页。 */
    val html: String = "",
    val note: String = "",
)

@Serializable
data class AiGlossaryPayload(
    val term: String = "",
    val explanation: String = "",
)

/** 问答不落库（REALTIME 触发），但复用同一套载荷结构便于统一渲染历史。 */
@Serializable
data class AiQaPayload(
    val answer: String = "",
    /** 模型引用的正文片段，UI 展示"依据"。 */
    val quotes: List<String> = emptyList(),
    /** 文中找不到依据时置 true，UI 明确提示"文中未提及"而不是让模型硬编。 */
    val notFound: Boolean = false,
)

// ── 推荐发现类 ─────────────────────────────────────────────────────────────

@Serializable
data class AiFeedRecommendPayload(
    val suggestions: List<Suggestion> = emptyList(),
) {
    @Serializable
    data class Suggestion(
        val name: String = "",
        /** 建议订阅的完整地址，可能为空（只给站点名让用户自己搜）。 */
        val url: String = "",
        /** 若来自内置 RSSHub 路由目录，填路由路径，UI 可直接预览。 */
        val route: String = "",
        val reason: String = "",
    )
}

@Serializable
data class AiTopicsPayload(
    val topics: List<Topic> = emptyList(),
) {
    @Serializable
    data class Topic(
        val name: String = "",
        val articleIds: List<Long> = emptyList(),
        val summary: String = "",
    )
}

@Serializable
data class AiBubblePayload(
    /** 画像覆盖不到的话题盲区。 */
    val blindSpots: List<String> = emptyList(),
    val articleIds: List<Long> = emptyList(),
    val note: String = "",
)

@Serializable
data class AiAggregatePayload(
    val consensus: List<String> = emptyList(),
    val divergence: List<String> = emptyList(),
    val watch: List<String> = emptyList(),
    val sourceIds: List<Long> = emptyList(),
)

@Serializable
data class AiInterestRankPayload(
    val interests: List<Interest> = emptyList(),
) {
    @Serializable
    data class Interest(
        val name: String = "",
        /** 0.0~1.0，归一化后的强度。 */
        val weight: Double = 0.0,
        val feeds: List<String> = emptyList(),
    )
}

@Serializable
data class AiEventPayload(
    val event: String = "",
    val timeline: List<Node> = emptyList(),
) {
    @Serializable
    data class Node(
        val articleId: Long = 0,
        /** 原文发布的时间描述，照抄原文，不做时区换算（换算错了就是编造）。 */
        val time: String = "",
        val headline: String = "",
        val outlet: String = "",
        /** 该源与其他源的口径差异，无差异时留空。 */
        val difference: String = "",
    )
}

@Serializable
data class AiDiscoverPayload(
    val articleIds: List<Long> = emptyList(),
    val note: String = "",
)

// ── 辅助推送类 ─────────────────────────────────────────────────────────────

@Serializable
data class AiBriefPayload(
    val headline: String = "",
    val items: List<Item> = emptyList(),
    /** 判定为"可跳过"的文章 id，UI 折叠展示而不是直接隐藏——用户有权知道被跳过了什么。 */
    val skippable: List<Long> = emptyList(),
) {
    @Serializable
    data class Item(
        val title: String = "",
        val articleId: Long = 0,
        val why: String = "",
    )
}

@Serializable
data class AiSharePayload(
    val variants: List<Variant> = emptyList(),
) {
    /** SHORT=短评 / THREAD=长推 / BULLET=要点体。 */
    @Serializable
    data class Variant(
        val style: String = "SHORT",
        val text: String = "",
    )
}

@Serializable
data class AiImportancePayload(
    val important: Boolean = false,
    /** 0~100，UI 只用于排序与阈值判定，不对用户展示裸分数。 */
    val score: Int = 0,
    val reason: String = "",
)

@Serializable
data class AiHealthPayload(
    /** OK=正常 / DEGRADED=降频 / BROKEN=失效 / UNKNOWN=数据不足。 */
    val status: String = "UNKNOWN",
    val reason: String = "",
    val advice: String = "",
)

@Serializable
data class AiHabitPayload(
    /** 活跃时段（0~23 的小时），由真实 lastOpenedAt 统计与模型归纳共同得出。 */
    val activeHours: List<Int> = emptyList(),
    /** 订阅源集中度 0.0~1.0，越高说明读得越窄。 */
    val concentration: Double = 0.0,
    val observations: List<String> = emptyList(),
)

@Serializable
data class AiReportPayload(
    val summary: String = "",
    val highlights: List<String> = emptyList(),
    val missed: List<Long> = emptyList(),
    val observations: List<String> = emptyList(),
)

@Serializable
data class AiFilterRulePayload(
    val rules: List<Rule> = emptyList(),
) {
    @Serializable
    data class Rule(
        val keyword: String = "",
        /** TITLE=标题 / SUMMARY=摘要 / BOTH=两者。 */
        val field: String = "BOTH",
        /** 命中示例，让用户确认规则没有误伤；取自真实文章标题，不编造。 */
        val hits: List<String> = emptyList(),
    )
}

@Serializable
data class AiColdStartPayload(
    /** 用户勾选的领域 → 画像种子词，写入兴趣画像。 */
    val seeds: List<String> = emptyList(),
)


/**
 * 载荷与功能的绑定关系：给定 [AiFeature]，知道产物该按哪种类型解析。
 *
 * 集中在一处而不是散落在各执行分支，是为了**新增功能时只改一个文件**——
 * 忘了在这里登记，解析会走 [AiFeature.UNPARSED] 分支直接当纯文本，
 * 不会崩，但 UI 拿不到结构化字段，测试能第一时间发现。
 */
object AiPayloadKinds {
    /** 产物是结构化 JSON、按下面 `parse` 分支解析的功能。 */
    val STRUCTURED: Set<AiFeature> = setOf(
        AiFeature.TAGS,
        AiFeature.KEYWORDS,
        AiFeature.CLASSIFY,
        AiFeature.SENTIMENT,
        AiFeature.QUALITY,
        AiFeature.NOISE,
        AiFeature.CREDIBILITY,
        AiFeature.OUTLINE,
        AiFeature.OPINION,
        AiFeature.DEDUPE,
        AiFeature.FULLTEXT,
        AiFeature.GLOSSARY,
        AiFeature.QA,
        AiFeature.FEED_RECOMMEND,
        AiFeature.TOPIC_GALAXY,
        AiFeature.BUBBLE_BREAK,
        AiFeature.AGGREGATE,
        AiFeature.INTEREST_RANK,
        AiFeature.EVENT_MERGE,
        AiFeature.DISCOVER,
        AiFeature.DAILY_BRIEF,
        AiFeature.SHARE_COPY,
        AiFeature.SMART_NOTIFY,
        AiFeature.FEED_HEALTH,
        AiFeature.HABIT,
        AiFeature.DAILY_REPORT,
        AiFeature.FILTER_RULE,
        AiFeature.COLD_START,
    )

    /** 产物是纯文本/Markdown，直接存原文（摘要、翻译、每日简报正文等）。 */
    val PLAIN_TEXT: Set<AiFeature> = setOf(
        AiFeature.SUMMARY,
        AiFeature.TRANSLATE,
    )

    fun isStructured(feature: AiFeature): Boolean = feature in STRUCTURED
}

/** 序列化器：宽松配置是刻意的——模型不是可靠的 JSON 生产者。 */
val AiJson = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
    coerceInputValues = true
}
