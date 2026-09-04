package com.cycling.rssradar.ui.me

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.core.model.rsshub.CatalogSource
import com.cycling.rssradar.core.data.rsshub.RouteCatalogStore
import com.cycling.rssradar.core.data.rsshub.RssHubInstanceStore
import com.cycling.rssradar.core.data.store.AiStore
import com.cycling.rssradar.core.data.store.ArchiveStore
import com.cycling.rssradar.core.data.store.KeepArchived
import com.cycling.rssradar.core.data.store.ListDescMode
import com.cycling.rssradar.core.data.store.ListDisplayState
import com.cycling.rssradar.core.data.notify.NotificationHelper
import com.cycling.rssradar.core.data.store.LinkOpenMode
import com.cycling.rssradar.core.data.store.LinkShareState
import com.cycling.rssradar.core.data.store.LinkStore
import com.cycling.rssradar.core.data.store.ListDisplayStore
import com.cycling.rssradar.core.data.store.NotificationStore
import com.cycling.rssradar.core.data.store.RecommendationStore
import com.cycling.rssradar.core.data.store.ShareContentFormat
import com.cycling.rssradar.core.data.store.SyncInterval
import com.cycling.rssradar.core.data.store.SyncState
import com.cycling.rssradar.core.data.store.SyncStore
import com.cycling.rssradar.core.data.store.ThemeMode
import com.cycling.rssradar.core.data.store.ThemeStore
import com.cycling.rssradar.sync.SyncScheduler
import com.cycling.rssradar.ui.components.tabBarBottomClearance
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.OnAccent
import com.cycling.rssradar.ui.theme.Surface1
import com.cycling.rssradar.ui.theme.TextPrimary
import com.cycling.rssradar.ui.theme.TextSecondary
import com.cycling.rssradar.ui.theme.TextTertiary
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


data class RssHubSettingsUiState(
    /** 当前生效的实例。 */
    val activeHost: String = "",
    /** 用户自定义实例输入。 */
    val customInput: String = "",
    val probing: Boolean = false,
    /** 最近一次探测的提示文案；null 表示没有要展示的提示。 */
    val probeMessage: String? = null,
    /** 当前主题模式。 */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** DeepSeek API Key 输入（issue #44）。 */
    val aiKeyInput: String = "",
    /** 是否已配置 Key（用于状态展示，不回显完整 Key）。 */
    val aiKeyConfigured: Boolean = false,
    /** AI Key 保存的提示文案。 */
    val aiMessage: String? = null,
    /** 信息流列表显示项（issue #56）。 */
    val listDisplay: ListDisplayState = ListDisplayState(),
    /** 归档保留档位（issue #57）。 */
    val keepArchived: KeepArchived = KeepArchived.ALWAYS,
    /** 自动同步状态（issue #58）。 */
    val sync: SyncState = SyncState(),
    /** 外链打开方式与分享格式（#26）。 */
    val linkShare: LinkShareState = LinkShareState(),
    /** 推荐流开关（ADR-0013）。 */
    val recommendationEnabled: Boolean = true,
    /** 新文章通知总开关（#31）。 */
    val notifyEnabled: Boolean = false,
    /** 系统通知权限是否已授予（Android 13+）；true = 低版本无需权限。 */
    val notifyPermissionGranted: Boolean = true,
    /** 通知设置的提示文案（权限被拒时说明原因）。 */
    val notifyMessage: String? = null,
    /** 路由目录（issue #59）：条数 / 数据时间 / 来源。 */
    val catalogRouteCount: Int = 0,
    val catalogGeneratedAt: Long? = null,
    val catalogSource: CatalogSource = CatalogSource.BUILTIN,
    val catalogRefreshing: Boolean = false,
    /** 目录更新结果的提示文案。 */
    val catalogMessage: String? = null,
)

/**
 * 「我的」页 ViewModel：全部设置偏好的读写中枢。
 * 四个设置二级页（SettingsSubPages.kt）各持有独立实例，均从 Store 读真值。
 */
