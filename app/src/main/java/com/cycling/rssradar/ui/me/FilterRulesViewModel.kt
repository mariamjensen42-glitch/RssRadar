package com.cycling.rssradar.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.R
import com.cycling.rssradar.core.data.db.dao.ArticleDao
import com.cycling.rssradar.core.data.db.DEFAULT_GROUP
import com.cycling.rssradar.core.data.db.dao.FeedDao
import com.cycling.rssradar.core.data.db.entity.FeedEntity
import com.cycling.rssradar.core.data.repository.FeedRepository
import com.cycling.rssradar.core.data.filter.FilterRuleRepository
import com.cycling.rssradar.core.domain.filter.FilterRule
import com.cycling.rssradar.core.domain.filter.RuleMatchType
import com.cycling.rssradar.core.ui.text.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FilterRulesViewModel @Inject constructor(
    private val repository: FilterRuleRepository,
    private val feedRepository: FeedRepository,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
) : ViewModel() {

    data class UiState(
        val rules: List<FilterRule> = emptyList(),
        val feeds: List<FeedEntity> = emptyList(),
        val groups: List<String> = emptyList(),
        val editing: FilterRule? = null,
        val previewCount: Int? = null,
        val message: UiText? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAll().collect { rules ->
                _state.value = _state.value.copy(rules = rules)
            }
        }
        viewModelScope.launch {
            val feeds = feedDao.getAll()
            _state.value = _state.value.copy(
                feeds = feeds,
                groups = feeds.map { it.groupName.ifBlank { DEFAULT_GROUP } }.distinct().sorted(),
            )
        }
    }

    fun startCreate() {
        _state.value = _state.value.copy(editing = FilterRule(name = "", pattern = ""), previewCount = null)
    }

    fun startEdit(rule: FilterRule) {
        _state.value = _state.value.copy(editing = rule, previewCount = null)
    }

    fun cancelEdit() {
        _state.value = _state.value.copy(editing = null, previewCount = null)
    }

    fun save(rule: FilterRule) {
        if (rule.name.isBlank() || rule.pattern.isBlank()) {
            _state.value = _state.value.copy(message = UiText.res(R.string.rule_incomplete))
            return
        }
        viewModelScope.launch {
            repository.save(rule)
            _state.value = _state.value.copy(editing = null, previewCount = null)
            // 规则只在"新文章入库"时判定是不够的：用户配「含剧透就隐藏」期待的是立刻生效。
            // 保存后全库重扫一遍（用**全部启用规则**，不是只这一条——优先级合并的结果才对）
            val affected = runCatching { feedRepository.applyFilterRules(repository.engine()) }.getOrNull()
            _state.value = _state.value.copy(
                message = when {
                    affected == null -> UiText.res(R.string.rule_saved)
                    affected.total == 0 -> UiText.res(R.string.rule_saved_no_effect)
                    else -> UiText.res(R.string.rule_saved_applied, affected.total)
                },
            )
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            _state.value = _state.value.copy(editing = null, message = UiText.res(R.string.rule_deleted))
        }
    }

    fun toggle(rule: FilterRule, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(rule.id, enabled)
            // 启用一条隐藏规则同样要立刻生效；停用不回溯（已被删的文章不复活）
            if (!enabled) return@launch
            val affected = runCatching { feedRepository.applyFilterRules(repository.engine()) }.getOrNull() ?: return@launch
            if (affected.total > 0) {
                _state.value = _state.value.copy(message = UiText.res(R.string.rule_saved_applied, affected.total))
            }
        }
    }

    fun move(rule: FilterRule, delta: Int) {
        viewModelScope.launch { repository.move(rule.id, delta) }
    }

    /**
     * 命中预览：只对关键词型给估算（LIKE 匹配标题/摘要/正文）。
     * 正则型不猜——算不出来就给 null，UI 如实说"无法预估"，不编一个假数字。
     */
    fun preview(rule: FilterRule) {
        if (rule.pattern.isBlank() || rule.matchType != RuleMatchType.KEYWORD) {
            _state.value = _state.value.copy(previewCount = null)
            return
        }
        viewModelScope.launch {
            val escaped = rule.pattern
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            val hits = runCatching { articleDao.countMatching("%$escaped%") }.getOrNull()
            _state.value = _state.value.copy(previewCount = hits)
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
