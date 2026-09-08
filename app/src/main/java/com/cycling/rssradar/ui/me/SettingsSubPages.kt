package com.cycling.rssradar.ui.me

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.cycling.rssradar.R
import com.cycling.rssradar.i18n.labelRes
import com.cycling.rssradar.i18n.resolve
import com.cycling.rssradar.core.model.rsshub.CatalogSource
import com.cycling.rssradar.core.data.rsshub.RssHubInstanceStore
import com.cycling.rssradar.core.data.store.KeepArchived
import com.cycling.rssradar.core.data.store.AppLanguage
import com.cycling.rssradar.core.data.store.LinkOpenMode
import com.cycling.rssradar.core.data.store.ListViewMode
import com.cycling.rssradar.core.data.store.ShareContentFormat
import com.cycling.rssradar.core.data.store.SyncInterval
import com.cycling.rssradar.core.data.store.ThemeMode
import com.cycling.rssradar.core.ui.components.OptionPickerSheet
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.Lucide
import com.cycling.rssradar.core.ui.theme.ACCENT_PRESETS
import com.cycling.rssradar.core.ui.theme.DEFAULT_ACCENT_ARGB
import com.cycling.rssradar.core.ui.theme.argbToHsl
import com.cycling.rssradar.core.ui.theme.onAccentFor
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.theme.supportsDynamicColor
import com.cycling.rssradar.core.ui.theme.toArgb

/**
 * 设置二级页（原「我的」长页拆分）：通用 / 同步与清理 / RSSHub / AI 与诊断。
 * 主页只留分组入口（RssHubSettingsScreen），具体项按使用场景归进对应页。
 * 各页持有独立的 [RssHubSettingsViewModel] 实例，状态一律从 Store 读真值。
 */

// —— 公共骨架与小组件 ——

/** 二级页骨架：返回顶栏 + 滚动内容，与 InterestProfileScreen 同款形态。 */
@Composable
internal fun SettingsSubPage(
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
                Icon(Lucide.ArrowLeft, contentDescription = stringResource(R.string.back), tint = radarColors().textPrimary)
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
internal fun SectionHeader(title: String, description: String? = null) {
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

/**
 * 开关行。[subtitle] 为说明文案；[enabled] 为 false 时整行置灰——
 * UI 铁律：禁用必须配解释文案，所以两者成对出现，别只传 enabled。
 */
@Composable
internal fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (enabled) radarColors().textPrimary else radarColors().textTertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = radarColors().onAccent,
                checkedTrackColor = radarColors().accent,
            ),
        )
    }
}

/**
 * 强调色选择（#29）：预设色板 + HSL 三滑杆。
 *
 * 只换强调色，卡片与背景不动——整套换色等于把产品视觉身份交出去。
 */
@Composable
private fun AccentPicker(
    customAccent: Long?,
    onSelect: (Long?) -> Unit,
) {
    Text(
        text = stringResource(R.string.accent_color),
        color = radarColors().textPrimary,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
    Text(
        text = stringResource(R.string.accent_desc),
        color = radarColors().textTertiary,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
    )
    // 第一枚是默认紫：选中它 = 回到默认，不是「自定义了一个紫」
    ACCENT_PRESETS.chunked(6).forEach { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 10.dp),
        ) {
            row.forEach { argb ->
                val isDefault = argb == DEFAULT_ACCENT_ARGB
                val selected = if (isDefault) customAccent == null else customAccent == argb
                AccentSwatch(
                    argb = argb,
                    selected = selected,
                    onClick = { onSelect(if (isDefault) null else argb) },
                )
            }
        }
    }
    AccentSliders(base = customAccent ?: DEFAULT_ACCENT_ARGB, onChange = { onSelect(it) })
}

@Composable
private fun AccentSwatch(argb: Long, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(argb))
            .then(
                if (selected) {
                    Modifier.border(2.dp, radarColors().textPrimary, RoundedCornerShape(50))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
    )
}