@HiltViewModel
class RssHubSettingsViewModel @Inject constructor(
    private val store: RssHubInstanceStore,
    private val themeStore: ThemeStore,
    private val aiStore: AiStore,
    private val listDisplayStore: ListDisplayStore,
    private val archiveStore: ArchiveStore,
    private val syncStore: SyncStore,
    private val notificationStore: NotificationStore,
    private val recommendationStore: RecommendationStore,
    private val linkStore: LinkStore,
    private val catalogStore: RouteCatalogStore,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(
        RssHubSettingsUiState(
            activeHost = store.currentOrDefault(),
            aiKeyInput = aiStore.apiKey.orEmpty(),
            linkShare = linkStore.state.value,
            notifyEnabled = notificationStore.state.value,
            recommendationEnabled = recommendationStore.state.value,
            notifyPermissionGranted = NotificationHelper.hasPermission(appContext),
            aiKeyConfigured = aiStore.hasKey(),
        ),
    )
    val state: StateFlow<RssHubSettingsUiState> = _state.asStateFlow()

    init {
        // 主题模式跟随 ThemeStore 的 flow，设置页外（系统切换）也同步
        viewModelScope.launch {
            themeStore.mode.collect { mode ->
                _state.value = _state.value.copy(themeMode = mode)
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
            notificationStore.state.collect { enabled ->
                _state.value = _state.value.copy(notifyEnabled = enabled)
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
                        catalogMessage = "已更新，共 $count 条路由",
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        catalogRefreshing = false,
                        catalogMessage = "更新失败：${error.message ?: "网络错误"}",
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
            notifyMessage = if (enabled && !granted) "请在系统弹窗中允许通知权限" else null,
        )
        if (enabled && !granted) return // 等权限结果回来（见 onNotifyPermissionResult）
        notificationStore.set(enabled)
    }

    /** 权限请求结果回填：给了就开开关，没给就关掉并如实说明。 */
    fun onNotifyPermissionResult(granted: Boolean) {
        notificationStore.set(granted)
        _state.value = _state.value.copy(
            notifyPermissionGranted = granted,
            notifyMessage = if (granted) null else "没有通知权限，无法开启新文章通知",
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
            aiMessage = if (key.isEmpty()) "已清除 API Key" else "API Key 已保存",
        )
    }

    fun saveCustomHost() {
        val raw = _state.value.customInput.trim()
        if (raw.isEmpty()) {
            store.customHost = null
            _state.value = _state.value.copy(activeHost = store.currentOrDefault(), probeMessage = "已清除自定义实例")
            return
        }
        val normalized = normalizeHost(raw) ?: run {
            _state.value = _state.value.copy(probeMessage = "实例地址格式不正确")
            return
        }
        store.customHost = normalized
        _state.value = _state.value.copy(
            activeHost = store.currentOrDefault(),
            customInput = normalized,
            probeMessage = "已保存：$normalized",
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
                    "探测到可用实例：$available"
                } else {
                    "所有内置实例均不可达，请检查网络或填入自建实例"
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

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

/**
 * 「我的」页主页：只放分组入口，具体设置收进四个二级页（SettingsSubPages.kt）。
 * 之前 12+ 个分组平铺一屏滚不到底，现按 iOS 设置的分组导航收敛。
 */
@Composable
fun RssHubSettingsScreen(
    viewModel: RssHubSettingsViewModel,
    modifier: Modifier = Modifier,
    onOpenGeneral: () -> Unit = {},
    onOpenSync: () -> Unit = {},
    onOpenRssHub: () -> Unit = {},
    onOpenAiDiag: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                end = 20.dp,
                // 底部让位悬浮 TabBar（含导航栏 inset）
                bottom = tabBarBottomClearance(),
            ),
    ) {
        Text(
            text = "我的",
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp),
        )

        SettingsEntryCard(
            icon = Lucide.Palette,
            title = "通用",
            summary = themeModeLabel(state.themeMode),
            onClick = onOpenGeneral,
        )
        Spacer(Modifier.height(10.dp))
        SettingsEntryCard(
            icon = Lucide.RefreshCw,
            title = "同步与清理",
            summary = state.sync.interval.label,
            onClick = onOpenSync,
        )
        Spacer(Modifier.height(10.dp))
        SettingsEntryCard(
            icon = Lucide.Server,
            title = "RSSHub",
            summary = state.activeHost,
            onClick = onOpenRssHub,
        )
        Spacer(Modifier.height(10.dp))
        SettingsEntryCard(
            icon = Lucide.Bot,
            title = "AI 与诊断",
            summary = if (state.aiKeyConfigured) "已配置" else "未配置",
            onClick = onOpenAiDiag,
        )
        Spacer(Modifier.height(32.dp))
    }
}

/** 主页分组入口卡：图标 + 名称 + 当前值摘要 + 箭头。 */
@Composable
private fun SettingsEntryCard(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Surface1,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = summary,
                color = TextTertiary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Lucide.ChevronRight,
                contentDescription = "进入",
                tint = TextTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
