package com.cycling.rssradar.ui.addsubscription

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.core.data.service.AddFeedResult
import com.cycling.rssradar.core.data.service.DiscoveredFeed
import com.cycling.rssradar.core.data.db.entity.FeedEntity
import com.cycling.rssradar.core.data.qualify.FeedContentTypeGuesser
import com.cycling.rssradar.core.domain.concurrency.quietCatching
import com.cycling.rssradar.core.domain.rss.FeedProbeResult
import com.cycling.rssradar.core.data.service.SubscriptionFlow
import com.cycling.rssradar.core.model.GROUP_DESIGN
import com.cycling.rssradar.core.model.GROUP_DEV
import com.cycling.rssradar.core.model.GROUP_TECH
import com.cycling.rssradar.core.model.rsshub.CatalogSource
import com.cycling.rssradar.core.model.rsshub.RouteCatalogQuery
import com.cycling.rssradar.core.data.rsshub.RouteCatalogStore
import com.cycling.rssradar.core.model.rsshub.RouteCategory
import com.cycling.rssradar.core.model.rsshub.RouteExample
import com.cycling.rssradar.core.model.rsshub.RouteParam
import com.cycling.rssradar.core.domain.rsshub.RoutePath
import com.cycling.rssradar.core.data.rsshub.RssHubInstanceStore
import com.cycling.rssradar.core.model.rsshub.RssHubRoute
import com.cycling.rssradar.core.domain.rsshub.RssHubRoutes
import com.cycling.rssradar.core.data.store.prefs.GroupStore
import com.cycling.rssradar.core.ui.mvi.MviStateViewModel
import com.cycling.rssradar.core.ui.text.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 链接校验结果。 */
sealed interface ValidationInfo {
    val message: UiText
    data object Idle : ValidationInfo { override val message: UiText = UiText.Raw("") }
    data class Valid(val articleCount: Int) : ValidationInfo {
        override val message: UiText get() = UiText.res(R.string.add_valid_ok, articleCount)
    }
    data class Invalid(override val message: UiText) : ValidationInfo
    data class Network(override val message: UiText) : ValidationInfo
    /**
     * 地址本身不是 feed，但自动发现（#5）找到了候选：让用户挑一个。
     * [message] 说明"这不是 feed，发现了 N 个"。
     */
    data class Discovered(override val message: UiText) : ValidationInfo
}

/**
 * 加订阅两步流的两阶段。
 * Catalog = 路由目录（搜索 + 分类 + 列表）；Params = 选中路由后填参数。
 * 步骤切换由 VM 状态承担——selectedRoute 非空即 Params 阶段，
 * 置空即回 Catalog（BackToCatalog）；本 state 同时承载两步共享的数据。
 */
