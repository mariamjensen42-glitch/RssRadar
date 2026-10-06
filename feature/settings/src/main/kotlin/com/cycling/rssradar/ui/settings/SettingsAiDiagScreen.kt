package com.cycling.rssradar.ui.settings

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.Lucide
import com.cycling.rssradar.core.ui.components.NavigateRow
import com.cycling.rssradar.core.ui.components.SectionHeader
import com.cycling.rssradar.core.ui.components.SettingsSubPage
import com.cycling.rssradar.core.ui.text.resolve
import com.cycling.rssradar.core.ui.theme.RssRadarTheme
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.theme.radarOutlinedTextFieldColors

@Composable
fun SettingsAiDiagDestination(
    onBack: () -> Unit = {},
    onOpenAiFeatures: () -> Unit = {},
    onOpenAiArtifacts: () -> Unit = {},
    onOpenPromptTemplates: () -> Unit = {},
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
    onOpenFetchDiagnostics: () -> Unit = {},
    onOpenCrashLog: () -> Unit = {},
    onAiKeyChange: (String) -> Unit = {},
    onSaveAiKey: () -> Unit = {},
) {
    SettingsSubPage(title = stringResource(R.string.settings_ai), onBack = onBack) {
        // AI（DeepSeek，issue #44 / ADR-0005）
        SectionHeader(
            "AI（DeepSeek）",
            description = stringResource(R.string.ai_desc),
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
                        text = stringResource(if (state.aiKeyConfigured) R.string.status_configured else R.string.status_not_configured),
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
                    onValueChange = onAiKeyChange,
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
                    colors = radarOutlinedTextFieldColors(),
                )
                state.aiMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = message.resolve(), color = radarColors().textTertiary, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onSaveAiKey,
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
            description = stringResource(R.string.ai_features_desc),
        )
        // 三行同属一组：间距交给组的 verticalArrangement，而不是在行与行之间手写 Spacer ——
        // NavigateRow 自身不带外部间距（卡片间只有内部 padding），漏一处就贴成一坨。
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NavigateRow(stringResource(R.string.features_and_usage), onClick = onOpenAiFeatures)
            NavigateRow(stringResource(R.string.ai_results_title), onClick = onOpenAiArtifacts)
            NavigateRow(stringResource(R.string.prompt_title), onClick = onOpenPromptTemplates)
        }

        Spacer(Modifier.height(24.dp))

        // 正文抓取（ADR-0012）
        SectionHeader(
            stringResource(R.string.body_fetch),
            description = stringResource(R.string.body_fetch_desc),
        )
        NavigateRow(stringResource(R.string.diag_title), onClick = onOpenFetchDiagnostics)

        Spacer(Modifier.height(24.dp))

        // 崩溃日志（issue #61）
        SectionHeader(
            stringResource(R.string.diagnostics),
            description = stringResource(R.string.crash_desc2),
        )
        NavigateRow(stringResource(R.string.crash_title), onClick = onOpenCrashLog)
        Spacer(Modifier.height(24.dp))
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
            state = RssHubSettingsUiState(aiKeyConfigured = true),
        )
    }
}
