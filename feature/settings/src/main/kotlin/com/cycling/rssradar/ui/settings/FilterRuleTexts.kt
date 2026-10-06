package com.cycling.rssradar.ui.settings

import com.cycling.rssradar.core.domain.filter.RuleAction
import com.cycling.rssradar.core.domain.filter.RuleField
import com.cycling.rssradar.core.domain.filter.RuleMatchType
import com.cycling.rssradar.core.domain.filter.RuleScopeType

/**
 * 过滤规则的枚举文案（ADR-0017：文案映射随领域走）。
 *
 * 原先与备份策略的文案挤在 app/i18n/FeatureTexts.kt —— 那个文件是「按技术层归拢」的产物。
 * 模块化时按领域拆开：过滤随过滤规则页进本模块，备份的留在 app（等 feature:me 批次归位）。
 */
fun RuleMatchType.labelRes(): Int = when (this) {
    RuleMatchType.KEYWORD -> R.string.rule_match_keyword
    RuleMatchType.REGEX -> R.string.rule_match_regex
}

fun RuleField.labelRes(): Int = when (this) {
    RuleField.TITLE -> R.string.rule_field_title
    RuleField.SUMMARY -> R.string.rule_field_summary
    RuleField.CONTENT -> R.string.rule_field_content
    RuleField.AUTHOR -> R.string.rule_field_author
}

fun RuleScopeType.labelRes(): Int = when (this) {
    RuleScopeType.GLOBAL -> R.string.rule_scope_global
    RuleScopeType.FEED -> R.string.rule_scope_feed
    RuleScopeType.GROUP -> R.string.rule_scope_group
}

fun RuleAction.labelRes(): Int = when (this) {
    RuleAction.HIDE -> R.string.rule_action_hide
    RuleAction.MARK_READ -> R.string.rule_action_mark_read
    RuleAction.STAR -> R.string.rule_action_star
    RuleAction.BOOKMARK -> R.string.rule_action_bookmark
    RuleAction.SUPPRESS_NOTIFY -> R.string.rule_action_suppress
}
