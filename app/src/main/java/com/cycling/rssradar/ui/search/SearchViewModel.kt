package com.cycling.rssradar.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.core.data.db.ArticleEntity
import com.cycling.rssradar.core.data.db.ArticleWithFeed
import com.cycling.rssradar.core.data.FeedRepository
import com.cycling.rssradar.core.data.ai.AiRepository
import com.cycling.rssradar.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


data class SearchUiState(
    val query: String = "",
    val history: List<String> = defaultHistory,
    val results: List<ArticleWithFeed> = emptyList(),
    /** 最近删除的文章（issue #46 撤销删除）：Snackbar 期内暂存。 */
    val pendingUndoDelete: ArticleEntity? = null,
)

private val defaultHistory = listOf("RSSHub 自部署", "Flutter 3.32", "周刊 305")

/** 搜索事件（候选 A，ADR-0003）。长按菜单动作与信息流一致（issue #46）。 */
sealed interface SearchIntent {
    data class QueryChange(val value: String) : SearchIntent
    data object Submit : SearchIntent
    data object ClearHistory : SearchIntent
    data class SetRead(val articleId: Long, val read: Boolean) : SearchIntent
    data class ToggleStarred(val articleId: Long) : SearchIntent
    data class ToggleBookmarked(val articleId: Long) : SearchIntent
    data class DeleteArticle(val articleId: Long) : SearchIntent
    data object UndoDeleteArticle : SearchIntent
    data object DiscardUndo : SearchIntent
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: FeedRepository,
    private val aiRepository: AiRepository,
) : ViewModel(), MviViewModel<SearchIntent> {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(250)
                .flatMapLatest { q ->
                    if (q.isBlank()) flowOf(emptyList())
                    else repository.search(q)
                }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
                .collect { results ->
                    _state.value = _state.value.copy(results = results)
                }
        }
    }

    override fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChange -> queryChange(intent.value)
            SearchIntent.Submit -> submit()
            SearchIntent.ClearHistory -> clearHistory()
            is SearchIntent.SetRead -> setRead(intent.articleId, intent.read)
            is SearchIntent.ToggleStarred -> toggleStarred(intent.articleId)
            is SearchIntent.ToggleBookmarked -> toggleBookmarked(intent.articleId)
            is SearchIntent.DeleteArticle -> deleteArticle(intent.articleId)
            SearchIntent.UndoDeleteArticle -> undoDelete()
            SearchIntent.DiscardUndo -> _state.value = _state.value.copy(pendingUndoDelete = null)
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
        queryFlow.value = value
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
}
