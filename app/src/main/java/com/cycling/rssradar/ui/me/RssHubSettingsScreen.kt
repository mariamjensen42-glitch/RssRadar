package com.cycling.rssradar.ui.me

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.data.rsshub.CatalogSource
import com.cycling.rssradar.data.rsshub.RouteCatalogStore
import com.cycling.rssradar.data.rsshub.RssHubInstanceStore
import com.cycling.rssradar.data.store.AiStore
import com.cycling.rssradar.data.store.ArchiveStore
import com.cycling.rssradar.data.store.KeepArchived
import com.cycling.rssradar.data.store.ListDescMode
import com.cycling.rssradar.data.store.ListDisplayState
import com.cycling.rssradar.data.notify.NotificationHelper
import com.cycling.rssradar.data.store.LinkOpenMode
import com.cycling.rssradar.data.store.LinkShareState
import com.cycling.rssradar.data.store.LinkStore
import com.cycling.rssradar.data.store.ListDisplayStore
import com.cycling.rssradar.data.store.NotificationStore
import com.cycling.rssradar.data.store.RecommendationStore
import com.cycling.rssradar.data.store.ShareContentFormat
import com.cycling.rssradar.data.store.SyncInterval
import com.cycling.rssradar.data.store.SyncState
import com.cycling.rssradar.data.store.SyncStore
import com.cycling.rssradar.data.store.ThemeMode
import com.cycling.rssradar.data.store.ThemeStore
import com.cycling.rssradar.sync.SyncScheduler
import com.cycling.rssradar.ui.components.OptionPickerSheet
import com.cycling.rssradar.ui.components.tabBarBottomClearance
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.BgRoot
import com.cycling.rssradar.ui.theme.OnAccent
import com.cycling.rssradar.ui.theme.Surface1
import com.cycling.rssradar.ui.theme.Surface2
import com.cycling.rssradar.ui.theme.Surface3
import com.cycling.rssradar.ui.theme.TextPrimary
import com.cycling.rssradar.ui.theme.TextSecondary
import com.cycling.rssradar.ui.theme.TextTertiary
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleCheckBig
import com.composables.icons.lucide.Lucide
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
 * 「我的」页：RSSHub 实例设置 + 主题设置 + AI 设置 + 列表显示设置。
 * 实例：查看当前实例、修改自定义实例、并发探测可达性（issue #14）。
 * 主题：浅色 / 深色 / 跟随系统（issue #9）。
 * AI：DeepSeek API Key 配置（issue #44，ADR-0005）。
 * 列表显示：信息流卡片显示项逐项可配（issue #56）。
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

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnAccent,
                checkedTrackColor = Accent,
            ),
        )
    }
}

/** 「标签 + 当前值 + 箭头」的跳转行（归档保留期同款形态，链接/分享偏好复用）。 */
@Composable
private fun OptionRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = Accent,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Lucide.ChevronRight,
            contentDescription = "选择",
            tint = TextTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 目录数据时间精确到分钟：更新完能一眼看出「确实换了」。 */
private fun formatCatalogTimestamp(millis: Long?): String {
    if (millis == null) return "—"
    return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(millis))
}

