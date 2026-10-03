package com.cycling.rssradar.ui.me

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.R
import com.cycling.rssradar.core.data.db.dao.ArticleDao
import com.cycling.rssradar.core.data.db.dao.FeedDao
import com.cycling.rssradar.core.data.notify.NotificationHelper
import com.cycling.rssradar.core.data.rsshub.RouteCatalogStore
import com.cycling.rssradar.core.data.rsshub.RssHubInstanceStore
import com.cycling.rssradar.core.data.store.model.AppLanguage
import com.cycling.rssradar.core.data.store.model.KeepArchived
import com.cycling.rssradar.core.data.store.model.LinkShareState
import com.cycling.rssradar.core.data.store.model.ListDisplayState
import com.cycling.rssradar.core.data.store.model.SyncState
import com.cycling.rssradar.core.data.store.prefs.AiStore
import com.cycling.rssradar.core.data.store.prefs.ArchiveStore
import com.cycling.rssradar.core.data.store.prefs.LanguageStore
import com.cycling.rssradar.core.data.store.prefs.LinkStore
import com.cycling.rssradar.core.data.store.prefs.ListDisplayStore
import com.cycling.rssradar.core.data.store.prefs.NotificationStore
import com.cycling.rssradar.core.data.store.prefs.RecommendationStore
import com.cycling.rssradar.core.data.store.prefs.SyncStore
import com.cycling.rssradar.core.data.store.prefs.ThemeStore
import com.cycling.rssradar.core.data.store.model.ThemeMode
import com.cycling.rssradar.i18n.UiText
import com.cycling.rssradar.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 「我的」页 ViewModel：全部设置偏好的读写中枢。
 * 四个设置二级页（SettingsSubPages.kt）各持有独立实例，均从 Store 读真值。
 */
