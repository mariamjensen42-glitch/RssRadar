package com.cycling.rssradar.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.data.AddFeedResult
import com.cycling.rssradar.data.db.ArticleEntity
import com.cycling.rssradar.data.db.ArticleWithFeed
import com.cycling.rssradar.data.FeedRepository
import com.cycling.rssradar.data.ai.AiRepository
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

/** 信息流页内的 4 个过滤 tab。 */
enum class FeedTab { All, Unread, Starred, Bookmarked }

/**
 * 信息流页界面状态（MVI 候选 C 首个落地，ADR-0003）：不可变快照驱动渲染，
 * 变更走 [FeedListViewModel] 的单一 onIntent。
 * 分页快照规则见 [PagedSnapshot]（纯函数，可测）。
 */
data class FeedListUiState(
    val selectedTab: FeedTab = FeedTab.All,
    /** 分组筛选：null = 全部。仅 All tab 生效。 */
    val selectedGroup: String? = null,
    /**
     * 当前 tab 的分页快照列表。
     * 快照语义：列表内的卡片状态（已读/收藏等）由 PagedSnapshot.mutate 原地更新，
     * 不靠 DB 失效重查——那正是数万行重查询 OOM 的根源。
     */
    val articles: List<ArticleWithFeed> = emptyList(),
    /** 是否还有下一页（四个 tab 均分页）。 */
    val hasMore: Boolean = false,
    /** 是否正在加载下一页。 */
    val isLoadingMore: Boolean = false,
    /** 下拉刷新进行中。 */
    val isRefreshing: Boolean = false,
    val isAddingFeed: Boolean = false,
    /** 一次性提示消息（Snackbar），消费后置空。 */
    val uiMessage: String? = null,
    /** 最近删除的文章（issue #46 撤销删除）：Snackbar 撤销期内暂存，带原 id 可完整插回。 */
    val pendingUndoDelete: ArticleEntity? = null,
)

/** 信息流事件（候选 A，ADR-0003）。 */
sealed interface FeedListIntent {
    data object ConsumeMessage : FeedListIntent
    data class SelectTab(val tab: FeedTab) : FeedListIntent
    data class SelectGroup(val group: String?) : FeedListIntent
    data class ToggleStarred(val articleId: Long) : FeedListIntent
    data class ToggleBookmarked(val articleId: Long) : FeedListIntent
    /** 已读/未读互切（长按菜单，issue #46）。 */
    data class SetRead(val articleId: Long, val read: Boolean) : FeedListIntent
    data class MarkRead(val articleId: Long) : FeedListIntent
    data class DeleteArticle(val articleId: Long) : FeedListIntent
    /** 撤销最近一次删除（Snackbar 动作）。 */
    data object UndoDeleteArticle : FeedListIntent
    /** 放弃撤销机会（Snackbar 自动消失）。 */
    data object DiscardUndo : FeedListIntent
    data object Refresh : FeedListIntent
    data object LoadMore : FeedListIntent
    data class AddFeed(val rawUrl: String, val groupName: String) : FeedListIntent
}