@Composable
fun RssHubSettingsScreen(
    viewModel: RssHubSettingsViewModel,
    modifier: Modifier = Modifier,
    /** 打开全文抓取诊断页（ADR-0012）。 */
    onOpenFetchDiagnostics: () -> Unit = {},
    /** 打开兴趣画像页（ADR-0013）。 */
    onOpenInterestProfile: () -> Unit = {},
    /** 打开崩溃日志页（issue #61）。 */
    onOpenCrashLog: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var showKeepSheet by remember { mutableStateOf(false) }
    var showIntervalSheet by remember { mutableStateOf(false) }
    var showLinkModeSheet by remember { mutableStateOf(false) }
    var showShareFormatSheet by remember { mutableStateOf(false) }
    // Android 13+ 的通知运行时权限：用户点开开关时才请求，不在进页面时打扰
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onNotifyPermissionResult(granted) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                end = 20.dp,
                // 底部让位悬浮 TabBar（含导航栏 inset），页尾设置项能完整滚出胶囊
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

        Text(
            text = "外观",
            color = TextSecondary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(14.dp), color = Surface1) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "主题",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            val selected = state.themeMode == mode
                            val bg = if (selected) Accent else Surface2
                            val fg = if (selected) OnAccent else TextPrimary
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = bg,
                                modifier = Modifier.clickable { viewModel.setThemeMode(mode) },
                            ) {
                                Text(
                                    text = when (mode) {
                                        ThemeMode.SYSTEM -> "跟随系统"
                                        ThemeMode.LIGHT -> "浅色"
                                        ThemeMode.DARK -> "深色"
                                    },
                                    color = fg,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ---- 列表显示（issue #56）----
        Text(
            text = "列表显示",
            color = TextSecondary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "信息流与订阅源文章列表卡片的显示项，全局生效，即改即见。",
            color = TextTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = Surface1) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                val display = state.listDisplay
                SettingSwitchRow(
                    label = "订阅源图标",
                    checked = display.showFeedIcon,
                    onChange = { v -> viewModel.updateListDisplay { it.copy(showFeedIcon = v) } },
                )
                SettingSwitchRow(
                    label = "订阅源名称",
                    checked = display.showFeedName,
                    onChange = { v -> viewModel.updateListDisplay { it.copy(showFeedName = v) } },
                )
                SettingSwitchRow(
                    label = "日期",
                    checked = display.showDate,
                    onChange = { v -> viewModel.updateListDisplay { it.copy(showDate = v) } },
                )
                SettingSwitchRow(
                    label = "缩略图",
                    checked = display.showThumbnail,
                    onChange = { v -> viewModel.updateListDisplay { it.copy(showThumbnail = v) } },
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "描述",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ListDescMode.entries.forEach { mode ->
                            val selected = display.descMode == mode
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (selected) Accent else Surface2,
                                modifier = Modifier.clickable {
                                    viewModel.updateListDisplay { it.copy(descMode = mode) }
                                },
                            ) {
                                Text(
                                    text = mode.label,
                                    color = if (selected) OnAccent else TextPrimary,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
                SettingSwitchRow(
                    label = "粘性日期头",
                    checked = display.stickyDateHeader,
                    onChange = { v -> viewModel.updateListDisplay { it.copy(stickyDateHeader = v) } },
                )
                SettingSwitchRow(
                    label = "已读弱化",
                    checked = display.dimRead,
                    onChange = { v -> viewModel.updateListDisplay { it.copy(dimRead = v) } },
                )
                // 滚动自动标记已读（#11）：卡片滚出视口顶部即标为已读。默认关——
                // 会改变用户数据，必须显式选择。
                SettingSwitchRow(
                    label = "滚动时自动标记已读",
                    checked = display.markReadOnScroll,
                    onChange = { v -> viewModel.updateListDisplay { it.copy(markReadOnScroll = v) } },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- 推荐流（ADR-0013）----
        Text(
            text = "推荐",
            color = TextSecondary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "按你的真实阅读行为给未读文章排序，全部计算在本机完成，画像不上传。",
            color = TextTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = Surface1) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                SettingSwitchRow(
                    label = "显示「推荐」标签页",
                    checked = state.recommendationEnabled,
                    onChange = viewModel::setRecommendationEnabled,
                )
                if (state.recommendationEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenInterestProfile)
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "兴趣画像",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Lucide.ChevronRight,
                            contentDescription = "进入",
                            tint = TextTertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- 文章清理（issue #57）----
        Text(
            text = "文章清理",
            color = TextSecondary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "发布时间超过所选期限的文章会被清理（打开应用时和自动同步完成后执行）；收藏与稍后读永不清理。",
            color = TextTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = Surface1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showKeepSheet = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "发布超过",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = state.keepArchived.label,
                    color = Accent,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- 链接与分享（#26）----
        Text(
            text = "链接与分享",
            color = TextSecondary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "外链怎么打开、分享文章时带哪些内容。阅读页顶栏可分享本文。",
            color = TextTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = Surface1) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                OptionRow(
                    label = "打开链接",
                    value = state.linkShare.linkOpenMode.label,
                    onClick = { showLinkModeSheet = true },
                )
                OptionRow(
                    label = "分享内容",
                    value = state.linkShare.shareFormat.label,
                    onClick = { showShareFormatSheet = true },
                )
                Text(
                    text = "Custom Tabs（应用内打开）需引入 androidx.browser 依赖，暂未提供。",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- 新文章通知（#31）----
        Text(
            text = "新文章通知",
            color = TextSecondary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "自动同步后发现新文章时发一条汇总通知。逐个订阅源可在订阅操作页单独关闭。",
            color = TextTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = Surface1) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                SettingSwitchRow(
                    label = "开启通知",
                    checked = state.notifyEnabled,
                    onChange = { enabled ->
                        if (enabled && !state.notifyPermissionGranted && needsNotificationPermission()) {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setNotifyEnabled(enabled)
                        }
                    },
                )
                state.notifyMessage?.let { message ->
                    Text(
                        text = message,
                        color = TextTertiary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- 自动同步（issue #58）----
        Text(
            text = "自动同步",
            color = TextSecondary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "后台周期刷新订阅源，同步完成后按保留天数清理归档。手动刷新不受这些限制。",
            color = TextTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = Surface1) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showIntervalSheet = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "同步间隔",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = state.sync.interval.label,
                        color = Accent,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                SettingSwitchRow(
                    label = "仅 WiFi",
                    checked = state.sync.onlyOnWifi,
                    onChange = { v -> viewModel.updateSync { it.copy(onlyOnWifi = v) } },
                )
                SettingSwitchRow(
                    label = "仅充电",
                    checked = state.sync.onlyWhenCharging,
                    onChange = { v -> viewModel.updateSync { it.copy(onlyWhenCharging = v) } },
                )
                SettingSwitchRow(
                    label = "启动时同步",
                    checked = state.sync.syncOnStart,
                    onChange = { v -> viewModel.updateSync { it.copy(syncOnStart = v) } },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- 路由目录（issue #59）----
        Text(
            text = "路由目录",
            color = TextSecondary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "内置 RSSHub 全量路由，加订阅时可搜索与分类筛选。官方新增路由后联网更新目录即可同步。",
            color = TextTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = Surface1) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "收录路由",
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (state.catalogRouteCount > 0) "${state.catalogRouteCount} 条" else "装载中…",
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "数据时间",
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatCatalogTimestamp(state.catalogGeneratedAt) +
                            if (state.catalogSource == CatalogSource.UPDATED) "（已更新）" else "（内置）",
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = viewModel::refreshCatalog,
                    enabled = !state.catalogRefreshing,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = OnAccent,
                    ),
                ) {
                    if (state.catalogRefreshing) {
                        CircularProgressIndicator(color = OnAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("更新中…", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text("更新路由目录", style = MaterialTheme.typography.labelLarge)
                    }
                }
                state.catalogMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = message, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "RSSHub 实例",
            color = TextSecondary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "路由解析由 RSSHub 实例完成。官方实例在部分网络环境不可达，可自动探测或填入自建实例。",
            color = TextTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        Surface(shape = RoundedCornerShape(14.dp), color = Surface1) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "当前实例",
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = state.activeHost,
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = viewModel::probeNow,
                    enabled = !state.probing,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = OnAccent,
                    ),
                ) {
                    if (state.probing) {
                        CircularProgressIndicator(color = OnAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("探测中…", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text("自动探测可用实例", style = MaterialTheme.typography.labelLarge)
                    }
                }
                state.probeMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = message, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "自定义实例（可选）",
            color = TextSecondary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.customInput,
            onValueChange = viewModel::onCustomInputChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://your-rsshub.example.com", color = TextTertiary, style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Surface2,
                unfocusedContainerColor = Surface2,
                focusedBorderColor = Accent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = Accent,
            ),
        )
        Text(
            text = "自定义实例优先于探测结果；留空并保存则清除。",
            color = TextTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = viewModel::saveCustomHost) {
            Text("保存", color = Accent, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(24.dp))

        // ---- AI（DeepSeek）----
        Text(
            text = "AI（DeepSeek）",
            color = TextSecondary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "详情页的 AI 摘要与翻译由 DeepSeek 提供，使用你自己的 API Key，费用与额度由你掌控。",
            color = TextTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = Surface1) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "状态",
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (state.aiKeyConfigured) "已配置" else "未配置",
                        color = if (state.aiKeyConfigured) Accent else TextTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.aiKeyInput,
                    onValueChange = viewModel::onAiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("sk-…", color = TextTertiary, style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Surface2,
                        unfocusedContainerColor = Surface2,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Accent,
                    ),
                )
                state.aiMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = message, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = viewModel::saveAiKey) {
                    Text("保存 Key", color = Accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ---- 正文抓取（ADR-0012）----
        Spacer(Modifier.height(16.dp))
        Text(
            text = "正文抓取",
            color = TextSecondary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "按需抓原文失败或抓不全时，这里能看到站点、状态码与原因。",
            color = TextTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Surface1,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenFetchDiagnostics),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "全文抓取诊断",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Lucide.ChevronRight,
                    contentDescription = "进入",
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // ---- 崩溃日志（issue #61）----
        Spacer(Modifier.height(16.dp))
        Text(
            text = "诊断",
            color = TextSecondary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "应用崩溃时自动记录异常与设备信息，最多保留 5 份，可导出。",
            color = TextTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Surface1,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenCrashLog),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "崩溃日志",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Lucide.ChevronRight,
                    contentDescription = "进入",
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "内置镜像（点选填入；自动探测取响应最快者）",
            color = TextTertiary,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(6.dp))
        RssHubInstanceStore.BUILTIN_INSTANCES.forEachIndexed { index, host ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Surface1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable { viewModel.onCustomInputChange(host) },
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}. $host",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (host == state.activeHost) {
                        Text("当前", color = Accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showKeepSheet) {
        OptionPickerSheet(
            title = "发布超过",
            options = KeepArchived.entries.toList(),
            selected = state.keepArchived,
            label = { it.label },
            onSelect = viewModel::setKeepArchived,
            onDismiss = { showKeepSheet = false },
        )
    }

    if (showLinkModeSheet) {
        OptionPickerSheet(
            title = "打开链接",
            options = LinkOpenMode.entries.toList(),
            selected = state.linkShare.linkOpenMode,
            label = { it.label },
            onSelect = { mode -> viewModel.updateLinkShare { it.copy(linkOpenMode = mode) } },
            onDismiss = { showLinkModeSheet = false },
        )
    }

    if (showShareFormatSheet) {
        OptionPickerSheet(
            title = "分享内容",
            options = ShareContentFormat.entries.toList(),
            selected = state.linkShare.shareFormat,
            label = { it.label },
            onSelect = { format -> viewModel.updateLinkShare { it.copy(shareFormat = format) } },
            onDismiss = { showShareFormatSheet = false },
        )
    }

    if (showIntervalSheet) {
        OptionPickerSheet(
            title = "同步间隔",
            options = SyncInterval.entries.toList(),
            selected = state.sync.interval,
            label = { it.label },
            onSelect = { interval -> viewModel.updateSync { it.copy(interval = interval) } },
            onDismiss = { showIntervalSheet = false },
        )
    }
}


/** Android 13（API 33）起通知是运行时权限；低版本由系统默认授予。 */
private fun needsNotificationPermission(): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
