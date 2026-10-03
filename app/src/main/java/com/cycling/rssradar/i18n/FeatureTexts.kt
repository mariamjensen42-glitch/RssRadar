package com.cycling.rssradar.i18n

import com.cycling.rssradar.R
import com.cycling.rssradar.core.data.backup.ConflictPolicy
import com.cycling.rssradar.core.data.backup.ImportStrategy
import com.cycling.rssradar.core.domain.filter.RuleAction
import com.cycling.rssradar.core.domain.filter.RuleField
import com.cycling.rssradar.core.domain.filter.RuleMatchType
import com.cycling.rssradar.core.domain.filter.RuleScopeType

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

fun ImportStrategy.labelRes(): Int = when (this) {
    ImportStrategy.MERGE -> R.string.backup_strategy_merge
    ImportStrategy.OVERWRITE -> R.string.backup_strategy_overwrite
}

fun ConflictPolicy.labelRes(): Int = when (this) {
    ConflictPolicy.KEEP_LOCAL -> R.string.backup_conflict_keep_local
    ConflictPolicy.KEEP_BACKUP -> R.string.backup_conflict_keep_backup
}
