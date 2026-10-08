package com.cycling.rssradar.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.HardDriveDownload
import com.composables.icons.lucide.Highlighter
import com.composables.icons.lucide.ListFilter
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Server
import com.cycling.rssradar.core.model.SyncInterval
import com.cycling.rssradar.core.model.SyncState
import com.cycling.rssradar.core.model.ThemeMode
import com.cycling.rssradar.core.ui.R as UiR
import com.cycling.rssradar.core.ui.components.tabBarBottomClearance
import com.cycling.rssradar.core.ui.theme.RssRadarTheme

@Composable
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
    ThemeMode.DARK -> stringResource(R.string.theme_dark)
}

@Composable
fun RssHubSettingsDestination(
    modifier: Modifier = Modifier,
    onOpenGeneral: () -> Unit = {},
    onOpenSync: () -> Unit = {},
    onOpenRssHub: () -> Unit = {},
    onOpenAiDiag: () -> Unit = {},
    onOpenReadingStats: () -> Unit = {},
    onOpenFilterRules: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenNotification: () -> Unit = {},
    onOpenAnnotations: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    viewModel: RssHubSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RssHubSettingsScreen(
        state = state,
        modifier = modifier,
        onOpenGeneral = onOpenGeneral,
        onOpenSync = onOpenSync,
        onOpenRssHub = onOpenRssHub,
        onOpenAiDiag = onOpenAiDiag,
        onOpenReadingStats = onOpenReadingStats,
        onOpenFilterRules = onOpenFilterRules,
        onOpenBackup = onOpenBackup,
        onOpenNotification = onOpenNotification,
        onOpenAnnotations = onOpenAnnotations,
        onOpenLibrary = onOpenLibrary,
    )
}

/**
 * 「我的」页主页：只放分组入口，具体设置收进四个二级页（SettingsSubPages.kt）。
 * 之前 12+ 个分组平铺一屏滚不到底，现按 iOS 设置的分组导航收敛。
 */
@Composable
fun RssHubSettingsScreen(
    state: RssHubSettingsUiState,
    modifier: Modifier = Modifier,
    onOpenGeneral: () -> Unit = {},
    onOpenSync: () -> Unit = {},
    onOpenRssHub: () -> Unit = {},
    onOpenAiDiag: () -> Unit = {},
    onOpenReadingStats: () -> Unit = {},
    onOpenFilterRules: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenNotification: () -> Unit = {},
    onOpenAnnotations: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.me_title),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    // 底部让位底部导航栏（含系统导航栏 inset）
                    bottom = tabBarBottomClearance(),
                ),
        ) {
            // 统计条：数字全部来自 DB 真实计数（AI 不参与、不估算）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard(label = stringResource(R.string.me_feeds), value = state.feedCount.toString(), modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.me_unread), value = state.unreadCount.toString(), modifier = Modifier.weight(1f))
            }

            // 阅读统计入口（#83）：与统计条相邻，数字页不动设置页布局
            SettingsEntryCard(
                icon = Lucide.Activity,
                title = stringResource(R.string.stats_title),
                summary = stringResource(R.string.last_7_days),
                onClick = onOpenReadingStats,
            )
            Spacer(Modifier.height(10.dp))

            SettingsEntryCard(
                icon = Lucide.Palette,
                title = stringResource(R.string.settings_general),
                summary = themeModeLabel(state.themeMode),
                onClick = onOpenGeneral,
            )
            Spacer(Modifier.height(10.dp))
            SettingsEntryCard(
                icon = Lucide.RefreshCw,
                title = stringResource(R.string.settings_sync),
                summary = stringResource(state.sync.interval.labelRes()),
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
                title = stringResource(R.string.settings_ai),
                // 摘要只放得下 ~10 个中文字（无宽度约束，超出会压扁标题而不是省略），
                // 所以这里只说"配好了 + 开了几项"，用量进页面看。
                summary = if (state.aiKeyConfigured) {
                    stringResource(R.string.ai_card_summary, state.aiEnabledCount)
                } else {
                    stringResource(R.string.ai_not_configured)
                },
                onClick = onOpenAiDiag,
            )
            Spacer(Modifier.height(10.dp))
            SettingsEntryCard(
                icon = Lucide.ListFilter,
                title = stringResource(R.string.rule_title),
                summary = stringResource(R.string.rule_entry_summary),
                onClick = onOpenFilterRules,
            )
            Spacer(Modifier.height(10.dp))
            SettingsEntryCard(
                icon = Lucide.HardDriveDownload,
                title = stringResource(R.string.backup_title),
                summary = stringResource(R.string.backup_entry_summary),
                onClick = onOpenBackup,
            )
            Spacer(Modifier.height(10.dp))
            SettingsEntryCard(
                icon = Lucide.Bell,
                title = stringResource(R.string.settings_notification),
                summary = stringResource(R.string.notify_entry_summary),
                onClick = onOpenNotification,
            )
            Spacer(Modifier.height(10.dp))
            SettingsEntryCard(
                icon = Lucide.Highlighter,
                title = stringResource(R.string.annotations_title),
                summary = stringResource(R.string.annotations_entry_summary),
                onClick = onOpenAnnotations,
            )
            Spacer(Modifier.height(10.dp))
            SettingsEntryCard(
                icon = Lucide.Bookmark,
                title = stringResource(R.string.library_title),
                summary = stringResource(R.string.library_entry_summary),
                onClick = onOpenLibrary,
            )
            Spacer(Modifier.height(32.dp))

            // 版本 footer：用户反馈 / 应用商店评价时第一件事就是问版本号
            val ctx = LocalContext.current
            val versionName = remember {
                runCatching {
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
                }.getOrNull() ?: "?"
            }
            Text(
                text = "RssRadar v$versionName",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 统计卡片：大数字 + 标签，纯展示。 */
@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** 主页分组入口卡：图标 + 名称 + 当前值摘要 + 箭头。 */
@Composable
internal fun SettingsEntryCard(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Lucide.ChevronRight,
                contentDescription = stringResource(UiR.string.enter),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "我的 · 浅色")
@Composable
private fun RssHubSettingsScreenPreview() {
    RssRadarTheme(darkTheme = false) {
        RssHubSettingsScreen(
            state = RssHubSettingsUiState(
                activeHost = "rsshub.app",
                feedCount = 618,
                unreadCount = 3090,
                sync = SyncState(interval = SyncInterval.EVERY_1_HOUR),
            ),
        )
    }
}

@Preview(showBackground = true, name = "我的 · 深色")
@Composable
private fun RssHubSettingsScreenDarkPreview() {
    RssRadarTheme(darkTheme = true) {
        RssHubSettingsScreen(
            state = RssHubSettingsUiState(
                activeHost = "rsshub.app",
                aiKeyConfigured = true,
                feedCount = 24,
                unreadCount = 128,
            ),
        )
    }
}
