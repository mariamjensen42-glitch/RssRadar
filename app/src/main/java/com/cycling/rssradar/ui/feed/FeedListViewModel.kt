package com.cycling.rssradar.ui.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.data.AddFeedResult
import com.cycling.rssradar.data.db.ArticleWithFeed
import com.cycling.rssradar.data.FeedRepository
import com.cycling.rssradar.data.store.GroupStore
import com.cycling.rssradar.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.cycling.rssradar.ui.theme.Success

/** 信息流页内的 4 个过滤 tab。 */
enum class FeedTab { All, Unread, Starred, Bookmarked }

/** 信息流事件（候选 A，ADR-0003）。 */
sealed interface FeedListIntent {
    data object ConsumeMessage : FeedListIntent
    data class SelectTab(val tab: FeedTab) : FeedListIntent
    data class SelectGroup(val group: String?) : FeedListIntent
    data class ToggleStarred(val articleId: Long) : FeedListIntent
    data class MarkRead(val articleId: Long) : FeedListIntent
    data object Refresh : FeedListIntent
    data object LoadMore : FeedListIntent
    data class AddFeed(val rawUrl: String, val groupName: String) : FeedListIntent
}

@HiltViewModel
class FeedListViewModel @Inject constructor(
    private val repository: FeedRepository,
    groupStore: GroupStore,
) : ViewModel(), MviViewModel<FeedListIntent> {

    private val _selectedTab = MutableStateFlow(FeedTab.All)
    val selectedTab: StateFlow<FeedTab> = _selectedTab.asStateFlow()

    /** 分组筛选：null = 全部。仅 All tab 生效。 */
    private val _selectedGroup = MutableStateFlow<String?>(null)
    val selectedGroup: StateFlow<String?> = _selectedGroup.asStateFlow()

    /** All tab 用分页累积列表（量大，只取已加载部分）；其余 tab 用实时 Flow。 */
    private val _allArticles = MutableStateFlow<List<ArticleWithFeed>>(emptyList())
    val allArticles: StateFlow<List<ArticleWithFeed>> = _allArticles.asStateFlow()

    val unreadArticles: StateFlow<List<ArticleWithFeed>> = repository.observeUnreadArticles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val starredArticles: StateFlow<List<ArticleWithFeed>> = repository.observeStarredArticles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bookmarkedArticles: StateFlow<List<ArticleWithFeed>> = repository.observeBookmarkedArticles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadCount: StateFlow<Int> = repository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 分组清单（注册表），供筛选栏使用。 */
    val groupOptions: StateFlow<List<String>> = MutableStateFlow(groupStore.getGroups())

    /** 是否还有下一页（仅 All tab 分页生效）。 */
    var hasMore by mutableStateOf(false)
        private set

    /** 是否正在加载下一页。 */
    var isLoadingMore by mutableStateOf(false)
        private set

    /** 下拉刷新进行中。 */
    var isRefreshing by mutableStateOf(false)
        private set

    var isAddingFeed by mutableStateOf(false)
        private set

    /** 一次性提示消息（Snackbar），消费后置空。 */
    var uiMessage by mutableStateOf<String?>(null)
        private set

    private var loadMoreJob: Job? = null

    init {
        // 首屏拉第一页；空库/异常静默，用户可下拉重试
        viewModelScope.launch { loadFirstPage() }
    }

    override fun onIntent(intent: FeedListIntent) {
        when (intent) {
            FeedListIntent.ConsumeMessage -> uiMessage = null
            is FeedListIntent.SelectTab -> selectTab(intent.tab)
            is FeedListIntent.SelectGroup -> selectGroup(intent.group)
            is FeedListIntent.ToggleStarred -> toggleStarred(intent.articleId)
            is FeedListIntent.MarkRead -> markRead(intent.articleId)
            FeedListIntent.Refresh -> refresh()
            FeedListIntent.LoadMore -> loadMore()
            is FeedListIntent.AddFeed -> addFeed(intent.rawUrl, intent.groupName)
        }
    }

    private fun selectTab(tab: FeedTab) {
        _selectedTab.value = tab
    }

    private fun selectGroup(group: String?) {
        _selectedGroup.value = group
    }

    /** 按当前分组筛选后的列表（null = 全部，直接透传）。保持 fun：纯投影，非事件。 */
    fun filterByGroup(articles: List<ArticleWithFeed>): List<ArticleWithFeed> {
        val group = _selectedGroup.value ?: return articles
        return articles.filter { it.feedGroup == group }
    }

    private fun toggleStarred(articleId: Long) {
        val current = starredOf(articleId)
        viewModelScope.launch { repository.setStarred(articleId, !current) }
    }

    /** 从自身缓存列表读当前收藏态再翻转（MVI：状态由 VM 持有，不靠 UI 回传 current）。 */
    private fun starredOf(articleId: Long): Boolean =
        (_allArticles.value + unreadArticles.value + starredArticles.value + bookmarkedArticles.value)
            .firstOrNull { it.article.id == articleId }?.article?.isStarred ?: false

    private fun markRead(articleId: Long) {
        viewModelScope.launch { repository.markRead(articleId) }
    }

    /** 下拉刷新：全源抓取 + 重载第一页。失败保留现有数据并提示。 */
    private fun refresh() {
        if (isRefreshing) return
        viewModelScope.launch {
            isRefreshing = true
            val successCount = repository.refreshAllFeeds()
            loadFirstPage()
            isRefreshing = false
            when {
                repository.hasFeeds() && successCount == 0 ->
                    uiMessage = "刷新失败，展示的是上次内容"
                successCount > 0 ->
                    uiMessage = "已更新 $successCount 个订阅源"
            }
        }
    }

    /** 滚动到列表尾部时调用：拉下一页追加。 */
    private fun loadMore() {
        if (isLoadingMore || !hasMore) return
        if (loadMoreJob?.isActive == true) return
        loadMoreJob = viewModelScope.launch {
            isLoadingMore = true
            val current = _allArticles.value
            val page = repository.loadArticlesPage(PAGE_SIZE, current.size)
            _allArticles.value = current + page
            hasMore = page.size == PAGE_SIZE
            isLoadingMore = false
        }
    }

    private suspend fun loadFirstPage() {
        val page = repository.loadArticlesPage(PAGE_SIZE, 0)
        _allArticles.value = page
        hasMore = page.size == PAGE_SIZE
    }

    private fun addFeed(rawUrl: String, groupName: String) {
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
            // 订阅成功后新源文章可能出现在首屏，重载第一页让列表可见
            if (repository.hasFeeds()) loadFirstPage()
        }
    }

    companion object {
        /** 信息流分页大小。 */
        const val PAGE_SIZE = 30
    }
}
