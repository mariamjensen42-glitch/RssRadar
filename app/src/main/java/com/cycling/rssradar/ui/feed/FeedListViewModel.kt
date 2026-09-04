package com.cycling.rssradar.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.data.AddFeedResult
import com.cycling.rssradar.data.db.ArticleEntity
import com.cycling.rssradar.data.db.ArticleWithFeed
import com.cycling.rssradar.data.FeedRepository
import com.cycling.rssradar.data.Recommendation
import com.cycling.rssradar.data.ai.AiRepository
import com.cycling.rssradar.data.store.GroupStore
import com.cycling.rssradar.core.model.MarkAsReadCondition
import com.cycling.rssradar.data.store.RecommendationStore
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

/** 信息流页内的过滤 tab（推荐流为第五个，ADR-0013）。 */
enum class FeedTab { All, Unread, Starred, Bookmarked, Recommended }

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
    /** 推荐流首次加载中（打分在内存里做，不是一瞬间）。 */
    val isRanking: Boolean = false,
    /**
     * 「减少此类」撤销期内暂存的订阅源 id（ADR-0013）。
     * Snackbar 期内可撤销；超时即丢弃（降权保留）。
     */
    val pendingUndoReduceFeedId: Long? = null,
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
    /** 推荐流「减少此类」（ADR-0013）：该文章所属订阅源后续降权。 */
    data class ReduceSuch(val articleId: Long) : FeedListIntent
    /** 撤销「减少此类」。 */
    data object UndoReduceSuch : FeedListIntent
    /** 放弃「减少此类」的撤销机会。 */
    data object DiscardUndoReduce : FeedListIntent
    data object Refresh : FeedListIntent
    data object LoadMore : FeedListIntent
    data class AddFeed(val rawUrl: String, val groupName: String) : FeedListIntent
    /** 批量标记已读（#10）：按条件（1/3/7 天前或全部）清空未读。 */
    data class MarkAllRead(val condition: MarkAsReadCondition) : FeedListIntent
    /** 滚动自动标记已读（#11）：卡片滚出视口后由列表上报的 id 批次。 */
    data class MarkReadPassed(val ids: List<Long>) : FeedListIntent
}

