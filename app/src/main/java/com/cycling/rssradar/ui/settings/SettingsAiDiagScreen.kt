package com.cycling.rssradar.ui.settings

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.Lucide
import com.cycling.rssradar.R
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.text.resolve
import com.cycling.rssradar.core.ui.components.NavigateRow
import com.cycling.rssradar.core.ui.components.SectionHeader
import com.cycling.rssradar.core.ui.components.SettingsSubPage
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

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
