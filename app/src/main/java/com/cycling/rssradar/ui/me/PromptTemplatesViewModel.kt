package com.cycling.rssradar.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.R
import com.cycling.rssradar.core.data.repository.FeedPromptOverrideRepository
import com.cycling.rssradar.core.ui.text.UiText
import com.cycling.rssradar.core.ui.mvi.MviStateViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 一个已配置摘要提示词覆盖的订阅源。 */
data class FeedPromptOverride(val feedId: Long, val feedTitle: String, val prompt: String)

/** 供「新增覆盖」选择用的订阅源（只带 id 与标题，全部源都可选，不止已配置的）。 */
data class FeedPickOption(val feedId: Long, val feedTitle: String)

data class PromptTemplatesUiState(
    val overrides: List<FeedPromptOverride> = emptyList(),
    val feeds: List<FeedPickOption> = emptyList(),
    val loading: Boolean = true,
    val message: UiText? = null,
)

sealed interface PromptTemplatesIntent {
    data object Refresh : PromptTemplatesIntent
    data class SavePrompt(val feedId: Long, val prompt: String) : PromptTemplatesIntent
    data class ClearPrompt(val feedId: Long) : PromptTemplatesIntent
    data object ConsumeMessage : PromptTemplatesIntent
}

@HiltViewModel
class PromptTemplatesViewModel @Inject constructor(
    private val repository: FeedPromptOverrideRepository,
) : ViewModel(), MviStateViewModel<PromptTemplatesIntent, PromptTemplatesUiState> {

    private val _uiState = MutableStateFlow(PromptTemplatesUiState())
    override val uiState: StateFlow<PromptTemplatesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    override fun onIntent(intent: PromptTemplatesIntent) {
        when (intent) {
            PromptTemplatesIntent.Refresh -> refresh()
            is PromptTemplatesIntent.SavePrompt -> save(intent.feedId, intent.prompt)
            is PromptTemplatesIntent.ClearPrompt -> clear(intent.feedId)
            PromptTemplatesIntent.ConsumeMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val titleOf = repository.allFeedTitles().toMap()
            val ids = repository.overriddenFeedIds()
            val prompts = ids.associateWith { repository.summaryPromptOf(it).orEmpty() }
            _uiState.update {
                it.copy(
                    loading = false,
                    overrides = ids.map { id ->
                        FeedPromptOverride(id, titleOf[id].orEmpty(), prompts[id].orEmpty())
                    }.sortedBy { it.feedTitle },
                    feeds = titleOf.map { (id, title) -> FeedPickOption(id, title) },
                )
            }
        }
    }

    private fun save(feedId: Long, prompt: String) {
        viewModelScope.launch {
            val normalized = prompt.trim().takeIf { it.isNotBlank() }
            repository.saveSummaryPrompt(feedId, normalized)
            _uiState.update {
                it.copy(
                    message = if (normalized == null) {
                        UiText.res(R.string.aimsg_prompt_builtin)
                    } else {
                        UiText.res(R.string.aimsg_prompt_saved)
                    },
                )
            }
            refresh()
        }
    }

    private fun clear(feedId: Long) = save(feedId, "")
}
