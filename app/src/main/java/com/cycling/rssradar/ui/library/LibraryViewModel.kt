package com.cycling.rssradar.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.core.data.FeedRepository
import com.cycling.rssradar.core.data.db.ArticleWithFeed
import com.cycling.rssradar.core.data.db.FeedEntity
import com.cycling.rssradar.core.data.store.LibraryStore
import com.cycling.rssradar.core.model.library.LibraryRange
import com.cycling.rssradar.core.model.library.LibrarySort
import com.cycling.rssradar.ui.feed.PagedSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 收藏整理页的状态：收藏与稍后读共用一套排序 / 时间范围 / 源筛选，页内切换。
 *
 * 与信息流的分页同策（[PagedSnapshot] 追加去重）——收藏夹在长期使用后同样会累积到
 * 数千条，全量载入不可行。排序交给 SQL（DAO 里三套 ORDER BY），不在内存里排，
 * 否则分页会错位。
 *
 * 三个偏好都落 [LibraryStore]（跨会话记住）：整理是持续动作，每次回来都重置成
 * 默认排序会让人每次都要重选。
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: FeedRepository,
    private val libraryStore: LibraryStore,
) : ViewModel() {

    data class LibraryUiState(
        val articles: List<ArticleWithFeed> = emptyList(),
        /** 当前条件下的**总条数**（不是已载入条数），用于「还能不能加载更多」。 */
        val total: Int = 0,
        val loading: Boolean = false,
        /** false = 收藏（isStarred），true = 稍后读（isBookmarked）。 */
        val showBookmarked: Boolean = false,
        val sort: LibrarySort = LibrarySort.STARRED_AT,
        val range: LibraryRange = LibraryRange.ALL,
        /** 源筛选；null = 全部来源。 */
        val feedId: Long? = null,
        /** 多选的 id 集合；非空即为选择态。 */
        val selection: Set<Long> = emptySet(),
    ) {
        val selecting: Boolean get() = selection.isNotEmpty()
    }

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    /** 源筛选的候选清单（同时用于把 feedId 显示成源名）。 */
    val feeds: StateFlow<List<FeedEntity>> = repository.observeFeeds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var reloadJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        val stored = libraryStore.state.value
        _uiState.value = _uiState.value.copy(
            showBookmarked = stored.showBookmarked,
            sort = stored.sort,
            range = stored.range,
        )
        reload()
    }

    fun setShowBookmarked(show: Boolean) {
        libraryStore.setShowBookmarked(show)
        _uiState.update { it.copy(showBookmarked = show, selection = emptySet()) }
        reload()
    }

    fun setSort(sort: LibrarySort) {
        libraryStore.setSort(sort)
        _uiState.update { it.copy(sort = sort) }
        reload()
    }

    fun setRange(range: LibraryRange) {
        libraryStore.setRange(range)
        _uiState.update { it.copy(range = range) }
        reload()
    }

    fun setFeedFilter(feedId: Long?) {
        _uiState.update { it.copy(feedId = feedId) }
        reload()
    }

    fun toggleSelection(id: Long) {
        _uiState.update { state ->
            val next = if (id in state.selection) state.selection - id else state.selection + id
            state.copy(selection = next)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selection = emptySet()) }
    }

    fun selectAll() {
        _uiState.update { it.copy(selection = it.articles.map { item -> item.article.id }.toSet()) }
    }

    /** 批量移出：收藏页取消收藏，稍后读页取消稍后读。两者都是同一个 boolean 落地。 */
    fun removeSelected() {
        val state = _uiState.value
        val ids = state.selection.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (state.showBookmarked) repository.unbookmarkBatch(ids) else repository.unstarBatch(ids)
            _uiState.update { it.copy(selection = emptySet()) }
            reload()
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.articles.size >= state.total) return
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val current = _uiState.value
            val page = repository.loadLibraryPage(
                starred = !current.showBookmarked,
                sort = current.sort,
                feedId = current.feedId,
                fromMillis = rangeStart(current.range),
                toMillis = null,
                limit = PAGE_SIZE,
                offset = current.articles.size,
            )
            _uiState.update {
                it.copy(
                    articles = PagedSnapshot.append(it.articles, page, keyOf = { item -> item.article.id }),
                    loading = false,
                )
            }
        }
    }

    private fun reload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val state = _uiState.value
            val from = rangeStart(state.range)
            val page = repository.loadLibraryPage(
                starred = !state.showBookmarked,
                sort = state.sort,
                feedId = state.feedId,
                fromMillis = from,
                toMillis = null,
                limit = PAGE_SIZE,
                offset = 0,
            )
            val total = repository.countLibrary(
                starred = !state.showBookmarked,
                feedId = state.feedId,
                fromMillis = from,
                toMillis = null,
            )
            _uiState.update {
                it.copy(articles = page, total = total, loading = false)
            }
        }
    }

    private fun rangeStart(range: LibraryRange): Long? =
        range.days?.let { days -> System.currentTimeMillis() - days * DAY_MILLIS }

    private companion object {
        const val PAGE_SIZE = 30
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
