package com.cycling.rssradar.ui.subscriptions

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.core.data.db.DEFAULT_GROUP
import com.cycling.rssradar.core.data.db.entity.FeedEntity
import com.cycling.rssradar.core.data.maintenance.ClearArticlesResult
import com.cycling.rssradar.core.data.repository.FeedRepository
import com.cycling.rssradar.core.data.service.SubscriptionFlow
import com.cycling.rssradar.core.model.GROUP_DESIGN
import com.cycling.rssradar.core.model.GROUP_DEV
import com.cycling.rssradar.core.model.GROUP_TECH
import com.cycling.rssradar.core.model.FeedSortMode
import com.cycling.rssradar.core.data.store.prefs.FeedSortStore
import com.cycling.rssradar.core.data.store.prefs.GroupStore
import com.cycling.rssradar.core.domain.rss.FeedFailureCategory
import com.cycling.rssradar.core.domain.rss.FeedHealth
import com.cycling.rssradar.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


/** 订阅 + 未读数，UI 直接消费。[failure] 非空 = 该源当前处于失效状态（#82）。 */
data class FeedWithUnread(
    val feed: FeedEntity,
    val unreadCount: Int,
    val failure: FeedFailureCategory? = null,
)

/** 一个分组下的所有订阅。 */
data class GroupSectionUi(val group: String, val feeds: List<FeedWithUnread>)

/**
 * 订阅页的渲染输入快照，唯一产出点是 [SubscriptionsViewModel.uiState]。
 * 定义在 VM 侧（而不是 Screen 侧）：状态是 VM 的契约，Screen 只是消费者。
 */
data class SubscriptionsUiState(
    val groups: List<GroupSectionUi> = emptyList(),
    val expandedIds: Set<String> = emptySet(),
    val totalUnread: Int = 0,
    val groupOptions: List<String> = emptyList(),
    val sortMode: FeedSortMode = FeedSortMode.BY_NAME,
    val unhealthyOnly: Boolean = false,
    val unhealthyCount: Int = 0,
    val unhealthyFeeds: List<FeedWithUnread> = emptyList(),
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val message: String? = null,
)

/** 排序所需的三股数据流快照（feeds / 未读数 / 每源最近文章时间）。 */
private data class FeedInputs(
    val feeds: List<FeedEntity>,
    val unread: Map<Long, Int>,
    val latest: Map<Long, Long>,
)

/**
 * 订阅源的分组归属归一：groupName 为空串的源归 [DEFAULT_GROUP]。
 *
 * 抽出来是因为它有**两个消费者**——列表分组与「删除分组内全部订阅」的目标筛选。
 * 各写一遍 `ifBlank { DEFAULT_GROUP }` 看着无害，但两边一旦走偏，表现是
 * 「列表里明明归在这一组，按组删除却漏掉它们」这种不会报错的静默错误。
 */
private fun groupOfFeed(feed: FeedEntity): String = feed.groupName.ifBlank { DEFAULT_GROUP }

/** 订阅页事件（候选 A，ADR-0003）。 */
sealed interface SubscriptionsIntent {
    data class ToggleGroup(val group: String) : SubscriptionsIntent
    data object MarkAllRead : SubscriptionsIntent
    /** 订阅列表排序方式（按名称/最近更新/未读数），选择后持久化并立即生效。 */
    data class SelectSort(val mode: FeedSortMode) : SubscriptionsIntent
    data object ConsumeMessage : SubscriptionsIntent
    data class CreateGroup(val name: String) : SubscriptionsIntent
    data class RenameGroup(val oldName: String, val newName: String) : SubscriptionsIntent
    data class DeleteGroup(val name: String) : SubscriptionsIntent
    data class MoveFeed(val feedId: Long, val targetGroup: String) : SubscriptionsIntent
    /** 批量移动（issue #7）：进入/退出多选、勾选、清空勾选、执行移动。 */
    data object ToggleSelectionMode : SubscriptionsIntent
    data class ToggleFeedSelected(val feedId: Long) : SubscriptionsIntent
    data class MoveSelectedFeeds(val targetGroup: String) : SubscriptionsIntent
    /** 批量删除（多选模式）：删除全部勾选的订阅源，文章级联删除。 */
    data object DeleteSelectedFeeds : SubscriptionsIntent
    /** 清空文章（issue #8）：只删文章，源与分组都保留；收藏/稍后读豁免。 */
    data class ClearFeedArticles(val feedId: Long, val feedTitle: String) : SubscriptionsIntent
    data class ClearGroupArticles(val group: String) : SubscriptionsIntent

