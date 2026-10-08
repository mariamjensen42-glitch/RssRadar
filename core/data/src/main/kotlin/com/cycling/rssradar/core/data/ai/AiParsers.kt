package com.cycling.rssradar.core.data.ai

/**
 * 模型输出的解析与清洗，全部是纯函数——这是本模块唯一可单测的缝。
 *
 * 设计前提：**模型输出不可信**。它会加代码围栏、会在 JSON 前后写"好的，这是我的分析："、
 * 会给出 schema 里没有的字段、会把数字写成字符串、会编造不在候选列表里的文章 id。
 * 所以这里的每个函数都遵循同一条纪律：
 * 1. 先尽力抢救（剥围栏、截 JSON 片段）；
 * 2. 再强制收敛（截断长度、去空去重、夹取区间、非法枚举回落）；
 * 3. 解析彻底失败时返回**带默认值的对象**而不是 null——调用方拿到空结果只是"这项没生成"，
 *    不会崩；这与本项目「宁可少给，不可错给」的取向一致。
 *
 */
object AiParsers {
    const val MAX_KEYWORDS = 8
    const val MAX_SECTIONS = 8
    const val MAX_POINTS = 4
    const val MAX_SUGGESTIONS = 6
    const val MAX_RULES = 6

    /** 单条文本字段的硬上限：超过就截断，防止异常输出把 UI 撑变形。 */
    const val MAX_TEXT_LEN = 60

    // ── 通用工具 ────────────────────────────────────────────────────────────