/** HSL 三滑杆 + 实时预览：拖「色相」不会顺手把颜色拖灰，比 RGB 直观。 */
@Composable
private fun AccentSliders(base: Long, onChange: (Long) -> Unit) {
    var hsl by remember(base) { mutableStateOf(argbToHsl(base)) }
    val argb = hsl.toArgb()
    Row(verticalAlignment = Alignment.CenterVertically) {
        AccentSlider(stringResource(R.string.hue), hsl.hue, 0f..360f) { hsl = hsl.copy(hue = it); onChange(hsl.toArgb()) }
        Spacer(Modifier.width(10.dp))
        Surface(shape = RoundedCornerShape(50), color = Color(argb)) {
            Text(
                text = stringResource(R.string.preview_aa),
                color = onAccentFor(Color(argb)),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
    AccentSlider(stringResource(R.string.saturation), hsl.saturation, 0f..1f) { hsl = hsl.copy(saturation = it); onChange(hsl.toArgb()) }
    AccentSlider(stringResource(R.string.lightness), hsl.lightness, 0f..1f) { hsl = hsl.copy(lightness = it); onChange(hsl.toArgb()) }
}

@Composable
private fun AccentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = radarColors().textTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(48.dp),
        )
        Slider(value = value, onValueChange = onValue, valueRange = range, modifier = Modifier.weight(1f))
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
            contentDescription = stringResource(R.string.select),
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
                contentDescription = stringResource(R.string.enter),
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

/** 通用分段选择器：胶囊 chip 一排，选中态 accent 填充。设置页三处共用，保证样式一致。 */
@Composable
private fun <T> SegmentedChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) radarColors().accent else radarColors().surface2,
                modifier = Modifier.clickable { onSelect(option) },
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) radarColors().onAccent else radarColors().textPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

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
                                    (context as? ComponentActivity)?.recreate()
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                // 动态取色（#27）：只换强调色；Android 12 以下禁用并说明原因
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
                        val descModeLabels = com.cycling.rssradar.core.data.store.ListDescMode.entries.associateWith { stringResource(it.labelRes()) }
                        SegmentedChips(
                            options = com.cycling.rssradar.core.data.store.ListDescMode.entries.toList(),
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

    SettingsSubPage(title = stringResource(R.string.settings_sync), onBack = onBack) {
        // 自动同步（issue #58）
        SectionHeader(
            stringResource(R.string.auto_sync),
            stringResource(R.string.auto_sync_desc),
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
                        text = stringResource(R.string.sync_interval),
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
                    label = stringResource(R.string.wifi_only),
                    checked = state.sync.onlyOnWifi,
                    onChange = { v -> viewModel.updateSync { it.copy(onlyOnWifi = v) } },
                )
                SettingSwitchRow(
                    label = stringResource(R.string.charging_only),
                    checked = state.sync.onlyWhenCharging,
                    onChange = { v -> viewModel.updateSync { it.copy(onlyWhenCharging = v) } },
                )
                SettingSwitchRow(
                    label = stringResource(R.string.sync_on_launch),
                    checked = state.sync.syncOnStart,
                    onChange = { v -> viewModel.updateSync { it.copy(syncOnStart = v) } },
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // 文章清理（issue #57）
        SectionHeader(
            stringResource(R.string.article_cleanup),
            stringResource(R.string.cleanup_desc),
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
                    text = stringResource(R.string.keep_over),
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
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Lucide.ChevronRight,
                    contentDescription = stringResource(R.string.select),
                    tint = radarColors().textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // 新文章通知（#31）
        SectionHeader(
            stringResource(R.string.new_article_notify),
            stringResource(R.string.notify_desc),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                SettingSwitchRow(
                    label = stringResource(R.string.enable_notify),
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
                        text = message.resolve(),
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
        val keepLabels = KeepArchived.entries.associateWith { stringResource(it.labelRes()) }
        OptionPickerSheet(
            title = stringResource(R.string.keep_over),
            options = KeepArchived.entries.toList(),
            selected = state.keepArchived,
            label = { keepLabels.getValue(it) },
            onSelect = viewModel::setKeepArchived,
            onDismiss = { showKeepSheet = false },
        )
    }

    if (showIntervalSheet) {
        val intervalLabels = SyncInterval.entries.associateWith { stringResource(it.labelRes()) }
        OptionPickerSheet(
            title = stringResource(R.string.sync_interval),
            options = SyncInterval.entries.toList(),
            selected = state.sync.interval,
            label = { intervalLabels.getValue(it) },
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
            stringResource(R.string.rsshub_instance),
            stringResource(R.string.instance_desc),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.current_instance),
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
                        Text(stringResource(R.string.probing), style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text(stringResource(R.string.auto_probe), style = MaterialTheme.typography.labelLarge)
                    }
                }
                state.probeMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = message.resolve(), color = radarColors().textTertiary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.custom_instance),
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
            text = stringResource(R.string.custom_instance_hint),
            color = radarColors().textTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
                Spacer(Modifier.height(10.dp))
                // 主操作用实心按钮（UI 审计 G3）：文本链接样式地位不符、点击面积小
                Button(
                    onClick = viewModel::saveCustomHost,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = radarColors().accent,
                        contentColor = radarColors().onAccent,
                    ),
                ) {
                    Text(stringResource(R.string.save), style = MaterialTheme.typography.labelLarge)
                }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.builtin_mirrors),
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
                        Text(stringResource(R.string.current_tag), color = radarColors().accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 路由目录（issue #59）
        SectionHeader(
            stringResource(R.string.route_catalog),
            stringResource(R.string.catalog_desc),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.catalog_count_label),
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (state.catalogRouteCount > 0) "${state.catalogRouteCount} 条" else stringResource(R.string.loading_ellipsis),
                        color = radarColors().textPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.data_time),
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatCatalogTimestamp(state.catalogGeneratedAt) +
                            if (state.catalogSource == CatalogSource.UPDATED) stringResource(R.string.updated_suffix) else stringResource(R.string.builtin_suffix),
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
                        Text(stringResource(R.string.updating), style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text(stringResource(R.string.update_catalog), style = MaterialTheme.typography.labelLarge)
                    }
                }
                state.catalogMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = message.resolve(), color = radarColors().textTertiary, style = MaterialTheme.typography.bodySmall)
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
    onOpenPromptTemplates: () -> Unit = {},
    onOpenFetchDiagnostics: () -> Unit = {},
    onOpenCrashLog: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    SettingsSubPage(title = stringResource(R.string.settings_ai), onBack = onBack) {
        // AI（DeepSeek，issue #44 / ADR-0005）
        SectionHeader(
            "AI（DeepSeek）",
            stringResource(R.string.ai_desc),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.status_label),
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
                // Key 默认打码：这页一截图就泄密（真机截图实测）。
                // 按住眼睛才临时可见，松手立即回打码——泄密窗口只有按住期间（UI 审计 G1）。
                var showAiKey by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = state.aiKeyInput,
                    onValueChange = viewModel::onAiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("sk-…", color = radarColors().textTertiary, style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    visualTransformation = if (showAiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        // 按住显示、松手即隐藏（onClick 空操作，手势全在 pointerInput）
                        IconButton(onClick = {}, modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    showAiKey = true
                                    try {
                                        awaitRelease()
                                    } finally {
                                        showAiKey = false
                                    }
                                },
                            )
                        }) {
                            Icon(
                                imageVector = Lucide.Eye,
                                contentDescription = stringResource(R.string.key_reveal_hint),
                                tint = radarColors().textTertiary,
                            )
                        }
                    },
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
                    Text(text = message.resolve(), color = radarColors().textTertiary, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = viewModel::saveAiKey,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = radarColors().accent,
                        contentColor = radarColors().onAccent,
                    ),
                ) {
                    Text(stringResource(R.string.save_key), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // AI 智能功能（35 项）：开关矩阵、用量看板与任务队列
        SectionHeader(
            stringResource(R.string.ai_features_title),
            stringResource(R.string.ai_features_desc),
        )
        NavigateRow(stringResource(R.string.features_and_usage), onClick = onOpenAiFeatures)
        NavigateRow(stringResource(R.string.ai_results_title), onClick = onOpenAiArtifacts)
        NavigateRow(stringResource(R.string.prompt_title), onClick = onOpenPromptTemplates)

        Spacer(Modifier.height(24.dp))

        // 正文抓取（ADR-0012）
        SectionHeader(
            stringResource(R.string.body_fetch),
            stringResource(R.string.body_fetch_desc),
        )
        NavigateRow(stringResource(R.string.diag_title), onClick = onOpenFetchDiagnostics)

        Spacer(Modifier.height(24.dp))

        // 崩溃日志（issue #61）
        SectionHeader(
            stringResource(R.string.diagnostics),
            stringResource(R.string.crash_desc2),
        )
        NavigateRow(stringResource(R.string.crash_title), onClick = onOpenCrashLog)
        Spacer(Modifier.height(24.dp))
    }
}
