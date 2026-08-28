package com.cycling.rssradar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cycling.rssradar.data.AddFeedResult
import com.cycling.rssradar.data.ArticleWithFeed
import com.cycling.rssradar.data.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 信息流页内的 3 个过滤 tab。 */
enum class FeedTab { All, Unread, Starred }

class FeedListViewModel(private val repository: FeedRepository) : ViewModel() {

    private val _selectedTab = MutableStateFlow(FeedTab.All)
    val selectedTab: StateFlow<FeedTab> = _selectedTab.asStateFlow()

    val allArticles: StateFlow<List<ArticleWithFeed>> = repository.observeAllArticles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadArticles: StateFlow<List<ArticleWithFeed>> = repository.observeUnreadArticles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val starredArticles: StateFlow<List<ArticleWithFeed>> = repository.observeStarredArticles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadCount: StateFlow<Int> = repository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    var isAddingFeed by mutableStateOf(false)
        private set

    /** 一次性提示消息（Snackbar），消费后置空。 */
    var uiMessage by mutableStateOf<String?>(null)
        private set

    fun onMessageShown() {
        uiMessage = null
    }

    fun selectTab(tab: FeedTab) {
        _selectedTab.value = tab
    }

    fun toggleStarred(articleId: Long, current: Boolean) {
        viewModelScope.launch { repository.setStarred(articleId, !current) }
    }

    fun markRead(articleId: Long) {
        viewModelScope.launch { repository.markRead(articleId) }
    }

    fun addFeed(rawUrl: String, groupName: String) {
        if (isAddingFeed) return
        viewModelScope.launch {
            isAddingFeed = true
            uiMessage = when (repository.addFeed(rawUrl, groupName)) {
                AddFeedResult.Success -> "订阅成功"
                AddFeedResult.Duplicate -> "该源已订阅"
                AddFeedResult.InvalidFeed -> "不是有效的 RSS/Atom 源"
                AddFeedResult.NetworkError -> "网络错误，请检查链接后重试"
            }
            isAddingFeed = false
        }
    }

    companion object {
        fun factory(container: com.cycling.rssradar.AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { FeedListViewModel(container.repository) }
            }
    }
}