    /**
     * 从模型输出里抢救出 JSON 片段。
     *
     * 按序尝试：```json 围栏 → 任意 ``` 围栏 → 首尾花括号之间的内容 → 首尾方括号之间的内容。
     * 全部失败返回 null（调用方按"解析失败"处理，返回默认值对象）。
     */
    fun extractJson(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        val fenced = FENCE_REGEX.find(text)
        if (fenced != null) {
            val body = fenced.groupValues[1].trim()
            if (body.isNotEmpty()) return body
        }

        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1)
        }

        val firstBracket = text.indexOf('[')
        val lastBracket = text.lastIndexOf(']')
        if (firstBracket >= 0 && lastBracket > firstBracket) {
            return text.substring(firstBracket, lastBracket + 1)
        }
        return null
    }

    private val FENCE_REGEX = Regex("```(?:json|JSON)?\\s*([\\s\\S]*?)```")

    private inline fun <reified T> decode(raw: String): T? = try {
        val json = extractJson(raw) ?: return null
        com.cycling.rssradar.core.data.ai.AiJson.decodeFromString<T>(json)
    } catch (_: Exception) {
        null
    }

    private inline fun <reified T> decodeOr(raw: String, fallback: T): T = decode<T>(raw) ?: fallback

    /** 清洗字符串列表：去空白、去空、去重、限长、限条数。 */
    fun cleanList(items: List<String>, max: Int, maxLen: Int = MAX_TEXT_LEN): List<String> =
        items.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(max)
            .map { if (it.length > maxLen) it.take(maxLen) else it }

    fun clampScore(value: Int): Int = value.coerceIn(0, 100)

    fun clampText(value: String, maxLen: Int = MAX_TEXT_LEN): String =
        value.trim().let { if (it.length > maxLen) it.take(maxLen) else it }

    /**
     * 归一化枚举值：大写去空白后匹配候选，匹配不上返回 fallback。
     * 模型把 "positive" / "Positive " / "偏正面" 都写得出，一律收成规范值。
     */
    fun normalizeEnum(raw: String, allowed: Set<String>, fallback: String): String {
        val normalized = raw.trim().uppercase()
        if (normalized in allowed) return normalized
        // 中文值兜底：模型偶尔不按 schema 写英文枚举。
        val alias = ENUM_ALIASES[raw.trim()]
        return if (alias != null && alias in allowed) alias else fallback
    }

    private val ENUM_ALIASES = mapOf(
        "未知" to "UNKNOWN", "正常" to "OK", "降频" to "DEGRADED", "失效" to "BROKEN",
    )

    // ── 内容处理类 ──────────────────────────────────────────────────────────

    fun keywords(raw: String): AiKeywordsPayload {
        val parsed = decode<AiKeywordsPayload>(raw) ?: return AiKeywordsPayload()
        return AiKeywordsPayload(keywords = cleanList(parsed.keywords, MAX_KEYWORDS, maxLen = 16))
    }

    fun noise(raw: String): AiNoisePayload {
        val parsed = decode<AiNoisePayload>(raw) ?: return AiNoisePayload()
        return AiNoisePayload(
            value = clampScore(parsed.value),
            isNoise = parsed.isNoise,
            reasons = cleanList(parsed.reasons, 3, maxLen = 40),
            keptPoints = cleanList(parsed.keptPoints, MAX_POINTS, maxLen = 80),
        )
    }

    fun outline(raw: String): AiOutlinePayload {
        val parsed = decode<AiOutlinePayload>(raw) ?: return AiOutlinePayload()
        return AiOutlinePayload(
            gist = clampText(parsed.gist, 100),
            sections = parsed.sections
                .filter { it.heading.isNotBlank() || it.summary.isNotBlank() }
                .take(MAX_SECTIONS)
                .map {
                    AiOutlinePayload.Section(
                        heading = clampText(it.heading, 24),
                        summary = clampText(it.summary, 120),
                        anchor = clampText(it.anchor, 40),
                    )
                },
        )
    }

    fun glossary(raw: String): AiGlossaryPayload {
        val parsed = decode<AiGlossaryPayload>(raw)
        // 模型经常不按 schema 直接甩一句解释，此时整段原文就是最好的释义。
        val explanation = parsed?.explanation?.trim()?.takeIf { it.isNotBlank() } ?: raw.trim()
        return AiGlossaryPayload(
            term = parsed?.term?.let { clampText(it, 24) }.orEmpty(),
            explanation = clampText(explanation, 200),
        )
    }

    fun qa(raw: String): AiQaPayload {
        val parsed = decode<AiQaPayload>(raw)
        if (parsed != null) {
            return AiQaPayload(
                answer = parsed.answer.trim(),
                quotes = cleanList(parsed.quotes, 2, maxLen = 200),
                notFound = parsed.notFound,
            )
        }
        // 模型没按 JSON 输出时，整段当回答用——问答是实时交互，宁可降级也不要弹错误。
        val plain = raw.trim()
        return AiQaPayload(answer = plain, quotes = emptyList(), notFound = plain.isBlank())
    }

    // ── 推荐发现类 ──────────────────────────────────────────────────────────

    fun feedRecommend(raw: String): AiFeedRecommendPayload {
        val parsed = decode<AiFeedRecommendPayload>(raw) ?: return AiFeedRecommendPayload()
        return AiFeedRecommendPayload(
            suggestions = parsed.suggestions
                .filter { it.name.isNotBlank() }
                .take(MAX_SUGGESTIONS)
                .map {
                    AiFeedRecommendPayload.Suggestion(
                        name = clampText(it.name, 24),
                        url = it.url.trim(),
                        route = it.route.trim(),
                        reason = clampText(it.reason, 60),
                    )
                },
        )
    }

    // ── 辅助推送类 ──────────────────────────────────────────────────────────

    fun shareCopy(raw: String): AiSharePayload {
        val parsed = decode<AiSharePayload>(raw) ?: return AiSharePayload()
        return AiSharePayload(
            variants = parsed.variants
                .filter { it.text.isNotBlank() }
                .take(3)
                .map {
                    AiSharePayload.Variant(
                        style = normalizeEnum(it.style, SHARE_STYLES, "SHORT"),
                        text = it.text.trim(),
                    )
                },
        )
    }

    private val SHARE_STYLES = setOf("SHORT", "THREAD", "BULLET")

    fun feedHealth(raw: String): AiHealthPayload {
        val parsed = decode<AiHealthPayload>(raw) ?: return AiHealthPayload()
        return AiHealthPayload(
            status = normalizeEnum(parsed.status, HEALTH_STATUSES, "UNKNOWN"),
            reason = clampText(parsed.reason, 80),
            advice = clampText(parsed.advice, 80),
        )
    }

    private val HEALTH_STATUSES = setOf("OK", "DEGRADED", "BROKEN", "UNKNOWN")

    fun filterRule(raw: String): AiFilterRulePayload {
        val parsed = decode<AiFilterRulePayload>(raw) ?: return AiFilterRulePayload()
        return AiFilterRulePayload(
            rules = parsed.rules
                .filter { it.keyword.isNotBlank() }
                .take(MAX_RULES)
                .map {
                    AiFilterRulePayload.Rule(
                        keyword = clampText(it.keyword, 16),
                        field = normalizeEnum(it.field, RULE_FIELDS, "BOTH"),
                        hits = cleanList(it.hits, 3, maxLen = 60),
                    )
                },
        )
    }

    private val RULE_FIELDS = setOf("TITLE", "SUMMARY", "BOTH")

    // 「空壳判定 isMeaningful」与「统一分发 parse」已收敛到 AiFeatureSpecs——
    // 每项功能的解析、判空、prompt 构建登记在同一行，不再按技术层各开一个 when。
}
