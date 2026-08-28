package com.cycling.rssradar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cycling.rssradar.data.DEFAULT_GROUP
import com.cycling.rssradar.data.FeedEntity
import com.cycling.rssradar.data.FeedRepository
import com.cycling.rssradar.data.GROUP_DESIGN
import com.cycling.rssradar.data.GROUP_DEV
import com.cycling.rssradar.data.GROUP_TECH
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 订阅 + 未读数，UI 直接消费。 */
data class FeedWithUnread(val feed: FeedEntity, val unreadCount: Int)

/** 一个分组下的所有订阅。 */
data class GroupSectionUi(val group: String, val feeds: List<FeedWithUnread>)

class SubscriptionsViewModel(private val repository: FeedRepository) : ViewModel() {

    private val _expandedIds = MutableStateFlow(setOf(GROUP_TECH, GROUP_DEV, GROUP_DESIGN))
    val expandedGroupIds: StateFlow<Set<String>> = _expandedIds.asStateFlow()

    val groups: StateFlow<List<GroupSectionUi>> = combine(
        repository.observeFeeds(),
        repository.observeFeedUnreadCounts(),
    ) { feeds, unreadMap ->
        groupFeeds(feeds, unreadMap)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalUnread: StateFlow<Int> = repository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    var uiMessage by mutableStateOf<String?>(null)
        private set

    fun toggleGroup(group: String) {
        _expandedIds.value = _expandedIds.value.toMutableSet().also { set ->
            if (!set.add(group)) set.remove(group)
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            repository.markAllRead()
            uiMessage = "已全部标记为已读"
        }
    }

    fun toggleSort() {
        uiMessage = "排序方式已切换"
    }

    fun onMessageShown() {
        uiMessage = null
    }

    private fun groupFeeds(
        feeds: List<FeedEntity>,
        unreadMap: Map<Long, Int>,
    ): List<GroupSectionUi> {
        if (feeds.isEmpty()) {
            // 即使没有订阅，也展示 3 个示例分组占位（科技 / 开发 / 设计），与设计稿空态保持一致
            return listOf(
                GroupSectionUi(GROUP_TECH, emptyList()),
                GroupSectionUi(GROUP_DEV, emptyList()),
                GroupSectionUi(GROUP_DESIGN, emptyList()),
            )
        }
        return feeds
            .groupBy { it.groupName.ifBlank { DEFAULT_GROUP } }
            .toSortedMap(compareBy<String> { it })
            .map { (group, list) ->
                GroupSectionUi(
                    group = group,
                    feeds = list
                        .sortedBy { it.title }
                        .map { FeedWithUnread(it, unreadMap[it.id] ?: 0) },
                )
            }
    }

    companion object {
        fun factory(container: com.cycling.rssradar.AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SubscriptionsViewModel(container.repository) }
            }
    }
}
