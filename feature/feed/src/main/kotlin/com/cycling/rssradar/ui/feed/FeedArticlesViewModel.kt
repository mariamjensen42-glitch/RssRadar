package com.cycling.rssradar.ui.feed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.core.data.ai.AiRepository
import com.cycling.rssradar.core.data.db.entity.ArticleEntity
import com.cycling.rssradar.core.data.db.entity.FeedEntity
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.core.data.repository.FeedRepository
import com.cycling.rssradar.core.ui.mvi.MviStateViewModel
import com.cycling.rssradar.core.ui.text.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 订阅源文章列表事件。 */
sealed interface FeedArticlesIntent {
    data object Refresh : FeedArticlesIntent
    data object LoadMore : FeedArticlesIntent
    data class ToggleStarred(val articleId: Long) : FeedArticlesIntent
    data class ToggleBookmarked(val articleId: Long) : FeedArticlesIntent
    data class SetRead(val articleId: Long, val read: Boolean) : FeedArticlesIntent
    data class MarkRead(val articleId: Long) : FeedArticlesIntent
    data class DeleteArticle(val articleId: Long) : FeedArticlesIntent
    data object ConsumeMessage : FeedArticlesIntent
}

/**
 * 订阅源文章列表的渲染输入快照，唯一产出点是 [FeedArticlesViewModel.uiState]。
 * `hasMore` 是分页内部判据，Screen 不消费，故不进快照。
 */
data class FeedArticlesUiState(
    val feed: FeedEntity? = null,
    val articles: List<ArticleWithFeed> = emptyList(),
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val uiMessage: UiText? = null,
)

/**
 * 订阅源文章列表（CONTEXT.md「Feed article list」，issue #51）。
 * 单源全部文章的分页快照，管线与信息流完全一致：
 * LIMIT/OFFSET 每页 30 条、滚动到底预加载、卡片状态 mutateLocal 原地更新。
 */
@HiltViewModel
class FeedArticlesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FeedRepository,
    private val aiRepository: AiRepository,
) : ViewModel(), MviStateViewModel<FeedArticlesIntent, FeedArticlesUiState> {

    /** nav args 在 SavedStateHandle 里的键，与 FeedArticlesRoute 的参数名一致。 */
    private val feedId: Long = savedStateHandle.get<Long>(KEY_FEED_ID)
        ?: savedStateHandle.get<String>(KEY_FEED_ID)?.toLongOrNull()
        ?: -1L

    private val _uiState = MutableStateFlow(FeedArticlesUiState())
    override val uiState: StateFlow<FeedArticlesUiState> = _uiState.asStateFlow()

    private var hasMore = false
    private var loadMoreJob: Job? = null

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(feed = repository.getFeed(feedId)) }
            loadFirstPage()
        }
    }

    override fun onIntent(intent: FeedArticlesIntent) {
        when (intent) {
            FeedArticlesIntent.Refresh -> refresh()
            FeedArticlesIntent.LoadMore -> loadMore()
            is FeedArticlesIntent.ToggleStarred -> toggleStarred(intent.articleId)
            is FeedArticlesIntent.ToggleBookmarked -> toggleBookmarked(intent.articleId)
            is FeedArticlesIntent.SetRead -> setRead(intent.articleId, intent.read)
            is FeedArticlesIntent.MarkRead -> markRead(intent.articleId)
            is FeedArticlesIntent.DeleteArticle -> deleteArticle(intent.articleId)
            FeedArticlesIntent.ConsumeMessage -> _uiState.update { it.copy(uiMessage = null) }
        }
    }

    /** 下拉刷新：仅刷新当前源（秒级），完成后重载首页。 */
    private fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val ok = repository.refreshSingleFeed(feedId)
            loadFirstPage()
            _uiState.update {
                if (ok) {
                    it.copy(isRefreshing = false)
                } else {
                    it.copy(isRefreshing = false, uiMessage = UiText.res(R.string.feed_refresh_failed))
                }
            }
        }
    }

    private fun loadMore() {
        if (_uiState.value.isLoadingMore || !hasMore) return
        if (loadMoreJob?.isActive == true) return
        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val current = _uiState.value.articles
            val page = repository.loadFeedPage(feedId, PAGE_SIZE, current.size)
            // 去重保护（同 FeedListViewModel）：DB 删除使 OFFSET 位移后，
            // 下一页可能与快照尾部重叠，重复 id 会让 LazyColumn key 冲突崩溃。
            val loadedIds = current.mapTo(HashSet()) { it.article.id }
            val fresh = page.filterNot { it.article.id in loadedIds }
            hasMore = page.size == PAGE_SIZE
            _uiState.update { it.copy(articles = current + fresh, isLoadingMore = false) }
        }
    }

    private suspend fun loadFirstPage() {
        val page = repository.loadFeedPage(feedId, PAGE_SIZE, 0)
        hasMore = page.size == PAGE_SIZE
        _uiState.update { it.copy(articles = page) }
    }

    private fun toggleStarred(articleId: Long) {
        val current = _uiState.value.articles.firstOrNull { it.article.id == articleId }?.article?.isStarred ?: false
        viewModelScope.launch {
            repository.setStarred(articleId, !current)
            mutateLocal(articleId) { it.copy(isStarred = !current) }
        }
    }

    private fun toggleBookmarked(articleId: Long) {
        val current = _uiState.value.articles.firstOrNull { it.article.id == articleId }?.article?.isBookmarked ?: false
        viewModelScope.launch {
            repository.setBookmarked(articleId, !current)
            mutateLocal(articleId) { it.copy(isBookmarked = !current) }
        }
    }

    private fun markRead(articleId: Long) {
        viewModelScope.launch {
            repository.markRead(articleId)
            mutateLocal(articleId) { it.copy(isRead = true) }
        }
    }

    private fun setRead(articleId: Long, read: Boolean) {
        viewModelScope.launch {
            repository.setRead(articleId, read)
            mutateLocal(articleId) { it.copy(isRead = read) }
        }
    }

    private fun deleteArticle(articleId: Long) {
        viewModelScope.launch {
            repository.deleteArticle(articleId) ?: return@launch
            aiRepository.clearTranslationCache(articleId)
            _uiState.update { state ->
                state.copy(articles = state.articles.filterNot { it.article.id == articleId })
            }
        }
    }

    /** 分页快照原地更新单篇卡片状态，免去整表重查。 */
    private fun mutateLocal(articleId: Long, transform: (ArticleEntity) -> ArticleEntity) {
        _uiState.update { state ->
            state.copy(
                articles = state.articles.map {
                    if (it.article.id == articleId) it.copy(article = transform(it.article)) else it
                },
            )
        }
    }

    companion object {
        private const val KEY_FEED_ID = "feedId"

        /** 与信息流分页大小保持一致。 */
        const val PAGE_SIZE = FeedListViewModel.PAGE_SIZE
    }
}
