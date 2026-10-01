package com.cycling.rssradar.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.core.data.db.entity.ArticleEntity
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.core.data.db.entity.FeedEntity
import com.cycling.rssradar.core.data.FeedRepository
import com.cycling.rssradar.core.data.ai.AiRepository
import com.cycling.rssradar.core.domain.search.SearchFilters
import com.cycling.rssradar.core.model.library.LibraryRange
import com.cycling.rssradar.ui.feed.PagedSnapshot
import com.cycling.rssradar.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


data class SearchUiState(
    val query: String = "",
    val history: List<String> = defaultHistory,
    val results: List<ArticleWithFeed> = emptyList(),
    /** 当前条件下的**总命中数**（不是已载入条数）。0 与"还没搜"要能区分，见 [searched]。 */
    val hits: Int = 0,
    /** 已经出过一次结果（哪怕 0 条）。没有它就无法区分「没搜过」与「确实没有」。 */
    val searched: Boolean = false,
    val loading: Boolean = false,
    val filters: SearchFilters = SearchFilters.None,
    /** 时间范围的原始选择，用于在筛选条上显示当前档位。 */
    val range: LibraryRange = LibraryRange.ALL,
    /** 最近删除的文章（issue #46 撤销删除）：Snackbar 期内暂存。 */
    val pendingUndoDelete: ArticleEntity? = null,
)

private val defaultHistory = listOf("RSSHub 自部署", "Flutter 3.32", "周刊 305")

/** 搜索事件（候选 A，ADR-0003）。长按菜单动作与信息流一致（issue #46）。 */
sealed interface SearchIntent {
    data class QueryChange(val value: String) : SearchIntent
    data object Submit : SearchIntent
    data object ClearHistory : SearchIntent
    /** 删除单条历史（UI 审计 S2）。 */
    data class DeleteHistoryItem(val value: String) : SearchIntent
    data class SetRead(val articleId: Long, val read: Boolean) : SearchIntent
    data class ToggleStarred(val articleId: Long) : SearchIntent
    data class ToggleBookmarked(val articleId: Long) : SearchIntent
    data class DeleteArticle(val articleId: Long) : SearchIntent
    data object UndoDeleteArticle : SearchIntent
    data object DiscardUndo : SearchIntent

