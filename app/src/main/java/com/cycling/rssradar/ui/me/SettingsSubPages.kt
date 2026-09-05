package com.cycling.rssradar.ui.me

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.IconButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.cycling.rssradar.core.model.rsshub.CatalogSource
import com.cycling.rssradar.core.data.rsshub.RssHubInstanceStore
import com.cycling.rssradar.core.data.store.KeepArchived
import com.cycling.rssradar.core.data.store.LinkOpenMode
import com.cycling.rssradar.core.data.store.ListViewMode
import com.cycling.rssradar.core.data.store.ShareContentFormat
import com.cycling.rssradar.core.data.store.SyncInterval
import com.cycling.rssradar.core.data.store.ThemeMode
import com.cycling.rssradar.core.ui.components.OptionPickerSheet
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.cycling.rssradar.core.ui.theme.radarColors

/**
 * 设置二级页（原「我的」长页拆分）：通用 / 同步与清理 / RSSHub / AI 与诊断。
 * 主页只留分组入口（RssHubSettingsScreen），具体项按使用场景归进对应页。
 * 各页持有独立的 [RssHubSettingsViewModel] 实例，状态一律从 Store 读真值。
 */

// —— 公共骨架与小组件 ——

/** 二级页骨架：返回顶栏 + 滚动内容，与 InterestProfileScreen 同款形态。 */
@Composable
private fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(radarColors().bgRoot)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, contentDescription = "返回", tint = radarColors().textPrimary)
            }
            Text(
                text = title,
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            content()
        }
    }
}

