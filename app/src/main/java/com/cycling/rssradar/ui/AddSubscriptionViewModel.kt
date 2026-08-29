package com.cycling.rssradar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cycling.rssradar.AppContainer
import com.cycling.rssradar.data.AddFeedResult
import com.cycling.rssradar.data.FeedProbeResult
import com.cycling.rssradar.data.FeedRepository
import com.cycling.rssradar.data.GROUP_DESIGN
import com.cycling.rssradar.data.GROUP_DEV
import com.cycling.rssradar.data.GROUP_TECH
import com.cycling.rssradar.data.RouteCategory
import com.cycling.rssradar.data.RssHubRoute
import com.cycling.rssradar.data.RssHubRoutes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/**
 * 添加订阅抽屉的两个阶段。
 * Catalog = 路由目录（搜索 + 分类 + 列表）；Params = 选中路由后填参数。
 */
enum class AddSheetStep { Catalog, Params }

data class AddSubscriptionUiState(
    /** 最终要订阅的地址：可能来自手填，也可能由 RSSHub 路由拼出。 */
    val url: String = "",
    val isValidating: Boolean = false,
    val validation: ValidationInfo = ValidationInfo.Idle,
    val selectedGroup: String = GROUP_TECH,
    val isAdding: Boolean = false,
    val step: AddSheetStep = AddSheetStep.Catalog,
    val query: String = "",
    val category: String = RouteCategory.ALL,
    val selectedRouteId: String? = null,
    val paramValues: Map<String, String> = emptyMap(),
    val host: String = RssHubRoutes.DEFAULT_HOST,
) {
    val selectedRoute: RssHubRoute? get() = selectedRouteId?.let { RssHubRoutes.byId(it) }
    val visibleRoutes: List<RssHubRoute> get() = RssHubRoutes.search(query, category)
    /** 当前参数拼出来的完整地址；没选路由时为 null。 */
    val builtUrl: String? get() = selectedRoute?.let { RssHubRoutes.buildUrl(it, paramValues, host) }
    /** 内置路由表里没有「必填且没兜底值」的参数，所以选了路由就能预览。 */
    val canPreview: Boolean get() = selectedRoute != null
    val isUrlFromRoute: Boolean get() = selectedRoute != null && url.isNotBlank()
    val canSubmit: Boolean get() = url.isNotBlank() && validation is ValidationInfo.Valid && !isAdding
}

class AddSubscriptionViewModel(
    private val repository: FeedRepository,
    /** 当前 RSSHub 实例。由宿主注入（实例探测见 issue #14），默认官方实例。 */
    hostProvider: () -> String = { RssHubRoutes.DEFAULT_HOST },
) : ViewModel() {

    private val _state = MutableStateFlow(AddSubscriptionUiState(host = hostProvider()))
    val state: StateFlow<AddSubscriptionUiState> = _state.asStateFlow()

    /** 分组选项。与订阅页保持一致，避免两处各写一份。 */
    val groupOptions: List<String> = listOf(GROUP_TECH, GROUP_DEV, GROUP_DESIGN)

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
                    is FeedProbeResult.Valid -> ValidationInfo.Valid(probe.articleCount)
                    FeedProbeResult.InvalidUrl -> ValidationInfo.Invalid("链接格式不正确")
                    FeedProbeResult.InvalidFeed -> ValidationInfo.Invalid("不是有效的 RSS/Atom 源")
                    FeedProbeResult.NetworkError -> ValidationInfo.Network("无法访问链接，请检查网络")
                },
            )
        }
    }

    fun onGroupSelected(group: String) {
        _state.value = _state.value.copy(selectedGroup = group)
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
    }

    fun onCategoryChange(category: String) {
        _state.value = _state.value.copy(category = category)
    }

    /** 选中路由 → 进入参数阶段。参数留空，由 placeholder 兜底出示例值。 */
    fun onRouteSelected(route: RssHubRoute) {
        _state.value = _state.value.copy(
            step = AddSheetStep.Params,
            selectedRouteId = route.id,
            paramValues = emptyMap(),
            url = "",
            validation = ValidationInfo.Idle,
            selectedGroup = route.suggestedGroup,
        )
        validationJob?.cancel()
    }

    fun onParamChange(key: String, value: String) {
        val next = _state.value.paramValues.toMutableMap().apply { put(key, value) }
        // 参数一改，之前那次预览/校验就作废了
        validationJob?.cancel()
        _state.value = _state.value.copy(
            paramValues = next,
            url = "",
            validation = ValidationInfo.Idle,
        )
    }

    /** 从参数阶段退回目录。 */
    fun onBackToCatalog() {
        validationJob?.cancel()
        _state.value = _state.value.copy(
            step = AddSheetStep.Catalog,
            selectedRouteId = null,
            paramValues = emptyMap(),
            url = "",
            validation = ValidationInfo.Idle,
        )
    }

    /** 把拼好的地址塞进统一的 url 通道，走与手填完全相同的校验。 */
    fun onPreviewRoute() {
        val built = _state.value.builtUrl ?: return
        onUrlChange(built)
    }

    /** 抽屉关闭 / 添加成功后调用，避免下次打开残留上一次的状态。 */
    fun reset() {
        validationJob?.cancel()
        _state.value = AddSubscriptionUiState()
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
            if (result == AddFeedResult.Success) reset()
        }
    }

    fun onMessageShown() {
        uiMessage = null
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AddSubscriptionViewModel(
                        repository = container.repository,
                        hostProvider = { container.instanceStore.currentOrDefault() },
                    )
                }
            }
    }
}