    /**
     * 删除分组内全部订阅源（文章级联删除）。
     *
     * 与 [DeleteGroup] 是两件事，别混：那个**只把订阅移进默认分组**、一个都不删；
     * 这个删的是订阅源本身。也与 [ClearGroupArticles] 不同——那个保源删文章。
     */
    data class DeleteGroupFeeds(val group: String) : SubscriptionsIntent
    /** Feed 级预设：全文抓取开关（issue #9）。 */
    data class SetFullContentEnabled(val feedId: Long, val enabled: Boolean) : SubscriptionsIntent
    data class RenameFeed(val feedId: Long, val title: String) : SubscriptionsIntent
    data class DeleteFeed(val feedId: Long, val feedTitle: String) : SubscriptionsIntent
    data class ImportOpml(val uri: Uri) : SubscriptionsIntent
    /** OPML 导出（#4）：把全部订阅源写进 [uri]（SAF 另存为，用户决定存哪）。 */
    data class ExportOpml(val uri: Uri) : SubscriptionsIntent
    /** 自动同步开关（issue #58）：屏蔽后不参与自动同步，手动刷新照常。 */
    data class SetSyncEnabled(val feedId: Long, val enabled: Boolean) : SubscriptionsIntent
    /** Feed 级通知开关（#31）。 */
    data class SetNotificationsEnabled(val feedId: Long, val enabled: Boolean) : SubscriptionsIntent

    /** 内容类型（ADR-0014）：改的是列表浏览形态，不动数据。 */
    data class SetContentType(val feedId: Long, val contentType: Int) : SubscriptionsIntent

    /**
     * 订阅源级 AI 摘要提示词（AI 智能功能模块）。
     * 传 null 或空白 = 清除覆盖，回落到内置模板。
     */
    data class SetFeedSummaryPrompt(val feedId: Long, val prompt: String?) : SubscriptionsIntent

    /** 订阅源级「刷新后自动生成摘要」开关。null 语义由仓储层解释为"跟随全局"。 */
    data class SetFeedAutoSummary(val feedId: Long, val enabled: Boolean) : SubscriptionsIntent

    /** 只看失效源（#82）：订阅列表在「全部」与「仅失效」之间切换。 */
    data object ToggleUnhealthyFilter : SubscriptionsIntent

