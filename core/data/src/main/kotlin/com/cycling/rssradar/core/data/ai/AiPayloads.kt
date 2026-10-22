package com.cycling.rssradar.core.data.ai

import com.cycling.rssradar.core.model.AiFeature
import kotlinx.serialization.Serializable


/**
 * 16 项 AI 产物的结构化载荷。
 *
 * 全部用 `kotlinx.serialization` 且**每个字段都给默认值**——模型输出不可信，
 * 少一个字段、多一个字段、字段名拼错都可能发生；解析失败不该让整个功能崩掉，
 * 而应退化成"这项没结果"，用户看到的是"生成失败，可重试"而不是闪退。
 *
 * 存进 `ai_artifacts.payload` 的就是这些对象的 JSON；展示形态由 UI 决定，
 * 改 UI 不用重跑模型（重跑要花钱，这是既定原则）。
 */

// ── 内容处理类 ─────────────────────────────────────────────────────────────

@Serializable
data class AiKeywordsPayload(
    val keywords: List<String> = emptyList(),
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

// ── 辅助推送类 ─────────────────────────────────────────────────────────────

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
data class AiHealthPayload(
    /** OK=正常 / DEGRADED=降频 / BROKEN=失效 / UNKNOWN=数据不足。 */
    val status: String = "UNKNOWN",
    val reason: String = "",
    val advice: String = "",
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

/** 序列化器：宽松配置是刻意的——模型不是可靠的 JSON 生产者。 */
val AiJson = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
    coerceInputValues = true
}