@HiltViewModel
class FeedListViewModel @Inject constructor(
    private val repository: FeedRepository,
    groupStore: GroupStore,
    private val aiRepository: AiRepository,
) : ViewModel(), MviViewModel<FeedListIntent> {

    private val _uiState = MutableStateFlow(FeedListUiState())
    val uiState: StateFlow<FeedListUiState> = _uiState.asStateFlow()

    val unreadCount: StateFlow<Int> = repository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 分组清单（注册表），供筛选栏使用。 */
    val groupOptions: StateFlow<List<String>> = MutableStateFlow(groupStore.getGroups())

    private var loadMoreJob: Job? = null

    init {
        // 首屏拉第一页；空库/异常静默，用户可下拉重试
        viewModelScope.launch { loadFirstPage() }
    }

    override fun onIntent(intent: FeedListIntent) {
        when (intent) {
            FeedListIntent.ConsumeMessage -> update { it.copy(uiMessage = null) }
            is FeedListIntent.SelectTab -> selectTab(intent.tab)
            is FeedListIntent.SelectGroup -> update { it.copy(selectedGroup = intent.group) }
            is FeedListIntent.ToggleStarred -> toggleStarred(intent.articleId)
            is FeedListIntent.ToggleBookmarked -> toggleBookmarked(intent.articleId)
            is FeedListIntent.SetRead -> setRead(intent.articleId, intent.read)
            is FeedListIntent.MarkRead -> markRead(intent.articleId)
            is FeedListIntent.DeleteArticle -> deleteArticle(intent.articleId)
            FeedListIntent.UndoDeleteArticle -> undoDelete()
            FeedListIntent.DiscardUndo -> update { it.copy(pendingUndoDelete = null) }
            FeedListIntent.Refresh -> refresh()
            FeedListIntent.LoadMore -> loadMore()
            is FeedListIntent.AddFeed -> addFeed(intent.rawUrl, intent.groupName)
        }
    }

    private fun update(transform: (FeedListUiState) -> FeedListUiState) {
        _uiState.value = transform(_uiState.value)
    }

    private fun selectTab(tab: FeedTab) {
        if (_uiState.value.selectedTab == tab) return
        update { it.copy(selectedTab = tab) }
        viewModelScope.launch { loadFirstPage() }
    }

    /** 按当前分组筛选后的列表（null = 全部，直接透传）。保持 fun：纯投影，非事件。 */
    fun filterByGroup(articles: List<ArticleWithFeed>): List<ArticleWithFeed> {
        val group = _uiState.value.selectedGroup ?: return articles
        return articles.filter { it.feedGroup == group }
    }

    private fun toggleStarred(articleId: Long) {
        val current = starredOf(articleId)
        viewModelScope.launch {
            repository.setStarred(articleId, !current)
            update { it.copy(articles = PagedSnapshot.mutate(it.articles, ::idOf, articleId) { a -> a.copy(isStarred = !current) }) }
        }
    }

    /** 从自身缓存列表读当前收藏态再翻转（MVI：状态由 VM 持有，不靠 UI 回传 current）。 */
    private fun starredOf(articleId: Long): Boolean =
        _uiState.value.articles.firstOrNull { it.article.id == articleId }?.article?.isStarred ?: false

    private fun markRead(articleId: Long) {
        viewModelScope.launch {
            repository.markRead(articleId)
            update { it.copy(articles = PagedSnapshot.mutate(it.articles, ::idOf, articleId) { a -> a.copy(isRead = true) }) }
        }
    }

    /** 已读/未读互切：从自身缓存读当前态再翻转。 */
    private fun setRead(articleId: Long, read: Boolean) {
        viewModelScope.launch {
            repository.setRead(articleId, read)
            update { it.copy(articles = PagedSnapshot.mutate(it.articles, ::idOf, articleId) { a -> a.copy(isRead = read) }) }
        }
    }

    /** 稍后读切换（issue #46）。 */
    private fun toggleBookmarked(articleId: Long) {
        val current = stateOf(articleId)?.article?.isBookmarked ?: false
        viewModelScope.launch {
            repository.setBookmarked(articleId, !current)
            update { it.copy(articles = PagedSnapshot.mutate(it.articles, ::idOf, articleId) { a -> a.copy(isBookmarked = !current) }) }
        }
    }

    /**
     * 删除单篇文章：暂存实体供撤销，并清译文缓存（翻译不落盘，删文即孤儿）。
     * 撤销窗口由 Snackbar 的生命周期决定，超时丢弃。
     */
    private fun deleteArticle(articleId: Long) {
        viewModelScope.launch {
            val deleted = repository.deleteArticle(articleId) ?: return@launch
            aiRepository.clearTranslationCache(articleId)
            update {
                it.copy(
                    pendingUndoDelete = deleted,
                    // 分页快照同步移除，卡片立刻消失
                    articles = PagedSnapshot.remove(it.articles, ::idOf, articleId),
                )
            }
        }
    }

    private fun undoDelete() {
        val entity = _uiState.value.pendingUndoDelete ?: return
        update { it.copy(pendingUndoDelete = null) }
        viewModelScope.launch {
            repository.restoreArticle(entity)
            // All tab 是分页快照，恢复后重载第一页让文章立即可见
            loadFirstPage()
        }
    }

    /** 从自身缓存列表读指定文章的当前态（MVI：状态由 VM 持有）。 */
    private fun stateOf(articleId: Long): ArticleWithFeed? =
        _uiState.value.articles.firstOrNull { it.article.id == articleId }

    /** 下拉刷新：全源抓取 + 重载第一页。失败保留现有数据并提示。 */
    private fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            update { it.copy(isRefreshing = true) }
            val successCount = repository.refreshAllFeeds()
            loadFirstPage()
            update {
                it.copy(
                    isRefreshing = false,
                    uiMessage = when {
                        repository.hasFeeds() && successCount == 0 -> "刷新失败，展示的是上次内容"
                        successCount > 0 -> "已更新 $successCount 个订阅源"
                        else -> it.uiMessage
                    },
                )
            }
        }
    }

    /** 滚动到列表尾部时调用：按当前 tab 拉下一页追加（PagedSnapshot.append 兜去重）。 */
    private fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return
        if (loadMoreJob?.isActive == true) return
        loadMoreJob = viewModelScope.launch {
            update { it.copy(isLoadingMore = true) }
            val page = loadTabPage(PAGE_SIZE, _uiState.value.articles.size)
            update {
                it.copy(
                    articles = PagedSnapshot.append(it.articles, page, keyOf = ::idOf),
                    hasMore = page.size == PAGE_SIZE,
                    isLoadingMore = false,
                )
            }
        }
    }

    /** 按当前 tab 取一页。 */
    private suspend fun loadTabPage(limit: Int, offset: Int): List<ArticleWithFeed> =
        when (_uiState.value.selectedTab) {
            FeedTab.All -> repository.loadArticlesPage(limit, offset)
            FeedTab.Unread -> repository.loadUnreadPage(limit, offset)
            FeedTab.Starred -> repository.loadStarredPage(limit, offset)
            FeedTab.Bookmarked -> repository.loadBookmarkedPage(limit, offset)
        }

    private suspend fun loadFirstPage() {
        val page = loadTabPage(PAGE_SIZE, 0)
        update {
            it.copy(articles = page, hasMore = page.size == PAGE_SIZE)
        }
    }

    private fun addFeed(rawUrl: String, groupName: String) {
        if (_uiState.value.isAddingFeed) return
        viewModelScope.launch {
            update { it.copy(isAddingFeed = true) }
            val message = when (repository.addFeed(rawUrl, groupName)) {
                AddFeedResult.Success -> "订阅成功"
                AddFeedResult.Duplicate -> "该源已订阅"
                AddFeedResult.InvalidFeed -> "不是有效的 RSS/Atom 源"
                AddFeedResult.NetworkError -> "网络错误，请检查链接后重试"
            }
            update { it.copy(isAddingFeed = false, uiMessage = message) }
            // 订阅成功后新源文章可能出现在首屏，重载第一页让列表可见
            if (repository.hasFeeds()) loadFirstPage()
        }
    }

    companion object {
        /** 信息流分页大小。 */
        const val PAGE_SIZE = 30

        private fun idOf(item: ArticleWithFeed): Long = item.article.id
    }
}
