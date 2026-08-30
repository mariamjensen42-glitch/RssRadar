package com.cycling.rssradar.ui.subscriptions

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.data.db.DEFAULT_GROUP
import com.cycling.rssradar.data.db.FeedEntity
import com.cycling.rssradar.data.FeedRepository
import com.cycling.rssradar.data.db.GROUP_DESIGN
import com.cycling.rssradar.data.db.GROUP_DEV
import com.cycling.rssradar.data.db.GROUP_TECH
import com.cycling.rssradar.data.store.GroupStore
import com.cycling.rssradar.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


/** 订阅 + 未读数，UI 直接消费。 */
data class FeedWithUnread(val feed: FeedEntity, val unreadCount: Int)

/** 一个分组下的所有订阅。 */
data class GroupSectionUi(val group: String, val feeds: List<FeedWithUnread>)

/** 订阅页事件（候选 A，ADR-0003）。 */
sealed interface SubscriptionsIntent {
    data class ToggleGroup(val group: String) : SubscriptionsIntent
    data object MarkAllRead : SubscriptionsIntent
    data object ToggleSort : SubscriptionsIntent
    data object ConsumeMessage : SubscriptionsIntent
    data class CreateGroup(val name: String) : SubscriptionsIntent
    data class RenameGroup(val oldName: String, val newName: String) : SubscriptionsIntent
    data class DeleteGroup(val name: String) : SubscriptionsIntent
    data class MoveFeed(val feedId: Long, val targetGroup: String) : SubscriptionsIntent
    data class RenameFeed(val feedId: Long, val title: String) : SubscriptionsIntent
    data class DeleteFeed(val feedId: Long, val feedTitle: String) : SubscriptionsIntent
    data class ImportOpml(val uri: Uri) : SubscriptionsIntent
}

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val repository: FeedRepository,
    private val groupStore: GroupStore,
    @ApplicationContext private val appContext: Context,
) : ViewModel(), MviViewModel<SubscriptionsIntent> {

    private val _expandedIds = MutableStateFlow(setOf(GROUP_TECH, GROUP_DEV, GROUP_DESIGN))
    val expandedGroupIds: StateFlow<Set<String>> = _expandedIds.asStateFlow()

    /** 分组注册表：保证空分组也显示。 */
    private val _groupsList = MutableStateFlow(groupStore.getGroups())
    val groupsList: StateFlow<List<String>> = _groupsList.asStateFlow()

    val groups: StateFlow<List<GroupSectionUi>> = combine(
        repository.observeFeeds(),
        repository.observeFeedUnreadCounts(),
        _groupsList,
    ) { feeds, unreadMap, registered ->
        groupFeeds(feeds, unreadMap, registered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalUnread: StateFlow<Int> = repository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 按 id 取单条订阅（#31 FeedAction 目的地解析 feed 用）。保持 fun：状态 producer，非事件。 */
    fun getFeed(feedId: Long): StateFlow<FeedEntity?> =
        repository.observeFeeds()
            .map { list -> list.find { it.id == feedId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var uiMessage by mutableStateOf<String?>(null)
        private set

    override fun onIntent(intent: SubscriptionsIntent) {
        when (intent) {
            is SubscriptionsIntent.ToggleGroup -> toggleGroup(intent.group)
            SubscriptionsIntent.MarkAllRead -> markAllRead()
            SubscriptionsIntent.ToggleSort -> toggleSort()
            SubscriptionsIntent.ConsumeMessage -> uiMessage = null
            is SubscriptionsIntent.CreateGroup -> createGroup(intent.name)
            is SubscriptionsIntent.RenameGroup -> renameGroup(intent.oldName, intent.newName)
            is SubscriptionsIntent.DeleteGroup -> deleteGroup(intent.name)
            is SubscriptionsIntent.MoveFeed -> moveFeed(intent.feedId, intent.targetGroup)
            is SubscriptionsIntent.RenameFeed -> renameFeed(intent.feedId, intent.title)
            is SubscriptionsIntent.DeleteFeed -> deleteFeed(intent.feedId, intent.feedTitle)
            is SubscriptionsIntent.ImportOpml -> importOpml(intent.uri)
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
            uiMessage = "已全部标记为已读"
        }
    }

    private fun toggleSort() {
        uiMessage = "排序方式已切换"
    }

    // —— 分组 CRUD ——

    /** 新建分组：仅注册表加名；已有同名返回 false。 */
    private fun createGroup(name: String) {
        val ok = groupStore.addGroup(name)
        refreshGroups()
        uiMessage = if (ok) "已创建分组「${name.trim()}」" else "分组已存在或名称为空"
    }

    /** 重命名分组：注册表改名 + feeds.groupName 批量改。 */
    private fun renameGroup(oldName: String, newName: String) {
        val ok = groupStore.renameGroup(oldName, newName)
        refreshGroups()
        if (!ok) {
            uiMessage = "新名称无效或已存在"
            return
        }
        viewModelScope.launch {
            repository.renameGroup(oldName, newName.trim())
            uiMessage = "已重命名为「${newName.trim()}」"
        }
    }

    /** 删除分组：注册表删名 + 该组 feed 移回默认组。 */
    private fun deleteGroup(name: String) {
        if (name == DEFAULT_GROUP) {
            uiMessage = "默认分组不可删除"
            return
        }
        groupStore.removeGroup(name)
        refreshGroups()
        viewModelScope.launch {
            repository.deleteGroup(name)
            uiMessage = "已删除分组「$name」，其中的订阅移入默认分组"
        }
    }

    /** 移动订阅源到分组。 */
    private fun moveFeed(feedId: Long, targetGroup: String) {
        viewModelScope.launch {
            repository.moveFeed(feedId, targetGroup)
            uiMessage = "已移动订阅"
        }
    }

    /** 重命名订阅源标题。 */
    private fun renameFeed(feedId: Long, title: String) {
        if (title.isBlank()) {
            uiMessage = "标题不能为空"
            return
        }
        viewModelScope.launch {
            repository.renameFeed(feedId, title.trim())
            uiMessage = "已重命名"
        }
    }

    /** 删除订阅源（文章级联删除）。 */
    private fun deleteFeed(feedId: Long, feedTitle: String) {
        viewModelScope.launch {
            repository.deleteFeed(feedId)
            uiMessage = "已删除「$feedTitle」"
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
                        uiMessage = "无法读取所选文件"
                        return@launch
                    }
                stream.use { repository.importOpml(it) }
            } catch (_: IllegalArgumentException) {
                uiMessage = "不是有效的 OPML 文件"
                return@launch
            } catch (_: Exception) {
                uiMessage = "导入失败，请重试"
                return@launch
            }
            result.groups.forEach { groupStore.addGroup(it) }
            refreshGroups()
            uiMessage = if (result.skipped > 0) {
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

    private fun refreshGroups() {
        _groupsList.value = groupStore.getGroups()
    }

    private fun groupFeeds(
        feeds: List<FeedEntity>,
        unreadMap: Map<Long, Int>,
        registered: List<String>,
    ): List<GroupSectionUi> {
        // 注册表里没有 feed 的分组也要显示（空分组）
        val byName = feeds
            .groupBy { it.groupName.ifBlank { DEFAULT_GROUP } }
            .mapValues { (_, list) ->
                list.sortedBy { it.title }.map { FeedWithUnread(it, unreadMap[it.id] ?: 0) }
            }
        val ordered = registered.distinct() + byName.keys.filterNot { it in registered }
        return ordered.map { group ->
            GroupSectionUi(group = group, feeds = byName[group].orEmpty())
        }
    }
}
