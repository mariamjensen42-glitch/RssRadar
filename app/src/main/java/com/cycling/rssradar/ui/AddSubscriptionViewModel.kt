package com.cycling.rssradar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.data.AddFeedResult
import com.cycling.rssradar.data.FeedEntity
import com.cycling.rssradar.data.FeedProbeResult
import com.cycling.rssradar.data.FeedRepository
import com.cycling.rssradar.data.GROUP_DESIGN
import com.cycling.rssradar.data.GROUP_DEV
import com.cycling.rssradar.data.GROUP_TECH
import com.cycling.rssradar.data.RouteCategory
import com.cycling.rssradar.data.RssHubInstanceStore
import com.cycling.rssradar.data.RssHubRoute
import com.cycling.rssradar.data.RssHubRoutes
import com.cycling.rssradar.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
 * 加订阅两步流的两阶段。
 * Catalog = 路由目录（搜索 + 分类 + 列表）；Params = 选中路由后填参数。
 * 注意：步骤切换由导航承担（嵌套 nav graph，issue #33）——Catalog→Params 是
 * 目的地跳转（popBackStack 返回），不再走 VM 状态；本 state 只承载两步共享的数据。
 */
data class AddSubscriptionUiState(
    /** 最终要订阅的地址：可能来自手填，也可能由 RSSHub 路由拼出。 */
    val url: String = "",
    val isValidating: Boolean = false,
    val validation: ValidationInfo = ValidationInfo.Idle,
    val selectedGroup: String = GROUP_TECH,
    val isAdding: Boolean = false,
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

/** 加订阅抽屉事件（候选 A，ADR-0003）。 */
sealed interface AddSubscriptionIntent {
    data class UrlChange(val raw: String) : AddSubscriptionIntent
    data class GroupSelected(val group: String) : AddSubscriptionIntent
    data class QueryChange(val query: String) : AddSubscriptionIntent
    data class CategoryChange(val category: String) : AddSubscriptionIntent
    data class RouteSelected(val route: RssHubRoute) : AddSubscriptionIntent
    data class ParamChange(val key: String, val value: String) : AddSubscriptionIntent
    data object PreviewRoute : AddSubscriptionIntent
    data object Submit : AddSubscriptionIntent
    data object ConsumeMessage : AddSubscriptionIntent
}

@HiltViewModel
class AddSubscriptionViewModel @Inject constructor(
    private val repository: FeedRepository,
    private val instanceStore: RssHubInstanceStore,
) : ViewModel(), MviViewModel<AddSubscriptionIntent> {

    private val _state = MutableStateFlow(AddSubscriptionUiState(host = instanceStore.currentOrDefault()))
    val state: StateFlow<AddSubscriptionUiState> = _state.asStateFlow()

    /** 分组选项。与订阅页保持一致，避免两处各写一份。 */
    val groupOptions: List<String> = listOf(GROUP_TECH, GROUP_DEV, GROUP_DESIGN)

    var uiMessage by mutableStateOf<String?>(null)
        private set

    private var validationJob: Job? = null

    override fun onIntent(intent: AddSubscriptionIntent) {
        when (intent) {
            is AddSubscriptionIntent.UrlChange -> urlChange(intent.raw)
            is AddSubscriptionIntent.GroupSelected -> groupSelected(intent.group)
            is AddSubscriptionIntent.QueryChange -> queryChange(intent.query)
            is AddSubscriptionIntent.CategoryChange -> categoryChange(intent.category)
            is AddSubscriptionIntent.RouteSelected -> routeSelected(intent.route)
            is AddSubscriptionIntent.ParamChange -> paramChange(intent.key, intent.value)
            AddSubscriptionIntent.PreviewRoute -> previewRoute()
            AddSubscriptionIntent.Submit -> submit()
            AddSubscriptionIntent.ConsumeMessage -> uiMessage = null
        }
    }

    private fun urlChange(raw: String) {
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

    private fun groupSelected(group: String) {
        _state.value = _state.value.copy(selectedGroup = group)
    }

    private fun queryChange(query: String) {
        _state.value = _state.value.copy(query = query)
    }

    private fun categoryChange(category: String) {
        _state.value = _state.value.copy(category = category)
    }

    /** 选中路由 → 记录所选路由并清空手填痕迹；进入 Params 阶段由导航（目的地跳转）承担。参数留空，由 placeholder 兜底出示例值。 */
    private fun routeSelected(route: RssHubRoute) {
        _state.value = _state.value.copy(
            selectedRouteId = route.id,
            paramValues = emptyMap(),
            url = "",
            validation = ValidationInfo.Idle,
            selectedGroup = route.suggestedGroup,
        )
        validationJob?.cancel()
    }

    private fun paramChange(key: String, value: String) {
        val next = _state.value.paramValues.toMutableMap().apply { put(key, value) }
        // 参数一改，之前那次预览/校验就作废了
        validationJob?.cancel()
        _state.value = _state.value.copy(
            paramValues = next,
            url = "",
            validation = ValidationInfo.Idle,
        )
    }

    /** 把拼好的地址塞进统一的 url 通道，走与手填完全相同的校验。 */
    private fun previewRoute() {
        val built = _state.value.builtUrl ?: return
        urlChange(built)
    }

    /** 订阅成功后清空状态；抽屉关闭（graph 出栈）时整个 VM 销毁，状态天然重置。 */
    private fun reset() {
        validationJob?.cancel()
        _state.value = AddSubscriptionUiState()
    }

    private fun submit() {
        if (!_state.value.canSubmit) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isAdding = true)
            val state = _state.value
            // 路由拼出来的地址标记为 RSSHub 类型；手填 URL 一律按常规 RSS/Atom。
            val sourceType = if (state.isUrlFromRoute) {
                FeedEntity.SOURCE_TYPE_RSSHUB
            } else {
                FeedEntity.SOURCE_TYPE_RSS
            }
            val result = repository.addFeed(state.url.trim(), state.selectedGroup, sourceType)
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
}
