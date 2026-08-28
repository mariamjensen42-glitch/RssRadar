package com.cycling.rssradar.ui

import android.text.format.DateUtils
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cycling.rssradar.data.AddFeedResult
import com.cycling.rssradar.data.FeedEntity
import com.cycling.rssradar.data.FeedRepository
import com.cycling.rssradar.data.GROUP_DESIGN
import com.cycling.rssradar.data.GROUP_DEV
import com.cycling.rssradar.data.GROUP_TECH
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 链接校验结果。 */
sealed interface ValidationInfo {
    val message: String
    data object Idle : ValidationInfo { override val message = "" }
    data class Valid(val articleCount: Int) : ValidationInfo {
        override val message = "链接有效，已识别 RSS 2.0 格式，共 $articleCount 篇文章"
    }
    data class Invalid(override val message: String) : ValidationInfo
    data class Network(override val message: String) : ValidationInfo
}

/** RSSHub 路由示例。 */
data class RouteSample(
    val path: String,
    val suggestedTitle: String,
    val suggestedGroup: String,
)

/** 最近添加的订阅 + 显示用时间。 */
data class RecentlyAdded(val feed: FeedEntity, val relativeTime: String)

data class AddSubscriptionUiState(
    val url: String = "",
    val isValidating: Boolean = false,
    val validation: ValidationInfo = ValidationInfo.Idle,
    val selectedGroup: String = GROUP_TECH,
    val routeSamples: List<RouteSample> = defaultRouteSamples(),
    val isAdding: Boolean = false,
) {
    val canSubmit: Boolean get() = url.isNotBlank() && validation is ValidationInfo.Valid && !isAdding
}

private fun defaultRouteSamples(): List<RouteSample> = listOf(
    RouteSample("/sspai/matrix", "少数派 · 矩阵", GROUP_TECH),
    RouteSample("/36kr/newsflashes", "36氪 · 快讯", GROUP_TECH),
    RouteSample("/hackernews/best", "Hacker News · 精选", GROUP_DEV),
)

class AddSubscriptionViewModel(private val repository: FeedRepository) : ViewModel() {

    private val _state = MutableStateFlow(AddSubscriptionUiState())
    val state: StateFlow<AddSubscriptionUiState> = _state.asStateFlow()

    val recentlyAdded: StateFlow<List<RecentlyAdded>> = repository.observeFeeds()
        .map { feeds ->
            feeds
                .sortedByDescending { it.createdAt }
                .take(3)
                .map { feed ->
                    RecentlyAdded(
                        feed = feed,
                        relativeTime = DateUtils.getRelativeTimeSpanString(feed.createdAt).toString(),
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var uiMessage by mutableStateOf<String?>(null)
        private set

    private var validationJob: Job? = null

    fun onUrlChange(raw: String) {
        _state.value = _state.value.copy(url = raw, validation = ValidationInfo.Idle)
        validationJob?.cancel()
        if (raw.isBlank()) return
        validationJob = viewModelScope.launch {
            delay(400) // debounce
            _state.value = _state.value.copy(isValidating = true)
            val probe = repository.probeFeed(raw)
            _state.value = _state.value.copy(
                isValidating = false,
                validation = when (probe) {
                    is com.cycling.rssradar.data.FeedProbeResult.Valid ->
                        ValidationInfo.Valid(probe.articleCount)
                    com.cycling.rssradar.data.FeedProbeResult.InvalidUrl ->
                        ValidationInfo.Invalid("链接格式不正确")
                    com.cycling.rssradar.data.FeedProbeResult.InvalidFeed ->
                        ValidationInfo.Invalid("不是有效的 RSS/Atom 源")
                    com.cycling.rssradar.data.FeedProbeResult.NetworkError ->
                        ValidationInfo.Network("无法访问链接，请检查网络")
                },
            )
        }
    }

    fun onGroupSelected(group: String) {
        _state.value = _state.value.copy(selectedGroup = group)
    }

    fun submit() {
        if (!_state.value.canSubmit) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isAdding = true)
            val result = repository.addFeed(_state.value.url.trim(), _state.value.selectedGroup)
            _state.value = _state.value.copy(isAdding = false)
            uiMessage = when (result) {
                AddFeedResult.Success -> "订阅成功"
                AddFeedResult.Duplicate -> "该源已订阅"
                AddFeedResult.InvalidFeed -> "不是有效的 RSS/Atom 源"
                AddFeedResult.NetworkError -> "网络错误，请检查链接后重试"
            }
            if (result == AddFeedResult.Success) {
                _state.value = _state.value.copy(url = "", validation = ValidationInfo.Idle)
            }
        }
    }

    fun onMessageShown() {
        uiMessage = null
    }

    companion object {
        fun factory(container: com.cycling.rssradar.AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { AddSubscriptionViewModel(container.repository) }
            }
    }
}
