package com.cycling.rssradar.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.cycling.rssradar.core.model.AppLanguage
import com.cycling.rssradar.core.model.LinkOpenMode
import com.cycling.rssradar.core.model.LinkShareState
import com.cycling.rssradar.core.model.ListDescMode
import com.cycling.rssradar.core.model.ListDisplayState
import com.cycling.rssradar.core.model.ListViewMode
import com.cycling.rssradar.core.model.ShareContentFormat
import com.cycling.rssradar.core.model.ThemeMode
import com.cycling.rssradar.core.ui.R as UiR
import com.cycling.rssradar.core.ui.components.OptionPickerSheet
import com.cycling.rssradar.core.ui.components.OptionRow
import com.cycling.rssradar.core.ui.components.SectionHeader
import com.cycling.rssradar.core.ui.components.SegmentedChips
import com.cycling.rssradar.core.ui.components.SettingSwitchRow
import com.cycling.rssradar.core.ui.components.SettingsSubPage
import com.cycling.rssradar.core.ui.labels.labelRes
import com.cycling.rssradar.core.ui.theme.RssRadarTheme
import com.cycling.rssradar.core.ui.theme.supportsDynamicColor

@Composable
fun SettingsGeneralDestination(
    onBack: () -> Unit = {},
    onOpenInterestProfile: () -> Unit = {},
    viewModel: RssHubSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsGeneralScreen(
        state = state,
        onBack = onBack,
        onOpenInterestProfile = onOpenInterestProfile,
        onSetThemeMode = viewModel::setThemeMode,
        onSetAppLanguage = viewModel::setAppLanguage,
        onSetDynamicColor = viewModel::setDynamicColor,
        onSetCustomAccent = viewModel::setCustomAccent,
        onUpdateListDisplay = viewModel::updateListDisplay,
        onSetRecommendationEnabled = viewModel::setRecommendationEnabled,
        onUpdateLinkShare = viewModel::updateLinkShare,
    )
}