/** 分组小标题 + 说明文案（原长页同款）。 */
@Composable
private fun SectionHeader(title: String, description: String? = null) {
    Text(
        text = title,
        color = radarColors().textSecondary,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    if (description != null) {
        Text(
            text = description,
            color = radarColors().textTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
    } else {
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
internal fun SettingSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = radarColors().textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = radarColors().onAccent,
                checkedTrackColor = radarColors().accent,
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
            color = radarColors().textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = radarColors().accent,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Lucide.ChevronRight,
            contentDescription = "选择",
            tint = radarColors().textTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 跳转行（无当前值），如「全文抓取诊断」「崩溃日志」。 */
@Composable
private fun NavigateRow(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = radarColors().surface1,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Lucide.ChevronRight,
                contentDescription = "进入",
                tint = radarColors().textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 目录数据时间精确到分钟：更新完能一眼看出「确实换了」。 */
private fun formatCatalogTimestamp(millis: Long?): String {
    if (millis == null) return "—"
    return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(millis))
}

/** Android 13（API 33）起通知是运行时权限；低版本由系统默认授予。 */
private fun needsNotificationPermission(): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU

// —— 1. 通用：外观 / 列表显示 / 推荐 / 链接与分享 ——

@Composable
fun SettingsGeneralScreen(
    viewModel: RssHubSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenInterestProfile: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var showLinkModeSheet by remember { mutableStateOf(false) }
    var showShareFormatSheet by remember { mutableStateOf(false) }

    SettingsSubPage(title = "通用", onBack = onBack) {
        SectionHeader("外观")
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "主题",
                        color = radarColors().textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            val selected = state.themeMode == mode
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (selected) radarColors().accent else radarColors().surface2,
                                modifier = Modifier.clickable { viewModel.setThemeMode(mode) },
                            ) {
                                Text(
                                    text = when (mode) {
                                        ThemeMode.SYSTEM -> "跟随系统"
                                        ThemeMode.LIGHT -> "浅色"
                                        ThemeMode.DARK -> "深色"
                                    },
                                    color = if (selected) radarColors().onAccent else radarColors().textPrimary,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 列表显示（issue #56）
        SectionHeader(
            "列表显示",
            "信息流与订阅源文章列表卡片的显示项，全局生效，即改即见。",
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                val display = state.listDisplay
                // 视图模式（列表/卡片/杂志/网格）：与信息流顶栏同一份全局偏好
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "视图模式",
                        color = radarColors().textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ListViewMode.entries.forEach { mode ->
                            val selected = display.viewMode == mode
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (selected) radarColors().accent else radarColors().surface2,
                                modifier = Modifier.clickable {
                                    viewModel.updateListDisplay { it.copy(viewMode = mode) }
                                },
                            ) {
                                Text(
                                    text = mode.label,
                                    color = if (selected) radarColors().onAccent else radarColors().textPrimary,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
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
                        color = radarColors().textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.cycling.rssradar.core.data.store.ListDescMode.entries.forEach { mode ->
                            val selected = display.descMode == mode
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (selected) radarColors().accent else radarColors().surface2,
                                modifier = Modifier.clickable {
                                    viewModel.updateListDisplay { it.copy(descMode = mode) }
                                },
                            ) {
                                Text(
                                    text = mode.label,
                                    color = if (selected) radarColors().onAccent else radarColors().textPrimary,
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

        Spacer(Modifier.height(24.dp))

        // 推荐流（ADR-0013）
        SectionHeader(
            "推荐",
            "按你的真实阅读行为给未读文章排序，全部计算在本机完成，画像不上传。",
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
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
                            color = radarColors().textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Lucide.ChevronRight,
                            contentDescription = "进入",
                            tint = radarColors().textTertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 链接与分享（#26）
        SectionHeader(
            "链接与分享",
            "外链怎么打开、分享文章时带哪些内容。阅读页顶栏可分享本文。",
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
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
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
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
}

// —— 2. 同步与清理：自动同步 / 文章清理 / 新文章通知 ——

@Composable
fun SettingsSyncScreen(
    viewModel: RssHubSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var showKeepSheet by remember { mutableStateOf(false) }
    var showIntervalSheet by remember { mutableStateOf(false) }
    // Android 13+ 的通知运行时权限：用户点开开关时才请求，不在进页面时打扰
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onNotifyPermissionResult(granted) }

    SettingsSubPage(title = "同步与清理", onBack = onBack) {
        // 自动同步（issue #58）
        SectionHeader(
            "自动同步",
            "后台周期刷新订阅源，同步完成后按保留天数清理归档。手动刷新不受这些限制。",
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
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
                        color = radarColors().textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = state.sync.interval.label,
                        color = radarColors().accent,
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

        Spacer(Modifier.height(24.dp))

        // 文章清理（issue #57）
        SectionHeader(
            "文章清理",
            "发布时间超过所选期限的文章会被清理（打开应用时和自动同步完成后执行）；收藏与稍后读永不清理。",
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showKeepSheet = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "发布超过",
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = state.keepArchived.label,
                    color = radarColors().accent,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // 新文章通知（#31）
        SectionHeader(
            "新文章通知",
            "自动同步后发现新文章时发一条汇总通知。逐个订阅源可在订阅操作页单独关闭。",
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
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
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
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

// —— 3. RSSHub：实例 / 自定义实例 / 内置镜像 / 路由目录 ——

@Composable
fun SettingsRssHubScreen(
    viewModel: RssHubSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    SettingsSubPage(title = "RSSHub", onBack = onBack) {
        SectionHeader(
            "RSSHub 实例",
            "路由解析由 RSSHub 实例完成。官方实例在部分网络环境不可达，可自动探测或填入自建实例。",
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "当前实例",
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = state.activeHost,
                        color = radarColors().textPrimary,
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
                        containerColor = radarColors().accent,
                        contentColor = radarColors().onAccent,
                    ),
                ) {
                    if (state.probing) {
                        CircularProgressIndicator(color = radarColors().onAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("探测中…", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text("自动探测可用实例", style = MaterialTheme.typography.labelLarge)
                    }
                }
                state.probeMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = message, color = radarColors().textTertiary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "自定义实例（可选）",
            color = radarColors().textSecondary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.customInput,
            onValueChange = viewModel::onCustomInputChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://your-rsshub.example.com", color = radarColors().textTertiary, style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = radarColors().surface2,
                unfocusedContainerColor = radarColors().surface2,
                focusedBorderColor = radarColors().accent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = radarColors().textPrimary,
                unfocusedTextColor = radarColors().textPrimary,
                cursorColor = radarColors().accent,
            ),
        )
        Text(
            text = "自定义实例优先于探测结果；留空并保存则清除。",
            color = radarColors().textTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = viewModel::saveCustomHost) {
            Text("保存", color = radarColors().accent, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "内置镜像（点选填入；自动探测取响应最快者）",
            color = radarColors().textTertiary,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(6.dp))
        RssHubInstanceStore.BUILTIN_INSTANCES.forEachIndexed { index, host ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = radarColors().surface1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable { viewModel.onCustomInputChange(host) },
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}. $host",
                        color = radarColors().textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (host == state.activeHost) {
                        Text("当前", color = radarColors().accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 路由目录（issue #59）
        SectionHeader(
            "路由目录",
            "内置 RSSHub 全量路由，加订阅时可搜索与分类筛选。官方新增路由后联网更新目录即可同步。",
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "收录路由",
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (state.catalogRouteCount > 0) "${state.catalogRouteCount} 条" else "装载中…",
                        color = radarColors().textPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "数据时间",
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatCatalogTimestamp(state.catalogGeneratedAt) +
                            if (state.catalogSource == CatalogSource.UPDATED) "（已更新）" else "（内置）",
                        color = radarColors().textPrimary,
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
                        containerColor = radarColors().accent,
                        contentColor = radarColors().onAccent,
                    ),
                ) {
                    if (state.catalogRefreshing) {
                        CircularProgressIndicator(color = radarColors().onAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("更新中…", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text("更新路由目录", style = MaterialTheme.typography.labelLarge)
                    }
                }
                state.catalogMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = message, color = radarColors().textTertiary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// —— 4. AI 与诊断：DeepSeek Key / 全文抓取诊断 / 崩溃日志 ——

@Composable
fun SettingsAiDiagScreen(
    viewModel: RssHubSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenAiFeatures: () -> Unit = {},
    onOpenAiArtifacts: () -> Unit = {},
    onOpenFetchDiagnostics: () -> Unit = {},
    onOpenCrashLog: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    SettingsSubPage(title = "AI 与诊断", onBack = onBack) {
        // AI（DeepSeek，issue #44 / ADR-0005）
        SectionHeader(
            "AI（DeepSeek）",
            "详情页的 AI 摘要与翻译由 DeepSeek 提供，使用你自己的 API Key，费用与额度由你掌控。",
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "状态",
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (state.aiKeyConfigured) "已配置" else "未配置",
                        color = if (state.aiKeyConfigured) radarColors().accent else radarColors().textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.aiKeyInput,
                    onValueChange = viewModel::onAiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("sk-…", color = radarColors().textTertiary, style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = radarColors().surface2,
                        unfocusedContainerColor = radarColors().surface2,
                        focusedBorderColor = radarColors().accent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = radarColors().textPrimary,
                        unfocusedTextColor = radarColors().textPrimary,
                        cursorColor = radarColors().accent,
                    ),
                )
                state.aiMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = message, color = radarColors().textTertiary, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = viewModel::saveAiKey) {
                    Text("保存 Key", color = radarColors().accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // AI 智能功能（35 项）：开关矩阵、用量看板与任务队列
        SectionHeader(
            "AI 智能功能",
            "35 项 AI 功能各自独立开关：内容处理、推荐发现、辅助推送。用量与后台任务队列在这里看。",
        )
        NavigateRow("功能开关与用量", onClick = onOpenAiFeatures)
        NavigateRow("AI 生成结果", onClick = onOpenAiArtifacts)

        Spacer(Modifier.height(24.dp))

        // 正文抓取（ADR-0012）
        SectionHeader(
            "正文抓取",
            "按需抓原文失败或抓不全时，这里能看到站点、状态码与原因。",
        )
        NavigateRow("全文抓取诊断", onClick = onOpenFetchDiagnostics)

        Spacer(Modifier.height(24.dp))

        // 崩溃日志（issue #61）
        SectionHeader(
            "诊断",
            "应用崩溃时自动记录异常与设备信息，最多保留 5 份，可导出。",
        )
        NavigateRow("崩溃日志", onClick = onOpenCrashLog)
        Spacer(Modifier.height(24.dp))
    }
}
