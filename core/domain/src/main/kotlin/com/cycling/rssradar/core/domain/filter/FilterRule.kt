package com.cycling.rssradar.core.domain.filter

enum class RuleMatchType { KEYWORD, REGEX }

enum class RuleField(val bit: Int) {
    TITLE(1),
    SUMMARY(2),
    CONTENT(4),
    AUTHOR(8),
    ;

    companion object {
        fun fromMask(mask: Int): Set<RuleField> {
            val picked = entries.filter { mask and it.bit != 0 }
            return if (picked.isEmpty()) entries.toSet() else picked.toSet()
        }

        fun toMask(fields: Set<RuleField>): Int = fields.fold(0) { acc, field -> acc or field.bit }
    }
}

enum class RuleScopeType { GLOBAL, FEED, GROUP }

enum class RuleAction { HIDE, MARK_READ, STAR, BOOKMARK, SUPPRESS_NOTIFY }

data class FilterRule(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val matchType: RuleMatchType = RuleMatchType.KEYWORD,
    val fields: Set<RuleField> = RuleField.entries.toSet(),
    val pattern: String,
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = false,
    val scopeType: RuleScopeType = RuleScopeType.GLOBAL,
    val scopeId: String? = null,
    val action: RuleAction = RuleAction.HIDE,
)

data class RuleTarget(
    val feedId: Long,
    val groupName: String,
    val title: String,
    val summary: String = "",
    val content: String = "",
    val author: String = "",
)

data class RuleOutcome(
    val hidden: Boolean = false,
    val markRead: Boolean = false,
    val star: Boolean = false,
    val bookmark: Boolean = false,
    val suppressNotify: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !hidden && !markRead && !star && !bookmark && !suppressNotify

    fun merge(other: RuleOutcome): RuleOutcome = RuleOutcome(
        hidden = hidden || other.hidden,
        markRead = markRead || other.markRead,
        star = star || other.star,
        bookmark = bookmark || other.bookmark,
        suppressNotify = suppressNotify || other.suppressNotify,
    )
}
