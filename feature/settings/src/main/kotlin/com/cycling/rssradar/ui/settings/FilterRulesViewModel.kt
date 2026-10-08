package com.cycling.rssradar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.core.data.db.dao.ArticleDao
import com.cycling.rssradar.core.data.db.DEFAULT_GROUP
import com.cycling.rssradar.core.data.db.dao.FeedDao
import com.cycling.rssradar.core.data.db.entity.FeedEntity
import com.cycling.rssradar.core.data.ai.AiFilterRuleDrafter
import com.cycling.rssradar.core.data.repository.FeedRepository
import com.cycling.rssradar.core.data.filter.FilterRuleRepository
import com.cycling.rssradar.core.data.store.prefs.AiFeatureStore
import com.cycling.rssradar.core.domain.filter.FilterRule
import com.cycling.rssradar.core.domain.filter.RuleAction
import com.cycling.rssradar.core.domain.filter.RuleField
import com.cycling.rssradar.core.domain.filter.RuleMatchType
import com.cycling.rssradar.core.domain.filter.RuleScopeType
import com.cycling.rssradar.core.domain.search.SearchQueryBuilder
import com.cycling.rssradar.core.model.AiFeature
import com.cycling.rssradar.core.ui.text.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class FilterRulesViewModel @Inject constructor(
    private val repository: FilterRuleRepository,
    private val feedRepository: FeedRepository,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val aiFeatureStore: AiFeatureStore,
    /** AI 过滤规则提案（生成 + 本地复核）。 */
    private val drafter: AiFilterRuleDrafter,
) : ViewModel() {

    data class UiState(
        val rules: List<FilterRule> = emptyList(),
        val feeds: List<FeedEntity> = emptyList(),
        val groups: List<String> = emptyList(),
        val editing: FilterRule? = null,
        val previewCount: Int? = null,
        val message: UiText? = null,
        /** 「智能过滤规则生成」这项功能开没开——没开时界面给解释而不是让调用白跑一趟。 */
        val aiEnabled: Boolean = false,
        /** AI 提案面板状态；null = 面板没打开。 */
        val drafting: DraftUi? = null,
    )

    /** AI 提案面板：描述 → 生成 → 逐条复核 → 勾选。 */
    data class DraftUi(
        val description: String = "",
        val loading: Boolean = false,
        val proposals: List<ProposalUi> = emptyList(),
        /**
         * 这次没能产出提案的可展示原因（未开启 / 额度用尽 / 没有候选 / 调用失败）。
         *
         * 与"生成了但一条都没通过"分开表达：前者是"没跑成"，后者是"跑了但模型很克制"，
         * 混成一句话用户会以为功能坏了。
         */
        val problem: UiText? = null,
    )

    data class ProposalUi(
        val proposal: AiFilterRuleDrafter.Proposal,
        val selected: Boolean = true,
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
            aiFeatureStore.state.collect { settings ->
                _state.value = _state.value.copy(aiEnabled = settings.isEnabled(AiFeature.FILTER_RULE))
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
            val escaped = SearchQueryBuilder.escapeLike(rule.pattern)
            val hits = runCatching { articleDao.countMatching("%$escaped%") }.getOrNull()
            _state.value = _state.value.copy(previewCount = hits)
        }
    }

    // ── AI 生成（生成 → 本地复核 → 启用） ────────────────────────────────

    /**
     * 打开提案面板。
     *
     * 每次都从空白开始，不复用上一次的描述与提案：重开面板意味着"再提一个需求"，
     * 屏幕上留着上一次的结果只会让用户以为它是新的。
     */
    fun openDraft() {
        _state.value = _state.value.copy(drafting = DraftUi())
    }

    fun closeDraft() {
        _state.value = _state.value.copy(drafting = null)
    }

    fun updateDraftDescription(text: String) {
        val draft = _state.value.drafting ?: return
        _state.value = _state.value.copy(drafting = draft.copy(description = text))
    }

    fun toggleProposal(index: Int) {
        val draft = _state.value.drafting ?: return
        val next = draft.proposals.mapIndexed { i, item ->
            if (i == index) item.copy(selected = !item.selected) else item
        }
        _state.value = _state.value.copy(drafting = draft.copy(proposals = next))
    }

    /**
     * 生成提案。
     *
     * 功能没开就直接不发请求：`runWithContext` 只会返回 Skipped，绕一圈白等一次网络与限流，
     * 界面按 [UiState.aiEnabled] 给解释（含「去开启」入口）。
     */
    fun generateProposals() {
        val draft = _state.value.drafting ?: return
        if (draft.loading || !_state.value.aiEnabled) return
        _state.value = _state.value.copy(
            drafting = draft.copy(loading = true, problem = null, proposals = emptyList()),
        )
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) { drafter.draft(draft.description) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            // 面板可能已被关掉；这时结果无处可放，直接丢掉而不是把面板重新弹回来。
            val current = _state.value.drafting ?: return@launch
            _state.value = _state.value.copy(
                drafting = current.copy(
                    loading = false,
                    proposals = result?.proposals.orEmpty().map { ProposalUi(it) },
                    problem = when {
                        // 从 core:data 带回来的原因（未开启 / 额度用尽 / 没有候选 /
                        // AI 没返回有效结果）与 Outcome.Failed/Skipped 同一来源，
                        // 属"不可翻译的外部原因"，按既有约定走 Raw。
                        //
                        // 这里没有"生成成功但一条提案都没有"的分支：模型给出空规则时
                        // 会被 AiFeatureSpecs.isMeaningful 拦下、runner 返回 Failed，
                        // 也就是走下面这条 Raw。给它留分支只会留出一段永不执行的代码。
                        result == null -> UiText.res(R.string.rule_ai_failed)
                        result.problem != null -> UiText.Raw(result.problem!!)
                        else -> null
                    },
                ),
            )
        }
    }

    /**
     * 启用选中的提案。
     *
     * 生成的规则一律是**全部订阅 + 隐藏（归档）**：用户描述的就是"不想看到什么"，
     * 作用域当然是他自己的整个订阅列表。想改成"标记已读"或限定某个源，启用后到规则编辑器里改——
     * 把三个下拉塞进提案面板，会让"看一眼再确认"这一步变成一次配置。
     *
     * 收尾与手动保存走同一条路径：**用全部启用规则**重扫一遍，而不是只应用刚加的这几条
     * （优先级合并的结果才是线上真正生效的那份）。
     */
    fun applyProposals() {
        val draft = _state.value.drafting ?: return
        if (draft.loading) return
        val picked = draft.proposals.filter { it.selected }
        if (picked.isEmpty()) {
            _state.value = _state.value.copy(message = UiText.res(R.string.rule_ai_nothing_selected))
            return
        }
        viewModelScope.launch {
            picked.forEach { item ->
                repository.save(
                    FilterRule(
                        name = item.proposal.keyword,
                        pattern = item.proposal.keyword,
                        matchType = RuleMatchType.KEYWORD,
                        fields = item.proposal.fields,
                        scopeType = RuleScopeType.GLOBAL,
                        action = RuleAction.HIDE,
                    ),
                )
            }
            val affected = runCatching { feedRepository.applyFilterRules(repository.engine()) }.getOrNull()
            _state.value = _state.value.copy(
                drafting = null,
                message = when {
                    affected == null -> UiText.res(R.string.rule_ai_added, picked.size)
                    affected.total == 0 -> UiText.res(R.string.rule_ai_added_no_effect, picked.size)
                    else -> UiText.res(R.string.rule_ai_added_applied, picked.size, affected.total)
                },
            )
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
