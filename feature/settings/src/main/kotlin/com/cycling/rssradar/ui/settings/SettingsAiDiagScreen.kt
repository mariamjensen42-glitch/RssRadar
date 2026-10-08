package com.cycling.rssradar.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.Lucide
import com.cycling.rssradar.core.model.AiFeature
import com.cycling.rssradar.core.ui.R as UiR
import com.cycling.rssradar.core.ui.components.SectionHeader
import com.cycling.rssradar.core.ui.components.SettingsSubPage
import com.cycling.rssradar.core.ui.text.resolve
import com.cycling.rssradar.core.ui.theme.RssRadarTheme
import com.cycling.rssradar.core.ui.theme.radarOutlinedTextFieldColors

/**
 * 「AI 与诊断」：AI 的总入口，也是应用的诊断入口。
 *
 * 三组各司其职，顺序即使用顺序：
 * 1. **服务**——先把 Key 配上，否则下面所有入口都是死路（顶部状态卡会直接说"未配置"）。
 * 2. **智能功能**——开关、结果、画像、提示词四个出口。它们原先分散在两处
 *    （兴趣画像在「通用 → 推荐流」下），从 AI 的角度看"我的 AI 都产出了什么"没法一次问完。
 * 3. **诊断**——抓取与崩溃。它们与 AI 无关，但同属"出了事该去哪儿看"，
 *    因此留在同一页并**单独成组**，靠分组标题把两件事分开，而不是混成一张列表。
 *
 * 顶部状态卡是这一版新增的：入口页原先是三行裸链接，用户看不出
 * "我开了几项、今天花了多少额度、后台有没有积压"——而这三件事恰好决定要不要进去。
 */
@Composable
fun SettingsAiDiagDestination(
    onBack: () -> Unit = {},
    onOpenAiFeatures: () -> Unit = {},
    onOpenAiArtifacts: () -> Unit = {},
    onOpenPromptTemplates: () -> Unit = {},
    onOpenInterestProfile: () -> Unit = {},
    onOpenFetchDiagnostics: () -> Unit = {},
    onOpenCrashLog: () -> Unit = {},
    viewModel: RssHubSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsAiDiagScreen(
        state = state,
        onBack = onBack,
        onOpenAiFeatures = onOpenAiFeatures,
        onOpenAiArtifacts = onOpenAiArtifacts,
        onOpenPromptTemplates = onOpenPromptTemplates,
        onOpenInterestProfile = onOpenInterestProfile,
        onOpenFetchDiagnostics = onOpenFetchDiagnostics,
        onOpenCrashLog = onOpenCrashLog,
        onAiKeyChange = viewModel::onAiKeyChange,
        onSaveAiKey = viewModel::saveAiKey,
    )
}

@Composable
fun SettingsAiDiagScreen(
    state: RssHubSettingsUiState,
    onBack: () -> Unit = {},
    onOpenAiFeatures: () -> Unit = {},
    onOpenAiArtifacts: () -> Unit = {},
    onOpenPromptTemplates: () -> Unit = {},
    onOpenInterestProfile: () -> Unit = {},
    onOpenFetchDiagnostics: () -> Unit = {},
    onOpenCrashLog: () -> Unit = {},
    onAiKeyChange: (String) -> Unit = {},
    onSaveAiKey: () -> Unit = {},
) {
    SettingsSubPage(title = stringResource(R.string.settings_ai), onBack = onBack) {
        AiStatusCard(state = state, onClick = onOpenAiFeatures)

        Spacer(Modifier.height(24.dp))

        SectionHeader(
            stringResource(R.string.ai_group_service),
            description = stringResource(R.string.ai_desc),
        )
        ApiKeyCard(
            state = state,
            onAiKeyChange = onAiKeyChange,
            onSaveAiKey = onSaveAiKey,
        )

        Spacer(Modifier.height(24.dp))

        SectionHeader(
            stringResource(R.string.ai_group_features),
            description = stringResource(R.string.ai_features_desc_short),
        )
        HubGroup(
            listOf(
                HubEntry(
                    label = stringResource(R.string.features_and_usage),
                    subtitle = stringResource(R.string.ai_entry_features_sub),
                    onClick = onOpenAiFeatures,
                ),
                HubEntry(
                    label = stringResource(R.string.ai_results_title),
                    subtitle = stringResource(R.string.ai_entry_results_sub),
                    onClick = onOpenAiArtifacts,
                ),
                HubEntry(
                    label = stringResource(R.string.profile_title),
                    subtitle = stringResource(R.string.ai_entry_profile_sub),
                    onClick = onOpenInterestProfile,
                ),
                HubEntry(
                    label = stringResource(R.string.prompt_title),
                    subtitle = stringResource(R.string.ai_entry_prompts_sub),
                    onClick = onOpenPromptTemplates,
                ),
            ),
        )

        Spacer(Modifier.height(24.dp))

        SectionHeader(
            stringResource(R.string.ai_group_diagnostics),
            description = stringResource(R.string.ai_diag_desc_short),
        )
        HubGroup(
            listOf(
                HubEntry(
                    label = stringResource(R.string.diag_title),
                    subtitle = stringResource(R.string.ai_entry_diag_sub),
                    onClick = onOpenFetchDiagnostics,
                ),
                HubEntry(
                    label = stringResource(R.string.crash_title),
                    subtitle = stringResource(R.string.ai_entry_crash_sub),
                    onClick = onOpenCrashLog,
                ),
            ),
        )

        Spacer(Modifier.height(24.dp))
    }
}

