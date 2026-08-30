package com.cycling.rssradar.ui.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

import com.cycling.rssradar.ui.theme.Success

/** 信息流页内的 4 个过滤 tab。 */
enum class FeedTab { All, Unread, Starred, Bookmarked }

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

    private val _selectedTab = MutableStateFlow(FeedTab.All)
    val selectedTab: StateFlow<FeedTab> = _selectedTab.asStateFlow()

    /** 分组筛选：null = 全部。仅 All tab 生效。 */
    private val _selectedGroup = MutableStateFlow<String?>(null)
    val selectedGroup: StateFlow<String?> = _selectedGroup.asStateFlow()

    /**
     * 当前 tab 的分页快照列表（规模现实：源 1000+、文章数万条）。
     * 四个 tab 统一 LIMIT/OFFSET 分页累积；tab 切换 / 下拉刷新 / 撤销删除时重载首页。
     * 快照语义：列表内的卡片状态（已读/收藏等）由 [mutateLocal] 原地更新，
     * 不靠 DB 失效重查——那正是数万行重查询 OOM 的根源。
     */
    private val _pagedArticles = MutableStateFlow<List<ArticleWithFeed>>(emptyList())
    val pagedArticles: StateFlow<List<ArticleWithFeed>> = _pagedArticles.asStateFlow()

    val unreadCount: StateFlow<Int> = repository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 分组清单（注册表），供筛选栏使用。 */
    val groupOptions: StateFlow<List<String>> = MutableStateFlow(groupStore.getGroups())

    /** 是否还有下一页（四个 tab 均分页）。 */
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

    /** 最近删除的文章（issue #46 撤销删除）：Snackbar 撤销期内暂存，带原 id 可完整插回。 */
    var pendingUndoDelete by mutableStateOf<ArticleEntity?>(null)
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
            is FeedListIntent.ToggleBookmarked -> toggleBookmarked(intent.articleId)
            is FeedListIntent.SetRead -> setRead(intent.articleId, intent.read)
            is FeedListIntent.MarkRead -> markRead(intent.articleId)
            is FeedListIntent.DeleteArticle -> deleteArticle(intent.articleId)
            FeedListIntent.UndoDeleteArticle -> undoDelete()
            FeedListIntent.DiscardUndo -> pendingUndoDelete = null
            FeedListIntent.Refresh -> refresh()
            FeedListIntent.LoadMore -> loadMore()
            is FeedListIntent.AddFeed -> addFeed(intent.rawUrl, intent.groupName)
        }
    }

    private fun selectTab(tab: FeedTab) {
        if (_selectedTab.value == tab) return
        _selectedTab.value = tab
        viewModelScope.launch { loadFirstPage() }
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
        viewModelScope.launch {
            repository.setStarred(articleId, !current)
            mutateLocal(articleId) { it.copy(isStarred = !current) }
        }
    }

    /** 从自身缓存列表读当前收藏态再翻转（MVI：状态由 VM 持有，不靠 UI 回传 current）。 */
    private fun starredOf(articleId: Long): Boolean =
        _pagedArticles.value.firstOrNull { it.article.id == articleId }?.article?.isStarred ?: false

    private fun markRead(articleId: Long) {
        viewModelScope.launch {
            repository.markRead(articleId)
            mutateLocal(articleId) { it.copy(isRead = true) }
        }
    }

    /** 已读/未读互切：从自身缓存读当前态再翻转。 */
    private fun setRead(articleId: Long, read: Boolean) {
        viewModelScope.launch {
            repository.setRead(articleId, read)
            mutateLocal(articleId) { it.copy(isRead = read) }
        }
    }

    /** 稍后读切换（issue #46）。 */
    private fun toggleBookmarked(articleId: Long) {
        val current = stateOf(articleId)?.article?.isBookmarked ?: false
        viewModelScope.launch {
            repository.setBookmarked(articleId, !current)
            mutateLocal(articleId) { it.copy(isBookmarked = !current) }
        }
    }

    /** 分页快照原地更新单篇卡片状态，免去整表重查。 */
    private fun mutateLocal(articleId: Long, transform: (ArticleEntity) -> ArticleEntity) {
        _pagedArticles.value = _pagedArticles.value.map {
            if (it.article.id == articleId) it.copy(article = transform(it.article)) else it
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
            pendingUndoDelete = deleted
            // 分页快照同步移除，卡片立刻消失
            _pagedArticles.value = _pagedArticles.value.filterNot { it.article.id == articleId }
        }
    }

    private fun undoDelete() {
        val entity = pendingUndoDelete ?: return
        pendingUndoDelete = null
        viewModelScope.launch {
            repository.restoreArticle(entity)
            // All tab 是分页快照，恢复后重载第一页让文章立即可见
            loadFirstPage()
        }
    }

    /** 从自身缓存列表读指定文章的当前态（MVI：状态由 VM 持有）。 */
    private fun stateOf(articleId: Long): ArticleWithFeed? =
        _pagedArticles.value.firstOrNull { it.article.id == articleId }

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

    /** 滚动到列表尾部时调用：按当前 tab 拉下一页追加。 */
    private fun loadMore() {
        if (isLoadingMore || !hasMore) return
        if (loadMoreJob?.isActive == true) return
        loadMoreJob = viewModelScope.launch {
            isLoadingMore = true
            val current = _pagedArticles.value
            val page = loadTabPage(PAGE_SIZE, current.size)
            // 去重保护：任何 DB 删除（归档清理/单篇删除的本地移除）都会让 OFFSET
            // 位移，下一页可能与快照尾部重叠；重复 id 会让 LazyColumn 的 key
            // 冲突直接崩溃（实测 "Key 50442 was already used"）。ADR-0006 的
            // OFFSET 快照模型缺口，根治方向是 keyset 分页，先在追加边界兜住。
            val loadedIds = current.mapTo(HashSet()) { it.article.id }
            val fresh = page.filterNot { it.article.id in loadedIds }
            _pagedArticles.value = current + fresh
            hasMore = page.size == PAGE_SIZE
            isLoadingMore = false
        }
    }

    /** 按当前 tab 取一页。 */
    private suspend fun loadTabPage(limit: Int, offset: Int): List<ArticleWithFeed> = when (_selectedTab.value) {
        FeedTab.All -> repository.loadArticlesPage(limit, offset)
        FeedTab.Unread -> repository.loadUnreadPage(limit, offset)
        FeedTab.Starred -> repository.loadStarredPage(limit, offset)
        FeedTab.Bookmarked -> repository.loadBookmarkedPage(limit, offset)
    }

    private suspend fun loadFirstPage() {
        val page = loadTabPage(PAGE_SIZE, 0)
        _pagedArticles.value = page
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