    /** 一键删除全部失效源（#82）：文章级联删除，UI 侧有二次确认。 */
    data object DeleteUnhealthyFeeds : SubscriptionsIntent
}

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val repository: FeedRepository,
    private val subscriptionFlow: SubscriptionFlow,
    private val groupStore: GroupStore,
    private val feedSortStore: FeedSortStore,
    @param:ApplicationContext private val appContext: Context,
    /** AI 智能功能模块：订阅源级 AI 配置（摘要提示词覆盖与自动化开关）。 */
    private val feedAiProfileDao: com.cycling.rssradar.core.data.db.FeedAiProfileDao,
) : ViewModel(), MviViewModel<SubscriptionsIntent> {

    private val _expandedIds = MutableStateFlow(setOf(GROUP_TECH, GROUP_DEV, GROUP_DESIGN))
    val expandedGroupIds: StateFlow<Set<String>> = _expandedIds.asStateFlow()

    /** 只看失效源（#82）。 */
    private val _unhealthyOnly = MutableStateFlow(false)
    val unhealthyOnly: StateFlow<Boolean> = _unhealthyOnly.asStateFlow()

    /** 当前失效源数量（筛选入口的角标数字，来自 DB 字段推导，不估算）。 */
    val unhealthyCount: StateFlow<Int> = repository.observeFeeds()
        .map { feeds -> feeds.count { FeedHealth.isUnhealthy(it.consecutiveFailures, it.failureReason) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 批量移动的多选模式（issue #7）：开启后列表行变勾选行，整行点击 = 勾选而非进文章列表。 */
    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedFeedIds = MutableStateFlow(emptySet<Long>())
    val selectedFeedIds: StateFlow<Set<Long>> = _selectedFeedIds.asStateFlow()

    /** 分组注册表：保证空分组也显示。直连 GroupStore 的流，不再自持一份需要手动刷新的副本。 */
    val groupsList: StateFlow<List<String>> = groupStore.state

    val groups: StateFlow<List<GroupSectionUi>> =
        combine(
            repository.observeFeeds(),
            repository.observeFeedUnreadCounts(),
            repository.observeFeedLatestTimes(),
        ) { feeds, unread, latest -> FeedInputs(feeds, unread, latest) }
            .combine(groupsList) { inputs, registered -> inputs to registered }
            .combine(feedSortStore.state) { (inputs, registered), sort ->
                groupFeeds(inputs.feeds, inputs.unread, inputs.latest, registered, sort)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前订阅列表排序方式（持久化，重启后保持）。 */
    val sortMode: StateFlow<FeedSortMode> = feedSortStore.state

    val totalUnread: StateFlow<Int> = repository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 按 id 取单条订阅（#31 FeedAction 目的地解析 feed 用）。保持 fun：状态 producer，非事件。 */
    fun getFeed(feedId: Long): StateFlow<FeedEntity?> =
        repository.observeFeeds()
            .map { list -> list.find { it.id == feedId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 失效源拍平列表（#82「只看失效源」）：绕过分组结构直达，
     * 与搜索命中同一展示形态。failure 分类已推导好，UI 不再判阈值。
     */
    val unhealthyFeeds: StateFlow<List<FeedWithUnread>> =
        combine(repository.observeFeeds(), repository.observeFeedUnreadCounts()) { feeds, unread ->
            feeds.asSequence()
                .map { feed -> withFailure(feed, unread[feed.id] ?: 0) }
                .filter { it.failure != null }
                .sortedBy { it.feed.title }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 失效分类的推导只在此一处（VM 内）：DB 字段 → FeedHealth → UI。 */
    private fun withFailure(feed: FeedEntity, unread: Int): FeedWithUnread =
        FeedWithUnread(feed, unread, FeedHealth.categoryOf(feed.consecutiveFailures, feed.failureReason))

    /**
     * 按 id 取订阅源的 AI 配置（摘要提示词与自动化开关）。
     * 与 [getFeed] 同形态：Room 的 Flow 会随写入自动重放，编辑后界面立即反映。
     */
    fun observeFeedAiProfile(feedId: Long): StateFlow<com.cycling.rssradar.core.data.db.FeedAiProfileEntity?> =
        feedAiProfileDao.observe(feedId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    /**
     * 订阅页的单一状态快照：11 条流合成一份，UI 侧只订阅这一条。
     *
     * 分组 combine 是因为 Flow 的 combine 最多接 5 个流，分组只影响构造、不改对外语义。
     */
    val uiState: StateFlow<SubscriptionsUiState> = combine(
        combine(groups, expandedGroupIds, totalUnread, groupsList, sortMode, ::CoreInputs),
        combine(unhealthyOnly, unhealthyCount, unhealthyFeeds, ::HealthInputs),
        combine(selectionMode, selectedFeedIds, uiMessage, ::SelectionInputs),
    ) { core, health, sel ->
        SubscriptionsUiState(
            groups = core.groups,
            expandedIds = core.expandedIds,
            totalUnread = core.totalUnread,
            groupOptions = core.groupOptions,
            sortMode = core.sortMode,
            unhealthyOnly = health.unhealthyOnly,
            unhealthyCount = health.unhealthyCount,
            unhealthyFeeds = health.unhealthyFeeds,
            selectionMode = sel.selectionMode,
            selectedIds = sel.selectedIds,
            message = sel.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionsUiState())

    private data class CoreInputs(
        val groups: List<GroupSectionUi>,
        val expandedIds: Set<String>,
        val totalUnread: Int,
        val groupOptions: List<String>,
        val sortMode: FeedSortMode,
    )

    private data class HealthInputs(
        val unhealthyOnly: Boolean,
        val unhealthyCount: Int,
        val unhealthyFeeds: List<FeedWithUnread>,
    )

    private data class SelectionInputs(
        val selectionMode: Boolean,
        val selectedIds: Set<Long>,
        val message: String?,
    )

    override fun onIntent(intent: SubscriptionsIntent) {
        when (intent) {
            is SubscriptionsIntent.ToggleGroup -> toggleGroup(intent.group)
            SubscriptionsIntent.MarkAllRead -> markAllRead()
            is SubscriptionsIntent.SelectSort -> selectSort(intent.mode)
            SubscriptionsIntent.ConsumeMessage -> _uiMessage.value = null
            is SubscriptionsIntent.CreateGroup -> createGroup(intent.name)
            is SubscriptionsIntent.RenameGroup -> renameGroup(intent.oldName, intent.newName)
            is SubscriptionsIntent.DeleteGroup -> deleteGroup(intent.name)
            is SubscriptionsIntent.MoveFeed -> moveFeed(intent.feedId, intent.targetGroup)
            SubscriptionsIntent.ToggleSelectionMode -> toggleSelectionMode()
            is SubscriptionsIntent.ToggleFeedSelected -> toggleFeedSelected(intent.feedId)
            is SubscriptionsIntent.MoveSelectedFeeds -> moveSelectedFeeds(intent.targetGroup)
            SubscriptionsIntent.DeleteSelectedFeeds -> deleteSelectedFeeds()
            is SubscriptionsIntent.ClearFeedArticles -> clearFeedArticles(intent.feedId, intent.feedTitle)
            is SubscriptionsIntent.ClearGroupArticles -> clearGroupArticles(intent.group)
            is SubscriptionsIntent.DeleteGroupFeeds -> deleteGroupFeeds(intent.group)
            is SubscriptionsIntent.SetFullContentEnabled -> setFullContentEnabled(intent.feedId, intent.enabled)
            is SubscriptionsIntent.RenameFeed -> renameFeed(intent.feedId, intent.title)
            is SubscriptionsIntent.DeleteFeed -> deleteFeed(intent.feedId, intent.feedTitle)
            is SubscriptionsIntent.ImportOpml -> importOpml(intent.uri)
            is SubscriptionsIntent.ExportOpml -> exportOpml(intent.uri)
            is SubscriptionsIntent.SetSyncEnabled -> setSyncEnabled(intent.feedId, intent.enabled)
            is SubscriptionsIntent.SetNotificationsEnabled -> setNotificationsEnabled(intent.feedId, intent.enabled)
            is SubscriptionsIntent.SetContentType -> setContentType(intent.feedId, intent.contentType)
            is SubscriptionsIntent.SetFeedSummaryPrompt -> setFeedSummaryPrompt(intent.feedId, intent.prompt)
            is SubscriptionsIntent.SetFeedAutoSummary -> setFeedAutoSummary(intent.feedId, intent.enabled)
            SubscriptionsIntent.ToggleUnhealthyFilter -> _unhealthyOnly.value = !_unhealthyOnly.value
            SubscriptionsIntent.DeleteUnhealthyFeeds -> deleteUnhealthyFeeds()
        }
    }

    /**
     * 一键删除全部失效源：删除清单执行时从 DB 现取（不走 unhealthyFeeds StateFlow——
     * 它 WhileSubscribed，且弹窗期间数据可能变化），判定与 #82 同一函数，不另立标准。
     * 文章级联删除，不可逆；确认对话框在 UI 侧。
     */
    private fun deleteUnhealthyFeeds() {
        viewModelScope.launch {
            val targets = repository.observeFeeds().first()
                .filter { FeedHealth.isUnhealthy(it.consecutiveFailures, it.failureReason) }
            if (targets.isEmpty()) {
                _uiMessage.value = "没有失效的订阅源"
                return@launch
            }
            repository.deleteFeeds(targets.map { it.id })
            _uiMessage.value = "已删除 ${targets.size} 个失效订阅源（含其文章）"
        }
    }

    /**
     * 写订阅源的摘要提示词覆盖。空白串一律当"清除覆盖"——
     * 存一个只有空格的模板等于让模型收到空 system，输出会变得极不稳定。
     */
    private fun setFeedSummaryPrompt(feedId: Long, prompt: String?) {
        val normalized = prompt?.trim()?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val current = feedAiProfileDao.get(feedId)
            if (current == null) {
                if (normalized == null) return@launch // 本来就没配，无需建空行
                feedAiProfileDao.upsert(
                    com.cycling.rssradar.core.data.db.FeedAiProfileEntity(
                        feedId = feedId,
                        summaryPrompt = normalized,
                        updatedAt = now,
                    ),
                )
            } else {
                feedAiProfileDao.updateSummaryPrompt(feedId, normalized, now)
            }
            // 刻意**不**清除已生成的摘要：旧摘要是按旧提示词写的，但内容依然忠实于原文，
            // 清掉等于逼用户重新花钱生成一遍。用户在阅读页点"重新生成"即可套用新提示词。
            _uiMessage.value = if (normalized == null) "已改用内置摘要提示词" else "已保存该订阅源的摘要提示词"
        }
    }

    private fun setFeedAutoSummary(feedId: Long, enabled: Boolean) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val current = feedAiProfileDao.get(feedId)
            val base = current ?: com.cycling.rssradar.core.data.db.FeedAiProfileEntity(
                feedId = feedId,
                updatedAt = now,
            )
            feedAiProfileDao.upsert(base.copy(autoSummary = enabled, updatedAt = now))
        }
    }

    private fun toggleGroup(group: String) {
        _expandedIds.value = _expandedIds.value.toMutableSet().also { set ->
            if (!set.add(group)) set.remove(group)
        }
    }

    private fun markAllRead() {
        viewModelScope.launch {
            repository.markAllRead()
            _uiMessage.value = "已全部标记为已读"
        }
    }

    private fun selectSort(mode: FeedSortMode) {
        feedSortStore.set(mode)
    }

    // —— 分组 CRUD ——

    /** 新建分组：仅注册表加名；已有同名返回 false。 */
    private fun createGroup(name: String) {
        val ok = groupStore.addGroup(name)
        _uiMessage.value = if (ok) "已创建分组「${name.trim()}」" else "分组已存在或名称为空"
    }

    /** 重命名分组：注册表改名 + feeds.groupName 批量改。 */
    private fun renameGroup(oldName: String, newName: String) {
        val ok = groupStore.renameGroup(oldName, newName)
        if (!ok) {
            _uiMessage.value = "新名称无效或已存在"
            return
        }
        viewModelScope.launch {
            repository.renameGroup(oldName, newName.trim())
            _uiMessage.value = "已重命名为「${newName.trim()}」"
        }
    }

    /** 删除分组：注册表删名 + 该组 feed 移回默认组。 */
    private fun deleteGroup(name: String) {
        if (name == DEFAULT_GROUP) {
            _uiMessage.value = "默认分组不可删除"
            return
        }
        groupStore.removeGroup(name)
        viewModelScope.launch {
            repository.deleteGroup(name)
            _uiMessage.value = "已删除分组「$name」，其中的订阅移入默认分组"
        }
    }

    /** 移动订阅源到分组。 */
    private fun moveFeed(feedId: Long, targetGroup: String) {
        viewModelScope.launch {
            repository.moveFeed(feedId, targetGroup)
            _uiMessage.value = "已移动订阅"
        }
    }

    // —— 批量移动（issue #7） ——

    /** 进出多选模式；退出即清空勾选，不留残留状态。 */
    private fun toggleSelectionMode() {
        _selectionMode.value = !_selectionMode.value
        if (!_selectionMode.value) _selectedFeedIds.value = emptySet()
    }

    private fun toggleFeedSelected(feedId: Long) {
        _selectedFeedIds.value = _selectedFeedIds.value.toMutableSet().also { set ->
            if (!set.add(feedId)) set.remove(feedId)
        }
    }

    /** 执行批量移动：目标分组不在注册表时顺带注册（分组是注册表 + 字符串，见 GroupStore）。 */
    private fun moveSelectedFeeds(targetGroup: String) {
        val ids = _selectedFeedIds.value.toList()
        val group = targetGroup.trim().ifBlank { DEFAULT_GROUP }
        _selectionMode.value = false
        _selectedFeedIds.value = emptySet()
        if (ids.isEmpty()) return
        if (group !in groupStore.getGroups()) groupStore.addGroup(group)
        viewModelScope.launch {
            repository.moveFeedsToGroup(ids, group)
            _uiMessage.value = "已移动 ${ids.size} 个订阅到「$group」"
        }
    }

    /** 执行批量删除：勾选集合即删除清单，退出多选并如实报数。 */
    private fun deleteSelectedFeeds() {
        val ids = _selectedFeedIds.value.toList()
        _selectionMode.value = false
        _selectedFeedIds.value = emptySet()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteFeeds(ids)
            _uiMessage.value = "已删除 ${ids.size} 个订阅源（含其文章）"
        }
    }

    // —— 清空文章（issue #8） ——

    private fun clearFeedArticles(feedId: Long, feedTitle: String) {
        viewModelScope.launch {
            _uiMessage.value = clearMessage(repository.clearFeedArticles(feedId), "「$feedTitle」")
        }
    }

    private fun clearGroupArticles(group: String) {
        viewModelScope.launch {
            _uiMessage.value = clearMessage(repository.clearGroupArticles(group), "「$group」")
        }
    }

    /**
     * 删除分组内全部订阅源。两处刻意与既有做法对齐：
     * - **删除清单执行时从 DB 现取**（不走 UI 传回的名单 / 任何 WhileSubscribed 的 StateFlow）：
     *   确认弹窗停留期间列表可能已经变了，按弹窗那一刻的快照删会删错。
     * - **分组归属按 [groupFeeds] 的同一套归一**（空串 = 默认分组）：判据只此一份，
     *   否则列表里归在「默认」的源会逃过这次删除。
     * 分组本身保留在注册表里（删完只是空了）——「删除分组」是另一件事，见 [deleteGroup]。
     */
    private fun deleteGroupFeeds(group: String) {
        viewModelScope.launch {
            val targets = repository.observeFeeds().first()
                .filter { groupOfFeed(it) == group }
            if (targets.isEmpty()) {
                _uiMessage.value = "「$group」里没有订阅源"
                return@launch
            }
            repository.deleteFeeds(targets.map { it.id })
            _uiMessage.value = "已删除「$group」的 ${targets.size} 个订阅源（含其文章）"
        }
    }

    /** 提示文案：数字全部来自 ClearArticlesResult 的真实统计，不估算。 */
    private fun clearMessage(result: ClearArticlesResult, subject: String): String = buildString {
        append(
            if (result.deleted == 0) {
                "$subject 没有可清空的文章"
            } else {
                "已清空 $subject 的 ${result.deleted} 篇文章"
            },
        )
        if (result.kept > 0) append("，保留 ${result.kept} 篇收藏/稍后读")
    }

    /** Feed 级预设：全文抓取开关（issue #9）。 */
    private fun setFullContentEnabled(feedId: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setFullContentEnabled(feedId, enabled)
            _uiMessage.value = if (enabled) {
                "已开启全文抓取，详情页会自动抓原网页正文"
            } else {
                "已关闭全文抓取，详情页只显示订阅源自带内容"
            }
        }
    }

    /** 重命名订阅源标题。 */
    private fun renameFeed(feedId: Long, title: String) {
        if (title.isBlank()) {
            _uiMessage.value = "标题不能为空"
            return
        }
        viewModelScope.launch {
            repository.renameFeed(feedId, title.trim())
            _uiMessage.value = "已重命名"
        }
    }

    /** 删除订阅源（文章级联删除）。 */
    private fun deleteFeed(feedId: Long, feedTitle: String) {
        viewModelScope.launch {
            repository.deleteFeed(feedId)
            _uiMessage.value = "已删除「$feedTitle」"
        }
    }

    /** 自动同步开关（issue #58）。 */
    private fun setSyncEnabled(feedId: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setSyncEnabled(feedId, enabled)
            _uiMessage.value = if (enabled) "已参与自动同步" else "已屏蔽自动同步（手动刷新不受影响）"
        }
    }

    /**
     * OPML 盲导（ADR-0004）：解析入库 → 注册新分组 → 立即报结果 →
     * 后台对新导入的源定向刷新补文章（静默失败，语义同全量刷新）。
     */
    private fun importOpml(uri: Uri) {
        viewModelScope.launch {
            val result = try {
                val stream = appContext.contentResolver.openInputStream(uri)
                    ?: run {
                        _uiMessage.value = "无法读取所选文件"
                        return@launch
                    }
                stream.use { subscriptionFlow.importOpml(it) }
            } catch (_: IllegalArgumentException) {
                _uiMessage.value = "不是有效的 OPML 文件"
                return@launch
            } catch (_: Exception) {
                _uiMessage.value = "导入失败，请重试"
                return@launch
            }
            result.groups.forEach { groupStore.addGroup(it) }
                _uiMessage.value = if (result.skipped > 0) {
                "已导入 ${result.imported} 个订阅源，跳过 ${result.skipped} 个重复"
            } else {
                "已导入 ${result.imported} 个订阅源"
            }
            // 后台补文章：只刷新新导入的源，不阻塞导入结果提示
            viewModelScope.launch {
                repository.refreshFeeds(result.newFeedIds)
            }
        }
    }

    /** Feed 级通知开关（#31）：写库即生效，FeedAction 页的 feed 是 Room flow，自动刷新。 */
    private fun setNotificationsEnabled(feedId: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setNotificationsEnabled(feedId, enabled)
            _uiMessage.value = if (enabled) "已开启此源的通知" else "已关闭此源的通知"
        }
    }

    private fun setContentType(feedId: Long, contentType: Int) {
        viewModelScope.launch {
            repository.setContentType(feedId, contentType)
            _uiMessage.value = "内容类型已更新"
        }
    }

    /**
     * OPML 导出（#4）：序列化全部订阅源 → 写进用户选的 URI。
     * 写失败（没有写权限/存储被移除）如实报错，不假装成功。
     */
    private fun exportOpml(uri: Uri) {
        viewModelScope.launch {
            val opml = subscriptionFlow.exportOpml()
            val written = runCatching {
                appContext.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(opml.toByteArray(Charsets.UTF_8))
                    true
                } ?: false
            }.getOrDefault(false)
            _uiMessage.value = if (written) "已导出 OPML" else "导出失败，请重试"
        }
    }

    private fun groupFeeds(
        feeds: List<FeedEntity>,
        unreadMap: Map<Long, Int>,
        latestMap: Map<Long, Long>,
        registered: List<String>,
        sort: FeedSortMode,
    ): List<GroupSectionUi> {
        val byName = feeds.groupBy { groupOfFeed(it) }
        // 注册表里没有 feed 的分组也要显示（空分组）
        val ordered = registered.distinct() + byName.keys.filterNot { it in registered }
        val sections = ordered.map { group ->
            GroupSectionUi(
                group = group,
                feeds = byName[group].orEmpty().map { withFailure(it, unreadMap[it.id] ?: 0) },
            )
        }
        return when (sort) {
            // 名称序：分组保持注册顺序，组内按标题
            FeedSortMode.BY_NAME ->
                sections.map { it.copy(feeds = it.feeds.sortedBy { f -> f.feed.title }) }
            // 最近更新：有新文章的组和源都排前面；从没抓到文章的沉底
            FeedSortMode.BY_RECENT ->
                sections
                    .sortedByDescending { s -> s.feeds.maxOfOrNull { latestMap[it.feed.id] ?: 0L } ?: 0L }
                    .map { it.copy(feeds = it.feeds.sortedByDescending { f -> latestMap[f.feed.id] ?: 0L }) }
            // 未读数：未读多的组和源排前面；同数量按名称稳定排列
            FeedSortMode.BY_UNREAD ->
                sections
                    .sortedByDescending { s -> s.feeds.sumOf { it.unreadCount } }
                    .map {
                        it.copy(
                            feeds = it.feeds.sortedWith(
                                compareByDescending<FeedWithUnread> { f -> f.unreadCount }.thenBy { f -> f.feed.title },
                            ),
                        )
                    }
        }
    }
}
