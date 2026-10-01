package com.cycling.rssradar.core.data.filter

import com.cycling.rssradar.core.data.db.FilterRuleDao
import com.cycling.rssradar.core.data.db.FilterRuleEntity
import com.cycling.rssradar.core.domain.filter.FilterRule
import com.cycling.rssradar.core.domain.filter.FilterRuleEngine
import com.cycling.rssradar.core.domain.filter.RuleAction
import com.cycling.rssradar.core.domain.filter.RuleField
import com.cycling.rssradar.core.domain.filter.RuleMatchType
import com.cycling.rssradar.core.domain.filter.RuleScopeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object FilterRuleMapping {

    fun toDomain(entity: FilterRuleEntity): FilterRule = FilterRule(
        id = entity.id,
        name = entity.name,
        enabled = entity.enabled,
        priority = entity.priority,
        matchType = if (entity.matchType == MATCH_REGEX) RuleMatchType.REGEX else RuleMatchType.KEYWORD,
        fields = RuleField.fromMask(entity.fieldMask),
        pattern = entity.pattern,
        caseSensitive = entity.caseSensitive,
        wholeWord = entity.wholeWord,
        scopeType = when (entity.scopeType) {
            SCOPE_FEED -> RuleScopeType.FEED
            SCOPE_GROUP -> RuleScopeType.GROUP
            else -> RuleScopeType.GLOBAL
        },
        scopeId = entity.scopeId,
        action = when (entity.action) {
            ACTION_MARK_READ -> RuleAction.MARK_READ
            ACTION_STAR -> RuleAction.STAR
            ACTION_BOOKMARK -> RuleAction.BOOKMARK
            ACTION_SUPPRESS_NOTIFY -> RuleAction.SUPPRESS_NOTIFY
            else -> RuleAction.HIDE
        },
    )

    fun toEntity(rule: FilterRule, now: Long): FilterRuleEntity = FilterRuleEntity(
        id = rule.id,
        name = rule.name,
        enabled = rule.enabled,
        priority = rule.priority,
        matchType = if (rule.matchType == RuleMatchType.REGEX) MATCH_REGEX else MATCH_KEYWORD,
        fieldMask = RuleField.toMask(rule.fields),
        pattern = rule.pattern,
        caseSensitive = rule.caseSensitive,
        wholeWord = rule.wholeWord,
        scopeType = when (rule.scopeType) {
            RuleScopeType.FEED -> SCOPE_FEED
            RuleScopeType.GROUP -> SCOPE_GROUP
            RuleScopeType.GLOBAL -> SCOPE_GLOBAL
        },
        scopeId = rule.scopeId,
        action = when (rule.action) {
            RuleAction.HIDE -> ACTION_HIDE
            RuleAction.MARK_READ -> ACTION_MARK_READ
            RuleAction.STAR -> ACTION_STAR
            RuleAction.BOOKMARK -> ACTION_BOOKMARK
            RuleAction.SUPPRESS_NOTIFY -> ACTION_SUPPRESS_NOTIFY
        },
        createdAt = if (rule.id == 0L) now else now,
        updatedAt = now,
    )

    const val MATCH_KEYWORD = 0
    const val MATCH_REGEX = 1

    const val SCOPE_GLOBAL = 0
    const val SCOPE_FEED = 1
    const val SCOPE_GROUP = 2

    const val ACTION_HIDE = 0
    const val ACTION_MARK_READ = 1
    const val ACTION_STAR = 2
    const val ACTION_BOOKMARK = 3
    const val ACTION_SUPPRESS_NOTIFY = 4
}

class FilterRuleRepository(private val dao: FilterRuleDao) {

    fun observeAll(): Flow<List<FilterRule>> = dao.observeAll().map { rows -> rows.map(FilterRuleMapping::toDomain) }

    suspend fun getAll(): List<FilterRule> = dao.getAll().map(FilterRuleMapping::toDomain)

    suspend fun engine(): FilterRuleEngine = FilterRuleEngine(getAll())

    suspend fun save(rule: FilterRule): Long {
        val now = System.currentTimeMillis()
        val existing = rule.id
        val priority = if (existing == 0L) nextPriority() else rule.priority
        return dao.upsert(FilterRuleMapping.toEntity(rule.copy(priority = priority), now))
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled, System.currentTimeMillis())

    suspend fun move(id: Long, delta: Int) {
        val rules = dao.getAll()
        val index = rules.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, rules.size - 1)
        if (target == index) return
        val reordered = rules.toMutableList().apply { add(target, removeAt(index)) }
        val now = System.currentTimeMillis()
        reordered.forEachIndexed { position, entity -> dao.setPriority(entity.id, position, now) }
    }

    suspend fun replaceAll(rules: List<FilterRule>) {
        val now = System.currentTimeMillis()
        rules.forEachIndexed { index, rule ->
            dao.upsert(FilterRuleMapping.toEntity(rule.copy(id = 0L, priority = index), now))
        }
    }

    suspend fun count(): Int = dao.count()

    private suspend fun nextPriority(): Int = (dao.getAll().maxOfOrNull { it.priority } ?: -1) + 1
}