data class AddSubscriptionUiState(
    /** 最终要订阅的地址：可能来自手填，也可能由 RSSHub 路由拼出。 */
    val url: String = "",
    val isValidating: Boolean = false,
    val validation: ValidationInfo = ValidationInfo.Idle,
    val selectedGroup: String = GROUP_TECH,
    /**
     * 用户显式挑的内容类型（ADR-0014）。null = 没挑过，交给 [FeedContentTypeGuesser] 预判。
     *
     * 刻意不用「默认选中文章」来表达未选：订阅 bilibili / 播客这类源时预判本来能猜对，
     * 硬塞一个默认值反而把预判覆盖掉，是退步。
     */
    val selectedContentType: Int? = null,
    val isAdding: Boolean = false,
    val query: String = "",
    val category: String = RouteCategory.ALL,
    /** 选中的路由。直接持有对象：目录是动态数据，没有可反查的静态表。 */
    val selectedRoute: RssHubRoute? = null,
    val paramValues: Map<String, String> = emptyMap(),
    val host: String = RssHubRoutes.DEFAULT_HOST,
    /** 目录正在首次装载（读内置快照或缓存）。 */
    val isCatalogLoading: Boolean = false,
    /** 目录更新中。 */
    val isCatalogRefreshing: Boolean = false,
    val catalogRouteCount: Int = 0,
    /** 自动发现（#5）找到的候选 feed；非空时 UI 列出供选择。 */
    val discovered: List<DiscoveredFeed> = emptyList(),
    val isDiscovering: Boolean = false,
    /** 目录数据的生成时刻；null 表示还没装载。 */
    val catalogGeneratedAt: Long? = null,
    val catalogSource: CatalogSource = CatalogSource.BUILTIN,
    /** 当前检索结果。单独存而不用派生属性：3800 条打分不该在每次 state 拷贝时重算。 */
    val visibleRoutes: List<RssHubRoute> = emptyList(),
    /** Snackbar 文案。原先漏在 UiState 外用 Compose mutableStateOf，渲染时序会与其它字段不同步。 */
    val uiMessage: UiText? = null,
    /** 分组选项。原先硬编码三个常量，与订阅页读注册表各说各话，新建分组这里看不见。 */
    val groupOptions: List<String> = emptyList(),
) {
    /** 当前生效的内容类型：用户挑过就用它，否则按当前地址预判。UI 选中态读这个。 */
    val effectiveContentType: Int
        get() = selectedContentType ?: FeedContentTypeGuesser.guess(url, "")

    /** 当前参数拼出来的完整地址；必填参数没填时为 null。 */
    val builtUrl: String? get() = selectedRoute?.let { RssHubRoutes.buildUrl(it, paramValues, host) }
    /** 还没填的必填参数（顺序与表单一致）。空 = 能生成。缺参数时必须说缺哪个，不能只把按钮置灰。 */
    val missingParams: List<RouteParam>
        get() = selectedRoute?.requiredParams
            ?.filter { paramValues[it.key]?.isBlank() != false }
            .orEmpty()
    val canPreview: Boolean get() = selectedRoute != null && missingParams.isEmpty()
    val isUrlFromRoute: Boolean get() = selectedRoute != null && url.isNotBlank()
    val canSubmit: Boolean get() = url.isNotBlank() && validation is ValidationInfo.Valid && !isAdding
}

/** 加订阅抽屉事件（候选 A，ADR-0003）。 */
sealed interface AddSubscriptionIntent {
    data class UrlChange(val raw: String) : AddSubscriptionIntent
    data class GroupSelected(val group: String) : AddSubscriptionIntent

    data class ContentTypeSelected(val contentType: Int) : AddSubscriptionIntent
    data class QueryChange(val query: String) : AddSubscriptionIntent
    data class CategoryChange(val category: String) : AddSubscriptionIntent
    data class RouteSelected(val route: RssHubRoute) : AddSubscriptionIntent
    /** 从填参步返回路由目录：清空所选路由，目录的搜索/分类等状态原样保留。 */
    data object BackToCatalog : AddSubscriptionIntent
    data class ParamChange(val key: String, val value: String) : AddSubscriptionIntent
    /** 选中一条官方示例：反填参数并直接预览。 */
    data class ExampleSelected(val example: RouteExample) : AddSubscriptionIntent
    data object PreviewRoute : AddSubscriptionIntent
    /** 联网更新路由目录（ADR-0010）。 */
    data object RefreshCatalog : AddSubscriptionIntent
    data object Submit : AddSubscriptionIntent
    data object ConsumeMessage : AddSubscriptionIntent
    /** 采用自动发现（#5）找到的某条候选：填进地址栏并校验。 */
    data class PickDiscovered(val feed: DiscoveredFeed) : AddSubscriptionIntent
}