@HiltViewModel
class RssHubSettingsViewModel @Inject constructor(
    private val store: RssHubInstanceStore,
    /** 统计条数据源：订阅源计数与未读计数直接读 DAO，不经中间层（只读、无业务规则）。 */
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val themeStore: ThemeStore,
    private val languageStore: LanguageStore,
    private val aiStore: AiStore,
    private val listDisplayStore: ListDisplayStore,
    private val archiveStore: ArchiveStore,
    private val syncStore: SyncStore,
    private val notificationStore: NotificationStore,
    private val recommendationStore: RecommendationStore,
    private val linkStore: LinkStore,
    private val catalogStore: RouteCatalogStore,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(
        RssHubSettingsUiState(
            activeHost = store.currentOrDefault(),
            aiKeyInput = aiStore.apiKey.orEmpty(),
            linkShare = linkStore.state.value,
            notifyEnabled = notificationStore.state.value.enabled,
            recommendationEnabled = recommendationStore.state.value,
            notifyPermissionGranted = NotificationHelper.hasPermission(appContext),
            aiKeyConfigured = aiStore.hasKey(),
        ),
    )
    val state: StateFlow<RssHubSettingsUiState> = _state.asStateFlow()

    init {
        // 统计条（数字必须真实）：两条 DB 计数流合并进 UiState
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                feedDao.observeFeedCount(),
                articleDao.observeUnreadCount(),
            ) { feedCount, unreadCount -> feedCount to unreadCount }
                .collect { (feedCount, unreadCount) ->
                    _state.value = _state.value.copy(feedCount = feedCount, unreadCount = unreadCount)
                }
        }
        // 主题模式跟随 ThemeStore 的 flow，设置页外（系统切换）也同步
        viewModelScope.launch {
            themeStore.mode.collect { mode ->
                _state.value = _state.value.copy(themeMode = mode)
            }
        }
        // 界面语言（ADR-0017）
        viewModelScope.launch {
            languageStore.language.collect { language ->
                _state.value = _state.value.copy(appLanguage = language)
            }
        }
        // 动态取色（#27）
        viewModelScope.launch {
            themeStore.dynamicColor.collect { enabled ->
                _state.value = _state.value.copy(dynamicColor = enabled)
            }
        }
        // 自定义强调色（#29）
        viewModelScope.launch {
            themeStore.customAccent.collect { argb ->
                _state.value = _state.value.copy(customAccent = argb)
            }
        }
        // 列表显示项跟随 ListDisplayStore 的 flow（issue #56）
        viewModelScope.launch {
            listDisplayStore.state.collect { display ->
                _state.value = _state.value.copy(listDisplay = display)
            }
        }
        // 归档保留档位跟随 ArchiveStore 的 flow（issue #57）
        viewModelScope.launch {
            archiveStore.state.collect { keep ->
                _state.value = _state.value.copy(keepArchived = keep)
            }
        }
        // 自动同步状态跟随 SyncStore 的 flow（issue #58）
        viewModelScope.launch {
            syncStore.state.collect { sync ->
                _state.value = _state.value.copy(sync = sync)
            }
        }
        // 外链与分享偏好（#26）
        viewModelScope.launch {
            linkStore.state.collect { linkShare ->
                _state.value = _state.value.copy(linkShare = linkShare)
            }
        }
        // 通知总开关（#31）
        viewModelScope.launch {
            notificationStore.state.collect { prefs ->
                _state.value = _state.value.copy(notifyEnabled = prefs.enabled)
            }
        }
        // 推荐流开关（ADR-0013）
        viewModelScope.launch {
            recommendationStore.state.collect { enabled ->
                _state.value = _state.value.copy(recommendationEnabled = enabled)
            }
        }
        // 路由目录（issue #59）：装载一次，之后跟随 Store 的更新广播
        viewModelScope.launch {
            catalogStore.catalog.collect { catalog ->
                if (catalog == null) return@collect
                _state.value = _state.value.copy(
                    catalogRouteCount = catalog.routes.size,
                    catalogGeneratedAt = catalog.generatedAtMillis,
                    catalogSource = catalog.source,
                )
            }
        }
        viewModelScope.launch { catalogStore.load() }
    }

    /** 联网更新路由目录（issue #59）。 */
    fun refreshCatalog() {
        if (_state.value.catalogRefreshing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(catalogRefreshing = true, catalogMessage = null)
            catalogStore.refresh()
                .onSuccess { count ->
                    _state.value = _state.value.copy(
                        catalogRefreshing = false,
                        catalogMessage = UiText.res(R.string.catalog_updated, "$count"),
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        catalogRefreshing = false,
                        catalogMessage = error.message?.let { UiText.res(R.string.catalog_update_failed, it) }
                            ?: UiText.res(R.string.catalog_update_failed_generic),
                    )
                }
        }
    }

    /**
     * 通知总开关（#31）：只改开关不发权限请求——权限由设置页在用户点开时
     * 通过 [onNotifyPermissionResult] 的结果回填。
     */
    fun setNotifyEnabled(enabled: Boolean) {
        val granted = NotificationHelper.hasPermission(appContext)
        _state.value = _state.value.copy(
            notifyPermissionGranted = granted,
            notifyMessage = if (enabled && !granted) UiText.res(R.string.notify_permission_needed) else null,
        )
        if (enabled && !granted) return // 等权限结果回来（见 onNotifyPermissionResult）
        notificationStore.setEnabled(enabled)
    }

    /** 权限请求结果回填：给了就开开关，没给就关掉并如实说明。 */
    fun onNotifyPermissionResult(granted: Boolean) {
        notificationStore.setEnabled(granted)
        _state.value = _state.value.copy(
            notifyPermissionGranted = granted,
            notifyMessage = if (granted) null else UiText.res(R.string.notify_permission_missing),
        )
    }

    /** 推荐流开关（ADR-0013）：关闭后信息流不再显示「推荐」tab。 */
    fun setRecommendationEnabled(enabled: Boolean) {
        recommendationStore.set(enabled)
    }

    /** 外链与分享偏好（#26）。 */
    fun updateLinkShare(transform: (LinkShareState) -> LinkShareState) {
        linkStore.update(transform)
    }

    fun setThemeMode(mode: ThemeMode) {
        themeStore.setMode(mode)
    }

    /** 界面语言（ADR-0017）：只持久化，locale 推送与重建由 UI 层调 AppLocales。 */
    fun setAppLanguage(language: AppLanguage) {
        languageStore.setLanguage(language)
    }

    /**
     * Material You 动态取色（#27）。**开则清掉自定义色**——两个来源同时挂着时
     * 「到底哪个生效」没法向用户解释。
     */
    fun setDynamicColor(enabled: Boolean) {
        themeStore.setDynamicColor(enabled)
        if (enabled) themeStore.setCustomAccent(null)
    }

    /**
     * 自定义强调色（#29）：传 null 回到默认紫。
     * 选了具体颜色就顺手关掉动态取色，同样是为了互斥。
     */
    fun setCustomAccent(argb: Long?) {
        if (argb != null) themeStore.setDynamicColor(false)
        themeStore.setCustomAccent(argb)
    }

    /** 归档保留档位（issue #57）。 */
    fun setKeepArchived(keep: KeepArchived) {
        archiveStore.set(keep)
    }

    /** 自动同步偏好（issue #58）：持久化 + 重建 WorkManager 周期任务。 */
    fun updateSync(transform: (SyncState) -> SyncState) {
        syncStore.update(transform)
        SyncScheduler.reschedule(appContext)
    }

    /** 列表显示项：转交 ListDisplayStore（持久化 + StateFlow 广播，列表即改即见）。 */
    fun updateListDisplay(transform: (ListDisplayState) -> ListDisplayState) {
        listDisplayStore.update(transform)
    }

    fun onCustomInputChange(value: String) {
        _state.value = _state.value.copy(customInput = value)
    }

    /** DeepSeek API Key 输入（issue #44）。 */
    fun onAiKeyChange(value: String) {
        _state.value = _state.value.copy(aiKeyInput = value)
    }

    /** 保存 Key：留空保存 = 清除。 */
    fun saveAiKey() {
        val key = _state.value.aiKeyInput.trim()
        aiStore.apiKey = key.ifEmpty { null }
        _state.value = _state.value.copy(
            aiKeyConfigured = aiStore.hasKey(),
            aiMessage = if (key.isEmpty()) UiText.res(R.string.ai_key_cleared) else UiText.res(R.string.ai_key_saved),
        )
    }

    fun saveCustomHost() {
        val raw = _state.value.customInput.trim()
        if (raw.isEmpty()) {
            store.customHost = null
            _state.value = _state.value.copy(activeHost = store.currentOrDefault(), probeMessage = UiText.res(R.string.instance_cleared))
            return
        }
        val normalized = normalizeHost(raw) ?: run {
            _state.value = _state.value.copy(probeMessage = UiText.res(R.string.instance_invalid))
            return
        }
        store.customHost = normalized
        _state.value = _state.value.copy(
            activeHost = store.currentOrDefault(),
            customInput = normalized,
            probeMessage = UiText.res(R.string.instance_saved, normalized),
        )
    }

    /** 并发探测内置镜像 + 自定义实例，选首个可达者并记住。 */
    fun probeNow() {
        if (_state.value.probing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(probing = true, probeMessage = null)
            val available = store.refreshAvailableHost()
            _state.value = _state.value.copy(
                probing = false,
                activeHost = store.currentOrDefault(),
                probeMessage = if (available != null) {
                    UiText.res(R.string.probe_found, "$available")
                } else {
                    UiText.res(R.string.probe_none)
                },
            )
        }
    }

    private fun normalizeHost(raw: String): String? {
        val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        return runCatching {
            java.net.URL(withScheme).let { it.protocol + "://" + it.host + (it.port.takeIf { p -> p != -1 }?.let { p -> ":$p" } ?: "") }
        }.getOrNull()
    }
}