@Composable
fun SettingsGeneralScreen(
    state: RssHubSettingsUiState,
    onBack: () -> Unit = {},
    onOpenInterestProfile: () -> Unit = {},
    onSetThemeMode: (ThemeMode) -> Unit = {},
    onSetAppLanguage: (AppLanguage) -> Unit = {},
    onSetDynamicColor: (Boolean) -> Unit = {},
    onSetCustomAccent: (Long?) -> Unit = {},
    onUpdateListDisplay: ((ListDisplayState) -> ListDisplayState) -> Unit = {},
    onSetRecommendationEnabled: (Boolean) -> Unit = {},
    onUpdateLinkShare: ((LinkShareState) -> LinkShareState) -> Unit = {},
) {
    var showLinkModeSheet by remember { mutableStateOf(false) }
    var showShareFormatSheet by remember { mutableStateOf(false) }

    SettingsSubPage(title = stringResource(R.string.settings_general), onBack = onBack) {
        SectionHeader(stringResource(R.string.settings_appearance))
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.theme_label),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val themeLabels = mapOf(
                            ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                            ThemeMode.LIGHT to stringResource(R.string.theme_light),
                            ThemeMode.DARK to stringResource(R.string.theme_dark),
                        )
                        SegmentedChips(
                            options = ThemeMode.entries.toList(),
                            selected = state.themeMode,
                            label = { themeLabels.getValue(it) },
                            onSelect = onSetThemeMode,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                // 界面语言：选完即推 locale 并重建界面。
                // 选项名不用 stringResource —— 「中文」「English」在任何语言下都应原样显示。
                val context = LocalContext.current
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.language),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.language_subtitle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    // label 是普通 lambda 不在组合作用域，stringResource 必须在这里先取好
                    val languageLabels = mapOf(
                        AppLanguage.SYSTEM to stringResource(R.string.language_follow_system),
                        AppLanguage.CHINESE to stringResource(R.string.language_chinese),
                        AppLanguage.ENGLISH to stringResource(R.string.language_english),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SegmentedChips(
                            options = AppLanguage.entries.toList(),
                            selected = state.appLanguage,
                            label = { languageLabels.getValue(it) },
                            onSelect = { language ->
                                onSetAppLanguage(language)
                                if (com.cycling.rssradar.core.data.platform.AppLocales.apply(context, language)) {
                                    (context as? ComponentActivity)?.let { activity ->
                                        activity.recreate()
                                        // Android 12+ 的 relaunch 过渡会让旧/新窗口间隙透出桌面；
                                        // 屏蔽动画后间隙不可见（API 34+ 改用 overrideActivityTransition）。
                                        @Suppress("DEPRECATION")
                                        activity.overridePendingTransition(0, 0)
                                    }
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                // 动态取色（#27）：Android 12+ 取壁纸色，**整套表面与文字一并跟随**；
                // 低于 12 禁用并说明原因
                val dynamicSupported = supportsDynamicColor()
                SettingSwitchRow(
                    label = stringResource(R.string.dynamic_color),
                    checked = state.dynamicColor,
                    onChange = onSetDynamicColor,
                    enabled = dynamicSupported,
                    subtitle = if (dynamicSupported) {
                        stringResource(R.string.dynamic_color_desc)
                    } else {
                        stringResource(R.string.dynamic_color_unsupported)
                    },
                )
                Spacer(Modifier.height(10.dp))
                // 自定义强调色（#29）：与动态取色互斥，选了颜色即关掉跟随壁纸
                AccentPicker(
                    customAccent = state.customAccent,
                    onSelect = onSetCustomAccent,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // 列表显示（issue #56）
        SectionHeader(
            stringResource(R.string.list_display),
            description = stringResource(R.string.list_display_desc),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                val display = state.listDisplay
                // 视图模式（列表/卡片/杂志/网格）：与信息流顶栏同一份全局偏好
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.view_mode),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val viewModeLabels = ListViewMode.entries.associateWith { stringResource(it.labelRes()) }
                        SegmentedChips(
                            options = ListViewMode.entries.toList(),
                            selected = display.viewMode,
                            label = { viewModeLabels.getValue(it) },
                            onSelect = { mode -> onUpdateListDisplay { it.copy(viewMode = mode) } },
                        )
                    }
                }
                SettingSwitchRow(
                    label = stringResource(R.string.show_feed_icon),
                    checked = display.showFeedIcon,
                    onChange = { v -> onUpdateListDisplay { it.copy(showFeedIcon = v) } },
                )
                SettingSwitchRow(
                    label = stringResource(R.string.show_feed_name),
                    checked = display.showFeedName,
                    onChange = { v -> onUpdateListDisplay { it.copy(showFeedName = v) } },
                )
                SettingSwitchRow(
                    label = stringResource(R.string.show_date),
                    checked = display.showDate,
                    onChange = { v -> onUpdateListDisplay { it.copy(showDate = v) } },
                )
                SettingSwitchRow(
                    label = stringResource(R.string.show_thumbnail),
                    checked = display.showThumbnail,
                    onChange = { v -> onUpdateListDisplay { it.copy(showThumbnail = v) } },
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.desc_label),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val descModeLabels = ListDescMode.entries.associateWith { stringResource(it.labelRes()) }
                        SegmentedChips(
                            options = ListDescMode.entries.toList(),
                            selected = display.descMode,
                            label = { descModeLabels.getValue(it) },
                            onSelect = { mode -> onUpdateListDisplay { it.copy(descMode = mode) } },
                        )
                    }
                }
                SettingSwitchRow(
                    label = stringResource(R.string.sticky_date_header),
                    checked = display.stickyDateHeader,
                    onChange = { v -> onUpdateListDisplay { it.copy(stickyDateHeader = v) } },
                )
                SettingSwitchRow(
                    label = stringResource(R.string.dim_read),
                    checked = display.dimRead,
                    onChange = { v -> onUpdateListDisplay { it.copy(dimRead = v) } },
                )
                // 滚动自动标记已读（#11）：卡片滚出视口顶部即标为已读。默认关——
                // 会改变用户数据，必须显式选择。
                SettingSwitchRow(
                    label = stringResource(R.string.mark_read_on_scroll),
                    checked = display.markReadOnScroll,
                    onChange = { v -> onUpdateListDisplay { it.copy(markReadOnScroll = v) } },
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // 推荐流
        SectionHeader(
            stringResource(R.string.recommendation),
            description = stringResource(R.string.recommendation_desc),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                SettingSwitchRow(
                    label = stringResource(R.string.show_recommend_tab),
                    checked = state.recommendationEnabled,
                    onChange = onSetRecommendationEnabled,
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
                            text = stringResource(R.string.profile_title),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Lucide.ChevronRight,
                            contentDescription = stringResource(UiR.string.enter),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 链接与分享（#26）
        SectionHeader(
            stringResource(R.string.link_share),
            description = stringResource(R.string.link_share_desc),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                OptionRow(
                    label = stringResource(R.string.open_links),
                    value = stringResource(state.linkShare.linkOpenMode.labelRes()),
                    onClick = { showLinkModeSheet = true },
                )
                OptionRow(
                    label = stringResource(R.string.share_content),
                    value = stringResource(state.linkShare.shareFormat.labelRes()),
                    onClick = { showShareFormatSheet = true },
                )
                Text(
                    text = stringResource(R.string.custom_tabs_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // 检查更新（#35）：只查 latest release，不自动下载安装——装包必须过用户这一关，
        // 这里只负责把「有新版本」和去 Release 页的链接摆出来。
        SectionHeader(stringResource(R.string.about), description = stringResource(R.string.about_desc))
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                UpdateCheckRowDestination()
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showLinkModeSheet) {
        val linkModeLabels = LinkOpenMode.entries.associateWith { stringResource(it.labelRes()) }
        OptionPickerSheet(
            title = stringResource(R.string.open_links),
            options = LinkOpenMode.entries.toList(),
            selected = state.linkShare.linkOpenMode,
            label = { linkModeLabels.getValue(it) },
            onSelect = { mode -> onUpdateLinkShare { it.copy(linkOpenMode = mode) } },
            onDismiss = { showLinkModeSheet = false },
        )
    }

    if (showShareFormatSheet) {
        val shareFormatLabels = ShareContentFormat.entries.associateWith { stringResource(it.labelRes()) }
        OptionPickerSheet(
            title = stringResource(R.string.share_content),
            options = ShareContentFormat.entries.toList(),
            selected = state.linkShare.shareFormat,
            label = { shareFormatLabels.getValue(it) },
            onSelect = { format -> onUpdateLinkShare { it.copy(shareFormat = format) } },
            onDismiss = { showShareFormatSheet = false },
        )
    }
}

@Preview(showBackground = true, name = "通用设置 · 浅色")
@Composable
private fun SettingsGeneralScreenPreview() {
    RssRadarTheme(darkTheme = false) {
        SettingsGeneralScreen(state = RssHubSettingsUiState())
    }
}

@Preview(showBackground = true, name = "通用设置 · 深色")
@Composable
private fun SettingsGeneralScreenDarkPreview() {
    RssRadarTheme(darkTheme = true) {
        SettingsGeneralScreen(state = RssHubSettingsUiState())
    }
}