@HiltViewModel
class FeedListViewModel @Inject constructor(
    private val repository: FeedRepository,
    groupStore: GroupStore,
    private val aiRepository: AiRepository,
    /** 推荐流（ADR-0013）：候选池 + 打分 + 负反馈。 */
    private val recommendation: Recommendation,
    recommendationStore: RecommendationStore,
) : ViewModel(), MviViewModel<FeedListIntent> {

    private val _uiState = MutableStateFlow(FeedListUiState())
    val uiState: StateFlow<FeedListUiState> = _uiState.asStateFlow()

    val unreadCount: StateFlow<Int> = repository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 分组清单（注册表），供筛选栏使用。 */
    val groupOptions: StateFlow<List<String>> = MutableStateFlow(groupStore.getGroups())

    /** 推荐流开关状态（ADR-0013）。 */
    val recommendationEnabled: StateFlow<Boolean> = recommendationStore.state

    private var loadMoreJob: Job? = null

    /**
     * 推荐流的排序结果（进 tab 时实时算一次，ADR-0013）。
     * 只存 id 序，文章实体按页切片时才去 DB 还原——不把候选池全文常驻内存。
     */
    private var rankedIds: List<Long> = emptyList()

    init {
        // 首屏拉第一页；空库/异常静默，用户可下拉重试
        viewModelScope.launch { loadFirstPage() }
        // 推荐流在设置里被关掉时，若正停在「推荐」tab 则退回「全部」——
        // 否则用户会看到一个没有对应 chip 的列表（tab 与内容对不上）。
        viewModelScope.launch {
            recommendationEnabled.collect { enabled ->
                if (!enabled && _uiState.value.selectedTab == FeedTab.Recommended) {
                    selectTab(FeedTab.All)
                }
            }
        }
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
            is FeedListIntent.ReduceSuch -> reduceSuch(intent.articleId)
            FeedListIntent.UndoReduceSuch -> undoReduceSuch()
            FeedListIntent.DiscardUndoReduce -> update { it.copy(pendingUndoReduceFeedId = null) }
            FeedListIntent.Refresh -> refresh()
            FeedListIntent.LoadMore -> loadMore()
            is FeedListIntent.AddFeed -> addFeed(intent.rawUrl, intent.groupName)
            is FeedListIntent.MarkAllRead -> markAllRead(intent.condition)
            is FeedListIntent.MarkReadPassed -> markReadPassed(intent.ids)
        }
    }

    /**
     * 批量标记已读（#10）：只写库，不重查整表（数万行重查是 OOM 根源）。
     * 列表内的卡片状态由快照原地翻转，数字来自 DAO 的真实影响行数——不猜、不编。
     */
    private fun markAllRead(condition: MarkAsReadCondition) {
        viewModelScope.launch {
            val count = repository.markReadByCondition(condition)
            update {
                it.copy(
                    articles = it.articles.map { item ->
                        if (item.article.isRead) item else item.copy(article = item.article.copy(isRead = true))
                    },
                    uiMessage = if (count > 0) "已标记 $count 篇为已读" else "没有需要标记的文章",
                )
            }
        }
    }

    /**
     * 滚动自动标记已读（#11）：只更新仍未读的行。卡片状态走快照翻转，
     * 未读 tab 下卡片不会当场消失（下次刷新才移除）——避免滚动时列表在脚下抽掉。
     */
    private fun markReadPassed(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.markReadBatch(ids)
            val passed = ids.toSet()
            update {
                it.copy(
                    articles = it.articles.map { item ->
                        if (item.article.id in passed && !item.article.isRead) {
                            item.copy(article = item.article.copy(isRead = true))
                        } else {
                            item
                        }
                    },
                )
            }
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
            update { it.copy(articles = PagedSnapshot.mutate(it.articles, ::idOf, articleId) { a -> a.copy(article = a.article.copy(isStarred = !current)) }) }
        }
    }

    /** 从自身缓存列表读当前收藏态再翻转（MVI：状态由 VM 持有，不靠 UI 回传 current）。 */
    private fun starredOf(articleId: Long): Boolean =
        _uiState.value.articles.firstOrNull { it.article.id == articleId }?.article?.isStarred ?: false

    private fun markRead(articleId: Long) {
        viewModelScope.launch {
            repository.markRead(articleId)
            update { it.copy(articles = PagedSnapshot.mutate(it.articles, ::idOf, articleId) { a -> a.copy(article = a.article.copy(isRead = true)) }) }
        }
    }

    /** 已读/未读互切：从自身缓存读当前态再翻转。 */
    private fun setRead(articleId: Long, read: Boolean) {
        viewModelScope.launch {
            repository.setRead(articleId, read)
            update { it.copy(articles = PagedSnapshot.mutate(it.articles, ::idOf, articleId) { a -> a.copy(article = a.article.copy(isRead = read)) }) }
        }
    }

    /** 稍后读切换（issue #46）。 */
    private fun toggleBookmarked(articleId: Long) {
        val current = stateOf(articleId)?.article?.isBookmarked ?: false
        viewModelScope.launch {
            repository.setBookmarked(articleId, !current)
            update { it.copy(articles = PagedSnapshot.mutate(it.articles, ::idOf, articleId) { a -> a.copy(article = a.article.copy(isBookmarked = !current)) }) }
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
            val hasFeeds = repository.hasFeeds()
            update {
                it.copy(
                    isRefreshing = false,
                    uiMessage = when {
                        hasFeeds && successCount == 0 -> "刷新失败，展示的是上次内容"
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
            val offset = _uiState.value.articles.size
            val page = loadTabPage(PAGE_SIZE, offset)
            update {
                it.copy(
                    articles = PagedSnapshot.append(it.articles, page, keyOf = ::idOf),
                    hasMore = hasMoreAfter(offset, page.size),
                    isLoadingMore = false,
                )
            }
        }
    }

    /**
     * 是否还有下一页：推荐流的排序结果已在内存里，直接看游标；
     * 其余 tab 靠"上一页是否拉满"判断（SQL LIMIT/OFFSET 分页的常规做法）。
     */
    private fun hasMoreAfter(offset: Int, loaded: Int): Boolean =
        if (_uiState.value.selectedTab == FeedTab.Recommended) {
            offset + loaded < rankedIds.size
        } else {
            loaded == PAGE_SIZE
        }

    /** 按当前 tab 取一页。 */
    private suspend fun loadTabPage(limit: Int, offset: Int): List<ArticleWithFeed> =
        when (_uiState.value.selectedTab) {
            FeedTab.All -> repository.loadArticlesPage(limit, offset)
            FeedTab.Unread -> repository.loadUnreadPage(limit, offset)
            FeedTab.Starred -> repository.loadStarredPage(limit, offset)
            FeedTab.Bookmarked -> repository.loadBookmarkedPage(limit, offset)
            FeedTab.Recommended -> loadRecommendationsPage(limit, offset)
        }

    /**
     * 推荐流分页（ADR-0013）：首屏现算一次排序，之后按游标切片、批量还原文章。
     * 排序不落库——候选池受「未读 + 时间窗」约束，规模可控，落库的失效维护是无底洞。
     */
    private suspend fun loadRecommendationsPage(limit: Int, offset: Int): List<ArticleWithFeed> {
        if (offset == 0) {
            update { it.copy(isRanking = true) }
            rankedIds = recommendation.rank()
            update { it.copy(isRanking = false) }
        }
        val slice = rankedIds.drop(offset).take(limit)
        if (slice.isEmpty()) return emptyList()
        // SQL 的 IN 不保证顺序，按 id 序还原——否则打散白做
        return recommendation.loadOrdered(slice)
    }

    /**
     * 「减少此类」（ADR-0013）：文章所属订阅源在推荐流里降权，可撤销。
     * 降权落地后立刻重排，用户马上看到效果（不靠下次进 tab 才生效）。
     */
    private fun reduceSuch(articleId: Long) {
        viewModelScope.launch {
            val feedId = recommendation.reduceSuch(articleId) ?: return@launch
            update { it.copy(pendingUndoReduceFeedId = feedId) }
            loadFirstPage()
        }
    }

    private fun undoReduceSuch() {
        val feedId = _uiState.value.pendingUndoReduceFeedId ?: return
        update { it.copy(pendingUndoReduceFeedId = null) }
        viewModelScope.launch {
            recommendation.undoReduce(feedId)
            loadFirstPage()
        }
    }

    private suspend fun loadFirstPage() {
        val page = loadTabPage(PAGE_SIZE, 0)
        update {
            it.copy(articles = page, hasMore = hasMoreAfter(0, page.size))
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
