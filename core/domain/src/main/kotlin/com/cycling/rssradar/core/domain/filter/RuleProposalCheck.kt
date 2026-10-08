package com.cycling.rssradar.core.domain.filter

/**
 * 过滤规则提案的**本地复核**。
 *
 * 存在的理由：模型会为每条规则附上「会命中这些标题」的示例（`hits`），系统提示词也要求
 * 它逐字复制候选清单里的标题——但**提示词约束不等于事实**。而这条链路的代价是单向的：
 * 规则一旦启用，命中就隐藏，用户此后不会再看到那些文章，也就无从发现自己被屏蔽了什么。
 * 所以正式启用前必须复核：**模型举例里本地查不到的，就是它编的**。
 *
 * 复核用的是**与线上同一套** [FilterRuleEngine]（KEYWORD + 标题 + 全局作用域），
 * 不另写一份匹配逻辑——另写一份就会出现「预览说命中、线上不隐藏」这种不会报错的漂移。
 *
 * 纯函数：候选清单由调用方取好，不碰数据库、不碰 Android。
 */
object RuleProposalCheck {

    /**
     * 复核结果。
     *
     * [checked] 为 false 表示**这次没验**（而不是「验过但都没命中」）——两者必须分得开，
     * 否则界面会把"无法校验"显示成"命中 0 篇"，那是对用户撒谎。
     */
    data class Result(
        val checked: Boolean,
        /** 候选清单里真的会被这条规则命中的标题（本地实算）。 */
        val matched: List<String>,
        /** 模型给了示例、本地却对不上的标题。 */
        val fabricated: List<String>,
    )

    /**
     * 用标题候选复核一条规则。
     *
     * 只在规则**会看标题**时才验：候选清单里只有标题，模型能看到的也只有标题，
     * 所以纯摘要规则的示例根本无从校验——这时如实报「没验」，而不是判它编造（那是冤枉）。
     *
     * @param fields 规则作用的字段。不含 [RuleField.TITLE] 时直接返回未校验。
     */
    fun check(
        keyword: String,
        fields: Set<RuleField>,
        claimedHits: List<String>,
        candidates: List<String>,
    ): Result {
        val probe = keyword.trim()
        if (probe.isEmpty() || RuleField.TITLE !in fields) {
            return Result(checked = false, matched = emptyList(), fabricated = emptyList())
        }

        val engine = FilterRuleEngine(
            listOf(
                FilterRule(
                    name = "",
                    pattern = probe,
                    matchType = RuleMatchType.KEYWORD,
                    fields = setOf(RuleField.TITLE),
                    scopeType = RuleScopeType.GLOBAL,
                    action = RuleAction.HIDE,
                ),
            ),
        )
        fun hides(title: String): Boolean =
            engine.evaluate(RuleTarget(feedId = 0, groupName = "", title = title)).hidden

        val matched = candidates.distinct().filter(::hides)
        val matchedSet = matched.toSet()
        return Result(
            checked = true,
            matched = matched,
            // 举例对不上有两种：清单外的标题，或清单内但规则其实不命中的标题。
            // 两种都要让用户看见——它们都说明这条规则的推理链是断的。
            fabricated = claimedHits.distinct().filterNot { it in matchedSet },
        )
    }
}
