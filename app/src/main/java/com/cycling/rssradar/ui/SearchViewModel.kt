package com.cycling.rssradar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cycling.rssradar.data.ArticleWithFeed
import com.cycling.rssradar.data.FeedRepository
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
)

private val defaultHistory = listOf("RSSHub 自部署", "Flutter 3.32", "周刊 305")

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchViewModel(private val repository: FeedRepository) : ViewModel() {

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

    fun onQueryChange(value: String) {
        _state.value = _state.value.copy(query = value)
        queryFlow.value = value
    }

    fun submit() {
        val current = _state.value.query.trim()
        if (current.isEmpty()) return
        val newHistory = (listOf(current) + _state.value.history.filter { it != current })
            .take(10)
        _state.value = _state.value.copy(history = newHistory)
    }

    fun clearHistory() {
        _state.value = _state.value.copy(history = emptyList())
    }

    companion object {
        fun factory(container: com.cycling.rssradar.AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SearchViewModel(container.repository) }
            }
    }
}