@HiltViewModel
class AddSubscriptionViewModel @Inject constructor(
    private val subscriptionFlow: SubscriptionFlow,
    private val instanceStore: RssHubInstanceStore,
    private val catalogStore: RouteCatalogStore,
    private val groupStore: GroupStore,
) : ViewModel(), MviStateViewModel<AddSubscriptionIntent, AddSubscriptionUiState> {

    private val _state = MutableStateFlow(AddSubscriptionUiState(host = instanceStore.currentOrDefault()))
    override val uiState: StateFlow<AddSubscriptionUiState> = _state.asStateFlow()

    private var validationJob: Job? = null

    /** 全量路由常驻内存：检索是纯内存打分，不必每次回 Store。 */
    private var allRoutes: List<RssHubRoute> = emptyList()

    init {
        viewModelScope.launch {
            groupStore.state.collect { groups -> _state.update { it.copy(groupOptions = groups) } }
        }
        loadCatalog()
    }

    companion object {
        /** 手填链接的防抖；点「生成并预览」是明确意图，直接发请求。 */
        private const val VALIDATE_DEBOUNCE_MS = 400L
        /**
         * 整个探测（含重试）的兜底超时。HTTP 层是 10s 连接 / 20s 读取，读超时会自动
         * 重试一次，最坏 60s。这里设 50s 是刻意的：给第二次尝试留 20s——重试的意义
         * 是命中实例缓存（缓存命中通常 1s 内返回），若第二次还要再耗满 20s，说明不是
         * 缓存冷的问题，早点给结论更实在。被这里掐断时 probe 为 null，报「请求超时」，
         * 同样是真话。
         */
        private const val PROBE_TIMEOUT_MS = 50_000L
    }

    override fun onIntent(intent: AddSubscriptionIntent) {
        when (intent) {
            is AddSubscriptionIntent.UrlChange -> urlChange(intent.raw)
            is AddSubscriptionIntent.GroupSelected -> groupSelected(intent.group)
            is AddSubscriptionIntent.ContentTypeSelected -> contentTypeSelected(intent.contentType)
            is AddSubscriptionIntent.QueryChange -> queryChange(intent.query)
            is AddSubscriptionIntent.CategoryChange -> categoryChange(intent.category)
            is AddSubscriptionIntent.RouteSelected -> routeSelected(intent.route)
            AddSubscriptionIntent.BackToCatalog -> backToCatalog()
            is AddSubscriptionIntent.ParamChange -> paramChange(intent.key, intent.value)
            is AddSubscriptionIntent.ExampleSelected -> exampleSelected(intent.example)
            AddSubscriptionIntent.PreviewRoute -> previewRoute()
            AddSubscriptionIntent.RefreshCatalog -> refreshCatalog()
            AddSubscriptionIntent.Submit -> submit()
            AddSubscriptionIntent.ConsumeMessage -> _state.update { it.copy(uiMessage = null) }
            is AddSubscriptionIntent.PickDiscovered -> pickDiscovered(intent.feed)
        }
    }

    /* ------------------------------ 路由目录 ------------------------------ */

    private fun loadCatalog() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isCatalogLoading = true)
            val catalog = catalogStore.load()
            allRoutes = catalog.routes
            _state.value = _state.value.copy(
                isCatalogLoading = false,
                catalogRouteCount = catalog.routes.size,
                catalogGeneratedAt = catalog.generatedAtMillis,
                catalogSource = catalog.source,
                visibleRoutes = search(),
            )
        }
    }

    /** 联网更新目录；失败只提示，不影响已装载的目录继续用。 */
    private fun refreshCatalog() {
        if (_state.value.isCatalogRefreshing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isCatalogRefreshing = true)
            catalogStore.refresh()
                .onSuccess { count ->
                    allRoutes = catalogStore.catalog.value?.routes.orEmpty()
                    _state.value = _state.value.copy(
                        isCatalogRefreshing = false,
                        catalogRouteCount = count,
                        catalogGeneratedAt = System.currentTimeMillis(),
                        catalogSource = CatalogSource.UPDATED,
                        visibleRoutes = search(),
                    )
                    _state.update { it.copy(uiMessage = UiText.res(R.string.add_catalog_refreshed, count)) }
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(isCatalogRefreshing = false)
                    val reason = error.message?.let { msg -> UiText.Raw(msg) }
                        ?: UiText.res(R.string.add_err_network)
                    _state.update {
                        it.copy(uiMessage = UiText.res(R.string.add_catalog_refresh_failed, reason))
                    }
                }
        }
    }

    private fun search(
        query: String = _state.value.query,
        category: String = _state.value.category,
    ): List<RssHubRoute> = RouteCatalogQuery.search(allRoutes, query, category)

    private fun queryChange(query: String) {
        _state.value = _state.value.copy(query = query, visibleRoutes = search(query = query))
    }

    private fun categoryChange(category: String) {
        _state.value = _state.value.copy(category = category, visibleRoutes = search(category = category))
    }

    /* ------------------------------- 填参数 ------------------------------- */

    /**
     * 选中路由 → 记录所选路由并清空手填痕迹；UI 依 selectedRoute 切到填参步。
     * 参数预填元数据给的默认值（可选值与 default），用户改或点示例都行。
     */
    private fun routeSelected(route: RssHubRoute) {
        val defaults = route.params.mapNotNull { param -> param.fallback?.let { param.key to it } }.toMap()
        _state.value = _state.value.copy(
            selectedRoute = route,
            paramValues = defaults,
            url = "",
            validation = ValidationInfo.Idle,
            selectedGroup = route.suggestedGroup,
        )
        validationJob?.cancel()
    }

    /** 返回路由目录：只清所选路由，搜索词 / 分类 / 已填参数保留，方便换个路由或改主意。 */
    private fun backToCatalog() {
        validationJob?.cancel()
        _state.value = _state.value.copy(
            selectedRoute = null,
            paramValues = emptyMap(),
            url = "",
            validation = ValidationInfo.Idle,
        )
    }

    private fun paramChange(key: String, value: String) {
        // 参数一改，之前那次预览/校验就作废了
        validationJob?.cancel()
        _state.value = _state.value.copy(
            paramValues = _state.value.paramValues.toMutableMap().apply { put(key, value) },
            url = "",
            validation = ValidationInfo.Idle,
        )
    }

    /**
     * 选官方示例：按模板反解出参数值，填满表单并直接发起预览——
     * 示例是 RSSHub 文档里跑通过的真实值，比让用户猜 uid 可靠得多。
     */
    private fun exampleSelected(example: RouteExample) {
        val route = _state.value.selectedRoute ?: return
        val values = RoutePath.match(route.path, example.path)
        if (values == null) {
            _state.update { it.copy(uiMessage = UiText.res(R.string.add_err_example_mismatch)) }
            return
        }
        validationJob?.cancel()
        _state.value = _state.value.copy(
            paramValues = values,
            url = "",
            validation = ValidationInfo.Idle,
        )
        previewRoute()
    }

    /**
     * 生成并预览：把拼好的地址塞进统一的 url 通道，走与手填相同的校验（少数步骤不同，见 [validate]）。
     *
     * 必填参数没填时**必须给出反馈**——早期版本只把按钮置灰，点了毫无动静，
     * 用户只会以为功能坏了。这里就地报错 + 弹提示，把缺哪个参数说清楚。
     */
    private fun previewRoute() {
        // 实例可能刚在「我的 → RSSHub 实例」改过：以设置里的当前值为准，别用抽屉打开时的旧值
        _state.value = _state.value.copy(host = instanceStore.currentOrDefault())
        val state = _state.value
        val missing = state.missingParams
        if (missing.isNotEmpty()) {
            val names = missing.joinToString("、") { it.label.ifBlank { it.key } }
            val hint = UiText.res(R.string.add_err_missing_params, names)
            _state.value = _state.value.copy(validation = ValidationInfo.Invalid(hint))
            _state.update { it.copy(uiMessage = hint) }
            return
        }
        val built = state.builtUrl
        if (built == null) {
            _state.update { it.copy(uiMessage = UiText.res(R.string.add_err_build_failed)) }
            return
        }
        validate(built, fromRoute = true)
    }

    /* ------------------------------ 手填链接 ------------------------------ */

    private fun urlChange(raw: String) {
        validate(raw, fromRoute = false)
    }

    /**
     * 统一校验：记录地址 → 联网探测 → 落到 [ValidationInfo]。
     *
     * [fromRoute] = 地址由路由拼出，与手填有两处不同：
     * 1. **不做自动发现**。RSSHub 对一条路由只会返回 feed 或错误页，去站点 HTML 里翻
     *    feed 候选既得不到有用结果，还要让用户多等几十秒（旧行为：预览一次卡到半分钟无反馈）。
     * 2. **先探活内置实例**。官方实例在部分网络完全不可达，5 秒探活失败就直说，
     *    好过让用户干等到 HTTP 超时；自建实例不做这一步（/healthz 可能被反代挡掉）。
     */
    private fun validate(raw: String, fromRoute: Boolean) {
        _state.value = _state.value.copy(url = raw, validation = ValidationInfo.Idle)
        validationJob?.cancel()
        if (raw.isBlank()) return
        validationJob = viewModelScope.launch {
            if (!fromRoute) delay(VALIDATE_DEBOUNCE_MS) // 手填才防抖；点按钮是明确意图，立即发
            _state.value = _state.value.copy(isValidating = true)
            val host = _state.value.host
            // 只对内置实例用 /healthz 探活当判据：形态已知，探活可信，5 秒就能说"这个实例不通"。
            // 自建实例可能把 /healthz 挡在反代后面，探活失败不代表 feed 不通，照常请求。
            val probeHost = host in RssHubInstanceStore.BUILTIN_INSTANCES
            if (fromRoute && probeHost && !isReachable(host)) {
                _state.value = _state.value.copy(
                    isValidating = false,
                    validation = ValidationInfo.Network(
                        UiText.res(R.string.add_err_host_unreachable, host),
                    ),
                )
                return@launch
            }
            val probe = quietCatching {
                withTimeoutOrNull(PROBE_TIMEOUT_MS) { subscriptionFlow.probeFeed(raw) }
            }.getOrNull()
            if (probe is FeedProbeResult.Valid) {
                _state.value = _state.value.copy(
                    isValidating = false,
                    discovered = emptyList(),
                    validation = ValidationInfo.Valid(probe.articleCount),
                )
                return@launch
            }
            // 手填不是 feed 地址 → 试着从站点里发现（#5）。贴个首页也能订阅，这是订阅体验的下限。
            if (!fromRoute) {
                _state.value = _state.value.copy(isValidating = false, isDiscovering = true)
                val found = quietCatching { subscriptionFlow.discoverFeeds(raw) }.getOrDefault(emptyList())
                _state.value = _state.value.copy(
                    isDiscovering = false,
                    discovered = found,
                    validation = if (found.isNotEmpty()) {
                        ValidationInfo.Discovered(UiText.res(R.string.add_discovered, found.size))
                    } else {
                        validationOf(probe, fromRoute)
                    },
                )
                return@launch
            }
            _state.value = _state.value.copy(isValidating = false, validation = validationOf(probe, fromRoute))
        }
    }

    private suspend fun isReachable(host: String): Boolean =
        quietCatching { instanceStore.isReachable(host) }.getOrDefault(false)

    /**
     * 探测结果 → 用户能照着做点什么的一句话。
     *
     * 每一条都必须指向**不同的处置动作**。曾经所有 IOException 共用一个
     * NetworkError，于是「实例抓上游太慢」被说成「连不上这个地址」——
     * 用户网络好好的，只能去换实例，而换实例解决不了慢。
     */
    private fun validationOf(probe: FeedProbeResult?, fromRoute: Boolean): ValidationInfo = when (probe) {
        // 被 PROBE_TIMEOUT_MS 兜底掐断（含重试也没赶上）：还是慢，不是连不上
        null -> ValidationInfo.Network(UiText.res(R.string.add_err_slow))
        is FeedProbeResult.Valid -> ValidationInfo.Valid(probe.articleCount)
        FeedProbeResult.InvalidUrl -> ValidationInfo.Invalid(UiText.res(R.string.add_err_bad_url))
        is FeedProbeResult.HttpError -> httpErrorInfo(probe.code, fromRoute)
        is FeedProbeResult.Timeout -> if (probe.connecting) {
            ValidationInfo.Network(UiText.res(R.string.add_err_connect_timeout))
        } else {
            // 已自动重试过一次仍超时，把这点说出来，否则用户会以为只试了一次
            ValidationInfo.Network(UiText.res(R.string.add_err_slow_retry))
        }
        FeedProbeResult.DnsError -> ValidationInfo.Network(UiText.res(R.string.add_err_dns))
        FeedProbeResult.CertificateError -> ValidationInfo.Network(UiText.res(R.string.add_err_cert))
        FeedProbeResult.NetworkError -> ValidationInfo.Network(UiText.res(R.string.add_err_refused))
        FeedProbeResult.InvalidFeed -> if (fromRoute) {
            ValidationInfo.Invalid(UiText.res(R.string.add_err_invalid_from_route))
        } else {
            ValidationInfo.Invalid(UiText.res(R.string.add_err_no_feed))
        }
    }

    /**
     * 服务端真实回了响应但不成功。这里的关键是**别什么都赖网络**——
     * 公共实例的日常失败各有各的原因，说错了用户就无从下手。
     */
    private fun httpErrorInfo(code: Int, fromRoute: Boolean): ValidationInfo = when (code) {
        404 -> ValidationInfo.Invalid(
            if (fromRoute) UiText.res(R.string.add_err_404_route)
            else UiText.res(R.string.add_err_404),
        )
        429 -> ValidationInfo.Network(UiText.res(R.string.add_err_429))
        401, 403 -> ValidationInfo.Network(UiText.res(R.string.add_err_denied, code))
        in 500..599 -> ValidationInfo.Network(UiText.res(R.string.add_err_5xx, code))
        else -> ValidationInfo.Network(UiText.res(R.string.add_err_http, code))
    }

    /** 采用发现结果：地址栏换成候选地址，再走一次常规校验（成功后即可订阅）。 */
    private fun pickDiscovered(feed: DiscoveredFeed) {
        validationJob?.cancel()
        validationJob = viewModelScope.launch {
            _state.value = _state.value.copy(isValidating = true)
            val probe = quietCatching { subscriptionFlow.probeFeed(feed.url) }.getOrNull()
            _state.value = _state.value.copy(
                url = feed.url,
                isValidating = false,
                discovered = emptyList(),
                // 走同一套分类：候选地址失败的原因也各不相同（慢 / DNS / 证书 / 404），
                // 一句「暂时无法访问」把用户能做的事全抹掉了
                validation = if (probe == null) {
                    ValidationInfo.Network(UiText.res(R.string.add_err_no_result))
                } else {
                    validationOf(probe, fromRoute = false)
                },
            )
        }
    }

    private fun groupSelected(group: String) {
        _state.value = _state.value.copy(selectedGroup = group)
    }

    private fun contentTypeSelected(contentType: Int) {
        _state.value = _state.value.copy(selectedContentType = contentType)
    }

    /**
     * 订阅成功后清空「一次添加流程」的状态，回到目录步。
     *
     * 页面关闭不需要它：VM 绑在加订阅路由的 backStackEntry 上，退出即销毁，
     * 下次进入天然是干净实例（原先那套「Activity 作用域 + 手动 onDismissed」已随之删除）。
     */
    private fun reset() {
        validationJob?.cancel()
        // 实例与目录信息不属于「一次添加流程」，重置时保留
        val current = _state.value
        _state.value = AddSubscriptionUiState(
            host = current.host,
            catalogRouteCount = current.catalogRouteCount,
            catalogGeneratedAt = current.catalogGeneratedAt,
            catalogSource = current.catalogSource,
            visibleRoutes = RouteCatalogQuery.search(allRoutes, "", RouteCategory.ALL),
        )
        _state.update { it.copy(uiMessage = null) }
    }

    /**
     * 抽屉每次打开时调用：确认当前实例还活着，不活就自己换一个。
     *
     * 官方实例 rsshub.app 在部分网络完全不可达（issue #14，实测连接直接超时），
     * 而默认 host 恰恰就是它；此前只有手动去设置页点「自动探测」才会换。
     * 结果就是用户第一次预览必失败，还以为是自己网络坏了。
     *
     * 自定义实例不自动换：那是用户显式指定的，只提示，不覆盖。
     */
    fun onShown() {
        viewModelScope.launch {
            val host = instanceStore.currentOrDefault()
            _state.value = _state.value.copy(host = host)
            if (isReachable(host)) return@launch
            if (instanceStore.customHost != null) {
                _state.update {
                    it.copy(uiMessage = UiText.res(R.string.add_err_custom_unreachable, host))
                }
                return@launch
            }
            val found = instanceStore.refreshAvailableHost()
            if (found == null) {
                _state.update { it.copy(uiMessage = UiText.res(R.string.add_err_all_unreachable)) }
                return@launch
            }
            _state.value = _state.value.copy(host = found)
            _state.update { it.copy(uiMessage = UiText.res(R.string.add_switched_host, found)) }
        }
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
            // 内容类型：用户没挑过就传 null，让 addFeed 用完整信号（含标题）再预判一次
            val result = subscriptionFlow.addFeed(
                rawUrl = state.url.trim(),
                groupName = state.selectedGroup,
                sourceType = sourceType,
                contentType = state.selectedContentType,
            )
            _state.value = _state.value.copy(isAdding = false)
            _state.update {
                it.copy(
                    uiMessage = when (result) {
                        AddFeedResult.Success -> UiText.res(R.string.add_ok_success)
                        AddFeedResult.Duplicate -> UiText.res(R.string.add_ok_duplicate)
                        AddFeedResult.InvalidFeed -> UiText.res(R.string.add_err_invalid_feed)
                        AddFeedResult.NetworkError -> UiText.res(R.string.add_err_network_retry)
                    }
                )
            }
            if (result == AddFeedResult.Success) reset()
        }
    }
}