// ── 状态概览 ────────────────────────────────────────────────────────────────

/** 入口页顶部的状态卡：Key 状态 + 三个真实计数，整卡可点进功能页。 */
@Composable
private fun AiStatusCard(state: RssHubSettingsUiState, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val enabledValue = stringResource(R.string.ai_status_ratio, state.aiEnabledCount, AiFeature.entries.size)
    // 不限额度时不给分母——"0/0"或"42/不限"都不如直接给次数清楚。
    val todayValue = if (state.aiDailyLimit > 0) {
        stringResource(R.string.ai_status_ratio, state.aiUsedToday, state.aiDailyLimit)
    } else {
        state.aiUsedToday.toString()
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Lucide.Bot,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.ai_status_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(if (state.aiKeyConfigured) R.string.ai_configured else R.string.ai_not_configured),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.aiKeyConfigured) colors.primary else colors.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Lucide.ChevronRight,
                    contentDescription = stringResource(UiR.string.enter),
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusCell(stringResource(R.string.ai_status_enabled), enabledValue, Modifier.weight(1f))
                StatusCell(stringResource(R.string.ai_status_today), todayValue, Modifier.weight(1f))
                StatusCell(stringResource(R.string.ai_status_pending), state.aiPendingTasks.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.ai_status_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** 一个计数格：值在上、标签在下。纯展示，卡片整体才是点击目标。 */
@Composable
private fun StatusCell(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

// ── 服务配置 ────────────────────────────────────────────────────────────────

/** DeepSeek API Key（issue #44）。 */
@Composable
private fun ApiKeyCard(
    state: RssHubSettingsUiState,
    onAiKeyChange: (String) -> Unit,
    onSaveAiKey: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerLowest) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.status_label),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(if (state.aiKeyConfigured) R.string.status_configured else R.string.status_not_configured),
                    color = if (state.aiKeyConfigured) colors.primary else colors.onSurfaceVariant,
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
                onValueChange = onAiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-…", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) },
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
                            tint = colors.onSurfaceVariant,
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = radarOutlinedTextFieldColors(),
            )
            state.aiMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(text = message.resolve(), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onSaveAiKey,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Text(stringResource(R.string.save_key), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ── 分组入口 ────────────────────────────────────────────────────────────────

/** 一条入口：标题 + 一句它到底能做什么（原先只有标题，用户得点进去才知道）。 */
private data class HubEntry(
    val label: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

/** 一组入口装在同一张卡里，行间用分隔线切——卡片的边界就是分组的边界。 */
@Composable
private fun HubGroup(entries: List<HubEntry>) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerLowest) {
        Column {
            entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(color = colors.surfaceContainer, thickness = 1.dp)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = entry.onClick)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = entry.label,
                            color = colors.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = entry.subtitle,
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Lucide.ChevronRight,
                        contentDescription = stringResource(UiR.string.enter),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "AI 与诊断 · 未配置 Key")
@Composable
private fun SettingsAiDiagScreenPreview() {
    RssRadarTheme(darkTheme = false) {
        SettingsAiDiagScreen(state = RssHubSettingsUiState())
    }
}

@Preview(showBackground = true, name = "AI 与诊断 · 已配置 Key")
@Composable
private fun SettingsAiDiagScreenConfiguredPreview() {
    RssRadarTheme(darkTheme = true) {
        SettingsAiDiagScreen(
            state = RssHubSettingsUiState(
                aiKeyConfigured = true,
                aiEnabledCount = 13,
                aiUsedToday = 42,
                aiDailyLimit = 200,
                aiPendingTasks = 3,
            ),
        )
    }
}
