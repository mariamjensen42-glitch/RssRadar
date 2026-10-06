package com.cycling.rssradar.core.domain.filter

class FilterRuleEngine(rules: List<FilterRule>) {

    private data class Compiled(val rule: FilterRule, val regex: Regex?)

    private val compiled: List<Compiled> = rules
        .asSequence()
        .filter { it.enabled && it.pattern.isNotBlank() }
        .sortedBy { it.priority }
        .mapNotNull { rule ->
            when (rule.matchType) {
                RuleMatchType.KEYWORD -> Compiled(rule, null)
                RuleMatchType.REGEX -> runCatching {
                    Regex(
                        rule.pattern,
                        if (rule.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE),
                    )
                }.getOrNull()?.let { Compiled(rule, it) }
            }
        }
        .toList()

    fun evaluate(target: RuleTarget): RuleOutcome {
        var outcome = RuleOutcome()
        for (item in compiled) {
            if (!appliesTo(item.rule, target)) continue
            if (!matches(item, target)) continue
            outcome = outcome.merge(actionOf(item.rule.action))
        }
        return outcome
    }

    fun suppressesNotify(target: RuleTarget): Boolean = evaluate(target).suppressNotify

    private fun appliesTo(rule: FilterRule, target: RuleTarget): Boolean = when (rule.scopeType) {
        RuleScopeType.GLOBAL -> true
        RuleScopeType.FEED -> rule.scopeId == target.feedId.toString()
        RuleScopeType.GROUP -> rule.scopeId == target.groupName
    }

    private fun matches(item: Compiled, target: RuleTarget): Boolean {
        val rule = item.rule
        for (field in rule.fields) {
            val text = textOf(target, field)
            if (text.isEmpty()) continue
            val hit = when (rule.matchType) {
                RuleMatchType.KEYWORD -> KeywordMatcher.matches(
                    text = text,
                    keyword = rule.pattern,
                    caseSensitive = rule.caseSensitive,
                    wholeWord = rule.wholeWord,
                )

                RuleMatchType.REGEX -> item.regex?.containsMatchIn(text) == true
            }
            if (hit) return true
        }
        return false
    }

    private fun textOf(target: RuleTarget, field: RuleField): String = when (field) {
        RuleField.TITLE -> target.title
        RuleField.SUMMARY -> target.summary
        RuleField.CONTENT -> target.content
        RuleField.AUTHOR -> target.author
    }

    private fun actionOf(action: RuleAction): RuleOutcome = when (action) {
        RuleAction.HIDE -> RuleOutcome(hidden = true)
        RuleAction.MARK_READ -> RuleOutcome(markRead = true)
        RuleAction.STAR -> RuleOutcome(star = true)
        RuleAction.BOOKMARK -> RuleOutcome(bookmark = true)
        RuleAction.SUPPRESS_NOTIFY -> RuleOutcome(suppressNotify = true)
    }
}
