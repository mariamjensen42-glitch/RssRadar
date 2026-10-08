package com.cycling.rssradar.ui.me

import com.cycling.rssradar.core.data.backup.ConflictPolicy
import com.cycling.rssradar.core.data.backup.ImportStrategy

/**
 * 备份域枚举文案的资源映射：core 层的中文 label 是数据口径，
 * 界面展示一律走这里按当前语言取 res。
 *
 * 本文件原叫 FeatureTexts.kt，装着过滤 + 备份两个不相干领域；模块化时过滤的一半
 * 随过滤规则页迁进 feature:settings（ui.settings.FilterRuleTexts），这里只剩备份。
 */
fun ImportStrategy.labelRes(): Int = when (this) {
    ImportStrategy.MERGE -> R.string.backup_strategy_merge
    ImportStrategy.OVERWRITE -> R.string.backup_strategy_overwrite
}

fun ConflictPolicy.labelRes(): Int = when (this) {
    ConflictPolicy.KEEP_LOCAL -> R.string.backup_conflict_keep_local
    ConflictPolicy.KEEP_BACKUP -> R.string.backup_conflict_keep_backup
}
