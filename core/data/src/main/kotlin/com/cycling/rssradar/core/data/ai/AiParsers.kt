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
 * 涉及文章 id 的字段还要额外过 [restrictIds]：模型返回的 id 必须落在本次真正送进 prompt
 * 的候选集内，否则 UI 会指向一篇根本不相关的文章。
 */
object AiParsers {

    const val MAX_TAGS = 6
    const val MAX_KEYWORDS = 8
    const val MAX_CLAIMS = 5
    const val MAX_SECTIONS = 8
    const val MAX_POINTS = 4
    const val MAX_SUGGESTIONS = 6
    const val MAX_TOPICS = 12
    const val MAX_BRIEF_ITEMS = 8
    const val MAX_RULES = 6
    const val MAX_INTERESTS = 10
    const val MAX_SEEDS = 30
    const val MAX_DISCOVER = 12

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

    fun clamp01(value: Double): Double =
        if (value.isNaN() || value.isInfinite()) 0.0 else value.coerceIn(0.0, 1.0)

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
        "正面" to "POSITIVE", "积极" to "POSITIVE", "偏正面" to "POSITIVE",
        "中性" to "NEUTRAL", "客观" to "NEUTRAL", "中立" to "NEUTRAL",
        "负面" to "NEGATIVE", "消极" to "NEGATIVE", "偏负面" to "NEGATIVE",
        "高" to "HIGH", "中" to "MEDIUM", "低" to "LOW", "未知" to "UNKNOWN",
        "正常" to "OK", "降频" to "DEGRADED", "失效" to "BROKEN",
    )

    /**
     * 把模型返回的文章 id 限制在本次真实候选集内。
     *
     * **这是防捏造的最后一道闸**：模型很容易"顺着语义"给出一个看着合理但不存在于本次输入的 id，
     * UI 若是照单全收，用户点进去会看到完全不相关的文章，且无从察觉。
     */
    fun restrictIds(ids: List<Long>, allowed: Collection<Long>): List<Long> {
        if (allowed.isEmpty()) return emptyList()
        val set = allowed.toSet()
        return ids.filter { it in set }.distinct()
    }

    // ── 内容处理类 ──────────────────────────────────────────────────────────

    fun tags(raw: String): AiTagsPayload {
        val parsed = decode<AiTagsPayload>(raw) ?: return AiTagsPayload()
        return AiTagsPayload(tags = cleanList(parsed.tags, MAX_TAGS, maxLen = 12))
    }

    fun keywords(raw: String): AiKeywordsPayload {
        val parsed = decode<AiKeywordsPayload>(raw) ?: return AiKeywordsPayload()
        return AiKeywordsPayload(keywords = cleanList(parsed.keywords, MAX_KEYWORDS, maxLen = 16))
    }

    fun classify(raw: String): AiClassifyPayload {
        val parsed = decode<AiClassifyPayload>(raw) ?: return AiClassifyPayload()
        return AiClassifyPayload(
            topic = clampText(parsed.topic, 12),
            confidence = clamp01(parsed.confidence),
            alternatives = cleanList(parsed.alternatives, 2, maxLen = 12),
        )
    }

    fun sentiment(raw: String): AiSentimentPayload {
        val parsed = decode<AiSentimentPayload>(raw) ?: return AiSentimentPayload()
        return AiSentimentPayload(
            polarity = normalizeEnum(parsed.polarity, POLARITIES, "NEUTRAL"),
            score = clamp01(parsed.score),
            reason = clampText(parsed.reason, 80),
        )
    }

    private val POLARITIES = setOf("POSITIVE", "NEUTRAL", "NEGATIVE")

    fun quality(raw: String): AiQualityPayload {
        val parsed = decode<AiQualityPayload>(raw) ?: return AiQualityPayload()
        return AiQualityPayload(
            overall = clampScore(parsed.overall),
            density = clampScore(parsed.density),
            originality = clampScore(parsed.originality),
            clickbait = clampScore(parsed.clickbait),
            evidence = clampScore(parsed.evidence),
            note = clampText(parsed.note, 40),
        )
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

    fun credibility(raw: String): AiCredibilityPayload {
        val parsed = decode<AiCredibilityPayload>(raw) ?: return AiCredibilityPayload()
        return AiCredibilityPayload(
            level = normalizeEnum(parsed.level, CREDIBILITY_LEVELS, "UNKNOWN"),
            signals = cleanList(parsed.signals, 4, maxLen = 40),
            doubts = cleanList(parsed.doubts, 3, maxLen = 40),
        )
    }

    private val CREDIBILITY_LEVELS = setOf("HIGH", "MEDIUM", "LOW", "UNKNOWN")

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

    fun opinion(raw: String): AiOpinionPayload {
        val parsed = decode<AiOpinionPayload>(raw) ?: return AiOpinionPayload()
        return AiOpinionPayload(
            claims = parsed.claims
                .filter { it.claim.isNotBlank() }
                .take(MAX_CLAIMS)
                .map {
                    AiOpinionPayload.Claim(
                        claim = clampText(it.claim, 100),
                        basis = clampText(it.basis, 100),
                        kind = normalizeEnum(it.kind, CLAIM_KINDS, "VIEW"),
                    )
                },
        )
    }

    private val CLAIM_KINDS = setOf("VIEW", "FACT", "DATA")

    fun dedupe(raw: String): AiDedupePayload {
        val parsed = decode<AiDedupePayload>(raw) ?: return AiDedupePayload()
        val key = clampText(parsed.groupKey, 16)
        return AiDedupePayload(
            groupKey = key,
            // 连组标识都没给，说明模型认为没有同源报道，此时不该被当成主篇。
            isPrimary = parsed.isPrimary && key.isNotBlank(),
            reason = clampText(parsed.reason, 60),
        )
    }

    fun fulltext(raw: String): AiFulltextPayload {
        val parsed = decode<AiFulltextPayload>(raw) ?: return AiFulltextPayload()
        val html = parsed.html.trim()
        return AiFulltextPayload(
            // 说成功却没给内容，一律按失败处理——宁可让用户看到"提取失败"而不是空白页。
            ok = parsed.ok && html.isNotBlank(),
            html = html,
            note = clampText(parsed.note, 60),
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

    fun topics(raw: String): AiTopicsPayload {
        val parsed = decode<AiTopicsPayload>(raw) ?: return AiTopicsPayload()
        return AiTopicsPayload(
            topics = parsed.topics
                .filter { it.name.isNotBlank() }
                .take(MAX_TOPICS)
                .map {
                    AiTopicsPayload.Topic(
                        name = clampText(it.name, 12),
                        articleIds = it.articleIds.filter { id -> id > 0 }.distinct(),
                        summary = clampText(it.summary, 80),
                    )
                },
        )
    }

    fun bubble(raw: String): AiBubblePayload {
        val parsed = decode<AiBubblePayload>(raw) ?: return AiBubblePayload()
        return AiBubblePayload(
            blindSpots = cleanList(parsed.blindSpots, 5, maxLen = 12),
            articleIds = parsed.articleIds.filter { it > 0 }.distinct(),
            note = clampText(parsed.note, 80),
        )
    }

    fun aggregate(raw: String): AiAggregatePayload {
        val parsed = decode<AiAggregatePayload>(raw) ?: return AiAggregatePayload()
        return AiAggregatePayload(
            consensus = cleanList(parsed.consensus, 5, maxLen = 120),
            divergence = cleanList(parsed.divergence, 5, maxLen = 120),
            watch = cleanList(parsed.watch, 5, maxLen = 120),
            sourceIds = parsed.sourceIds.filter { it > 0 }.distinct(),
        )
    }

    fun interestRank(raw: String): AiInterestRankPayload {
        val parsed = decode<AiInterestRankPayload>(raw) ?: return AiInterestRankPayload()
        return AiInterestRankPayload(
            interests = parsed.interests
                .filter { it.name.isNotBlank() }
                .take(MAX_INTERESTS)
                .map {
                    AiInterestRankPayload.Interest(
                        name = clampText(it.name, 12),
                        weight = clamp01(it.weight),
                        feeds = cleanList(it.feeds, 3, maxLen = 20),
                    )
                }
                .sortedByDescending { it.weight },
        )
    }

    fun event(raw: String): AiEventPayload {
        val parsed = decode<AiEventPayload>(raw) ?: return AiEventPayload()
        return AiEventPayload(
            event = clampText(parsed.event, 60),
            timeline = parsed.timeline
                .filter { it.articleId > 0 }
                .map {
                    AiEventPayload.Node(
                        articleId = it.articleId,
                        time = clampText(it.time, 30),
                        headline = clampText(it.headline, 100),
                        outlet = clampText(it.outlet, 20),
                        difference = clampText(it.difference, 100),
                    )
                },
        )
    }

    fun discover(raw: String): AiDiscoverPayload {
        val parsed = decode<AiDiscoverPayload>(raw) ?: return AiDiscoverPayload()
        return AiDiscoverPayload(
            articleIds = parsed.articleIds.filter { it > 0 }.distinct().take(MAX_DISCOVER),
            note = clampText(parsed.note, 80),
        )
    }

    fun coldStart(raw: String): AiColdStartPayload {
        val parsed = decode<AiColdStartPayload>(raw) ?: return AiColdStartPayload()
        return AiColdStartPayload(seeds = cleanList(parsed.seeds, MAX_SEEDS, maxLen = 16))
    }

    // ── 辅助推送类 ──────────────────────────────────────────────────────────

    fun dailyBrief(raw: String): AiBriefPayload {
        val parsed = decode<AiBriefPayload>(raw) ?: return AiBriefPayload()
        return AiBriefPayload(
            headline = clampText(parsed.headline, 80),
            items = parsed.items
                .filter { it.title.isNotBlank() }
                .take(MAX_BRIEF_ITEMS)
                .map {
                    AiBriefPayload.Item(
                        title = clampText(it.title, 60),
                        articleId = it.articleId,
                        why = clampText(it.why, 60),
                    )
                },
            skippable = parsed.skippable.filter { it > 0 }.distinct(),
        )
    }

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

    fun importance(raw: String): AiImportancePayload {
        val parsed = decode<AiImportancePayload>(raw) ?: return AiImportancePayload()
        return AiImportancePayload(
            important = parsed.important,
            score = clampScore(parsed.score),
            reason = clampText(parsed.reason, 40),
        )
    }

    fun feedHealth(raw: String): AiHealthPayload {
        val parsed = decode<AiHealthPayload>(raw) ?: return AiHealthPayload()
        return AiHealthPayload(
            status = normalizeEnum(parsed.status, HEALTH_STATUSES, "UNKNOWN"),
            reason = clampText(parsed.reason, 80),
            advice = clampText(parsed.advice, 80),
        )
    }

    private val HEALTH_STATUSES = setOf("OK", "DEGRADED", "BROKEN", "UNKNOWN")

    fun habit(raw: String): AiHabitPayload {
        val parsed = decode<AiHabitPayload>(raw) ?: return AiHabitPayload()
        return AiHabitPayload(
            activeHours = parsed.activeHours.filter { it in 0..23 }.distinct().sorted(),
            concentration = clamp01(parsed.concentration),
            observations = cleanList(parsed.observations, 3, maxLen = 60),
        )
    }

    fun dailyReport(raw: String): AiReportPayload {
        val parsed = decode<AiReportPayload>(raw) ?: return AiReportPayload()
        return AiReportPayload(
            summary = clampText(parsed.summary, 200),
            highlights = cleanList(parsed.highlights, 4, maxLen = 100),
            missed = parsed.missed.filter { it > 0 }.distinct(),
            observations = cleanList(parsed.observations, 2, maxLen = 80),
        )
    }

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