    // —— 二次筛选（三万字库下的必选项）：改任一项都重跑第一页 ——
    data class SetFeedFilter(val feedId: Long?) : SearchIntent
    data class SetRange(val range: LibraryRange) : SearchIntent
    data object ToggleUnreadOnly : SearchIntent
    data object ToggleStarredOnly : SearchIntent
    data object ToggleBookmarkedOnly : SearchIntent
    data object ClearFilters : SearchIntent
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: FeedRepository,
    private val aiRepository: AiRepository,
) : ViewModel(), MviViewModel<SearchIntent> {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /** 源筛选的候选清单（同时用于把 feedId 显示成源名）。 */
    val feeds: StateFlow<List<FeedEntity>> = repository.observeFeeds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    override fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChange -> queryChange(intent.value)
            SearchIntent.Submit -> submit()
            SearchIntent.ClearHistory -> clearHistory()
            is SearchIntent.DeleteHistoryItem -> _state.value =
                _state.value.copy(history = _state.value.history - intent.value)
            is SearchIntent.SetRead -> setRead(intent.articleId, intent.read)
            is SearchIntent.ToggleStarred -> toggleStarred(intent.articleId)
            is SearchIntent.ToggleBookmarked -> toggleBookmarked(intent.articleId)
            is SearchIntent.DeleteArticle -> deleteArticle(intent.articleId)
            SearchIntent.UndoDeleteArticle -> undoDelete()
            SearchIntent.DiscardUndo -> _state.value = _state.value.copy(pendingUndoDelete = null)
            is SearchIntent.SetFeedFilter -> applyFilters { it.copy(feedId = intent.feedId) }
            is SearchIntent.SetRange -> {
                _state.update { it.copy(range = intent.range) }
                applyFilters { it.copy(fromMillis = rangeStart(intent.range), toMillis = null) }
            }
            SearchIntent.ToggleUnreadOnly -> applyFilters { it.copy(unreadOnly = !it.unreadOnly) }
            SearchIntent.ToggleStarredOnly -> applyFilters { it.copy(starredOnly = !it.starredOnly) }
            SearchIntent.ToggleBookmarkedOnly -> applyFilters { it.copy(bookmarkedOnly = !it.bookmarkedOnly) }
            SearchIntent.ClearFilters -> {
                _state.update { it.copy(range = LibraryRange.ALL) }
                applyFilters { SearchFilters.None }
            }
        }
    }

    /**
     * 筛选变化即重跑第一页。**游标回到顶部**——换了条件还停在第 5 页的位置没有意义，
     * 而且偏移会与新的结果集对不上。
     */
    private fun applyFilters(transform: (SearchFilters) -> SearchFilters) {
        _state.update { it.copy(filters = transform(it.filters)) }
        runSearch()
    }

    fun loadMore() {
        val state = _state.value
        if (state.loading || state.results.size >= state.hits || state.query.isBlank()) return
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val current = _state.value
            val page = repository.searchPage(
                raw = current.query,
                filters = current.filters,
                limit = PAGE_SIZE,
                offset = current.results.size,
            )
            _state.update {
                it.copy(
                    results = PagedSnapshot.append(
                        it.results,
                        page.articles,
                        keyOf = { item -> item.article.id },
                    ),
                    loading = false,
                )
            }
        }
    }

    private fun setRead(articleId: Long, read: Boolean) {
        viewModelScope.launch { repository.setRead(articleId, read) }
    }

    private fun toggleStarred(articleId: Long) {
        val current = currentArticle(articleId)?.article?.isStarred ?: false
        viewModelScope.launch { repository.setStarred(articleId, !current) }
    }

    private fun toggleBookmarked(articleId: Long) {
        val current = currentArticle(articleId)?.article?.isBookmarked ?: false
        viewModelScope.launch { repository.setBookmarked(articleId, !current) }
    }

    /** 删除单篇：暂存实体供撤销，清译文缓存。 */
    private fun deleteArticle(articleId: Long) {
        viewModelScope.launch {
            val deleted = repository.deleteArticle(articleId) ?: return@launch
            aiRepository.clearTranslationCache(articleId)
            _state.value = _state.value.copy(pendingUndoDelete = deleted)
        }
    }

    private fun undoDelete() {
        val entity = _state.value.pendingUndoDelete ?: return
        _state.value = _state.value.copy(pendingUndoDelete = null)
        viewModelScope.launch { repository.restoreArticle(entity) }
    }

    private fun currentArticle(articleId: Long): ArticleWithFeed? =
        _state.value.results.firstOrNull { it.article.id == articleId }

    private fun queryChange(value: String) {
        _state.value = _state.value.copy(query = value)
        runSearch()
    }

    /**
     * 走 FTS 的搜索（[com.cycling.rssradar.core.data.FeedRepository.searchPage]），
     * 不再是 `LIKE %q%` 全表扫。250ms 防抖靠 job 取消实现——取消掉 delay 就等于去抖，
     * 不必再引入一条 flow 链。
     */
    private fun runSearch() {
        searchJob?.cancel()
        val raw = _state.value.query.trim()
        if (raw.isEmpty()) {
            _state.update { it.copy(results = emptyList(), hits = 0, searched = false, loading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            _state.update { it.copy(loading = true) }
            val state = _state.value
            val page = repository.searchPage(raw, state.filters, PAGE_SIZE, 0)
            _state.update {
                it.copy(results = page.articles, hits = page.hits, searched = true, loading = false)
            }
        }
    }

    private fun submit() {
        val current = _state.value.query.trim()
        if (current.isEmpty()) return
        val newHistory = (listOf(current) + _state.value.history.filter { it != current })
            .take(10)
        _state.value = _state.value.copy(history = newHistory)
    }

    private fun clearHistory() {
        _state.value = _state.value.copy(history = emptyList())
    }

    private fun rangeStart(range: LibraryRange): Long? =
        range.days?.let { days -> System.currentTimeMillis() - days * DAY_MILLIS }

    private companion object {
        const val PAGE_SIZE = 30
        const val DEBOUNCE_MILLIS = 250L
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
