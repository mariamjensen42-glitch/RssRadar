package com.cycling.rssradar.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.data.db.ArticleWithFeed
import com.cycling.rssradar.data.FeedRepository
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

import com.cycling.rssradar.ui.subscriptions.String

data class SearchUiState(
    val query: String = "",
    val history: List<String> = defaultHistory,
    val results: List<ArticleWithFeed> = emptyList(),
)

private val defaultHistory = listOf("RSSHub 自部署", "Flutter 3.32", "周刊 305")

/** 搜索事件（候选 A，ADR-0003）。 */
sealed interface SearchIntent {
    data class QueryChange(val value: String) : SearchIntent
    data object Submit : SearchIntent
    data object ClearHistory : SearchIntent
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: FeedRepository,
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
        }
    }

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
