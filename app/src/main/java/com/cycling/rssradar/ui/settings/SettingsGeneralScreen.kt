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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.cycling.rssradar.R
import com.cycling.rssradar.core.data.store.model.AppLanguage
import com.cycling.rssradar.core.data.store.model.LinkOpenMode
import com.cycling.rssradar.core.data.store.model.ListDescMode
import com.cycling.rssradar.core.data.store.model.ListViewMode
import com.cycling.rssradar.core.data.store.model.ShareContentFormat
import com.cycling.rssradar.core.data.store.model.ThemeMode
import com.cycling.rssradar.core.ui.components.OptionPickerSheet
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.theme.supportsDynamicColor
import com.cycling.rssradar.i18n.labelRes
import androidx.compose.runtime.setValue
import com.cycling.rssradar.ui.me.RssHubSettingsViewModel
import com.cycling.rssradar.ui.me.UpdateCheckRow

@Composable
fun SettingsGeneralScreen(
    viewModel: RssHubSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenInterestProfile: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var showLinkModeSheet by remember { mutableStateOf(false) }
    var showShareFormatSheet by remember { mutableStateOf(false) }

    SettingsSubPage(title = stringResource(R.string.settings_general), onBack = onBack) {
        SectionHeader(stringResource(R.string.settings_appearance))
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.theme_label),
                        color = radarColors().textPrimary,
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
                            onSelect = { viewModel.setThemeMode(it) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                // 界面语言（ADR-0017）：选完即推 locale 并重建界面。
                // 选项名不用 stringResource —— 「中文」「English」在任何语言下都应原样显示。
                val context = LocalContext.current
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.language),
                            color = radarColors().textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.language_subtitle),
                            color = radarColors().textTertiary,
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
                                viewModel.setAppLanguage(language)
                                if (com.cycling.rssradar.i18n.AppLocales.apply(context, language)) {
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
                    onChange = { viewModel.setDynamicColor(it) },
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
                    onSelect = { viewModel.setCustomAccent(it) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // 列表显示（issue #56）
        SectionHeader(
            stringResource(R.string.list_display),
            stringResource(R.string.list_display_desc),
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
                        text = stringResource(R.string.view_mode),
                        color = radarColors().textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val viewModeLabels = ListViewMode.entries.associateWith { stringResource(it.labelRes()) }
                        SegmentedChips(
                            options = ListViewMode.entries.toList(),
                            selected = display.viewMode,
                            label = { viewModeLabels.getValue(it) },
                            onSelect = { mode -> viewModel.updateListDisplay { it.copy(viewMode = mode) } },
                        )
                    }
                }
                SettingSwitchRow(
                    label = stringResource(R.string.show_feed_icon),
                    checked = display.showFeedIcon,
                    onChange = { v -> viewModel.updateListDisplay { it.copy(showFeedIcon = v) } },
                )
                SettingSwitchRow(
                    label = stringResource(R.string.show_feed_name),
                    checked = display.showFeedName,
                    onChange = { v -> viewModel.updateListDisplay { it.copy(showFeedName = v) } },
                )
                SettingSwitchRow(
                    label = stringResource(R.string.show_date),
                    checked = display.showDate,
                    onChange = { v -> viewModel.updateListDisplay { it.copy(showDate = v) } },
                )
                SettingSwitchRow(
                    label = stringResource(R.string.show_thumbnail),
                    checked = display.showThumbnail,
                    onChange = { v -> viewModel.updateListDisplay { it.copy(showThumbnail = v) } },
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.desc_label),
                        color = radarColors().textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val descModeLabels = ListDescMode.entries.associateWith { stringResource(it.labelRes()) }
                        SegmentedChips(
                            options = ListDescMode.entries.toList(),
                            selected = display.descMode,
                            label = { descModeLabels.getValue(it) },
                            onSelect = { mode -> viewModel.updateListDisplay { it.copy(descMode = mode) } },
                        )
                    }
                }
                SettingSwitchRow(
                    label = stringResource(R.string.sticky_date_header),
                    checked = display.stickyDateHeader,
                    onChange = { v -> viewModel.updateListDisplay { it.copy(stickyDateHeader = v) } },
                )
                SettingSwitchRow(
                    label = stringResource(R.string.dim_read),
                    checked = display.dimRead,
                    onChange = { v -> viewModel.updateListDisplay { it.copy(dimRead = v) } },
                )
                // 滚动自动标记已读（#11）：卡片滚出视口顶部即标为已读。默认关——
                // 会改变用户数据，必须显式选择。
                SettingSwitchRow(
                    label = stringResource(R.string.mark_read_on_scroll),
                    checked = display.markReadOnScroll,
                    onChange = { v -> viewModel.updateListDisplay { it.copy(markReadOnScroll = v) } },
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // 推荐流（ADR-0013）
        SectionHeader(
            stringResource(R.string.recommendation),
            stringResource(R.string.recommendation_desc),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                SettingSwitchRow(
                    label = stringResource(R.string.show_recommend_tab),
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
                            text = stringResource(R.string.profile_title),
                            color = radarColors().textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Lucide.ChevronRight,
                            contentDescription = stringResource(R.string.enter),
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
            stringResource(R.string.link_share),
            stringResource(R.string.link_share_desc),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
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
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // 检查更新（#35）：只查 latest release，不自动下载安装——装包必须过用户这一关，
        // 这里只负责把「有新版本」和去 Release 页的链接摆出来。
        SectionHeader(stringResource(R.string.about), stringResource(R.string.about_desc))
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                UpdateCheckRow()
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
            onSelect = { mode -> viewModel.updateLinkShare { it.copy(linkOpenMode = mode) } },
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
            onSelect = { format -> viewModel.updateLinkShare { it.copy(shareFormat = format) } },
            onDismiss = { showShareFormatSheet = false },
        )
    }
}

// —— 2. 同步与清理：自动同步 / 文章清理 / 新文章通知 ——
