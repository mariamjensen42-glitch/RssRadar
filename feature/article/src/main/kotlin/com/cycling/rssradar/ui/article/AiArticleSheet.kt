package com.cycling.rssradar.ui.article

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.cycling.rssradar.core.model.AiFeature
import com.cycling.rssradar.core.ui.theme.radarColors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 阅读页的 AI 面板：触发按钮 + 已生成产物的展示 + 文章问答。
 *
 * 两条设计取向：
 * 1. **产物卡片一律先说结论、再说依据**——用户滑到这里是想"值不值得读"，
 *    不是想欣赏模型的分析过程。质量分、情感档位放最上面，理由折叠在下。
 * 2. **没生成的功能显示为可点的按钮而不是空白**——
 *    一片空白会让用户以为功能坏了，一个"点击生成"反而说明白了这是按需付费的动作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiArticleSheet(
    artifacts: Map<Int, Any>,
    running: Set<Int>,
    message: String?,
    /** 已开启的功能。未开启的按钮渲染为灰色并给出开启引导，不做"点了没反应"的静默失败。 */
    enabled: Set<AiFeature>,
    /** 未配置 API Key 时顶部直接给指引，省掉一次必然失败的等待。 */
    keyConfigured: Boolean = true,
    onRun: (AiFeature) -> Unit,
    onAsk: (String) -> Unit,
    onExplain: (String) -> Unit = {},
    onConsumeMessage: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    var question by remember { mutableStateOf("") }

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = radarColors().bgRoot,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Lucide.Sparkles,
                    contentDescription = null,
                    tint = radarColors().accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.ai_analysis),
                    style = MaterialTheme.typography.titleMedium,
                    color = radarColors().textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.ai_on_demand_hint),
                style = MaterialTheme.typography.bodySmall,
                color = radarColors().textTertiary,
            )
            Spacer(Modifier.height(14.dp))

            if (!keyConfigured) {
                NoKeyBanner()
                Spacer(Modifier.height(12.dp))
            }

            // 功能按钮：已生成的高亮，未开启的灰显，正在生成的转圈
            FeatureButtonGrid(
                features = ARTICLE_AI_BUTTONS,
                artifacts = artifacts,
                running = running,
                enabled = enabled,
                onRun = {
                    onConsumeMessage()
                    onRun(it)
                },
            )

            Spacer(Modifier.height(16.dp))

            // 文章问答与划词解释：都是实时交互，每次都真正调用模型
            QuestionBar(
                value = question,
                onValueChange = { question = it },
                onAsk = {
                    if (question.isNotBlank()) {
                        onConsumeMessage()
                        onAsk(question)
                        question = ""
                    }
                },
                onExplain = {
                    if (question.isNotBlank()) {
                        onConsumeMessage()
                        onExplain(question)
                        question = ""
                    }
                },
                askRunning = AiFeature.QA.dbValue in running,
                explainRunning = AiFeature.GLOSSARY.dbValue in running,
                askEnabled = AiFeature.QA in enabled,
                explainEnabled = AiFeature.GLOSSARY in enabled,
            )

            // 提示必须显眼：这是"点了没反应"的唯一解释出口，藏在弱色小字里等于没有。
            message?.let { text ->
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = radarColors().surface2) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = radarColors().textPrimary,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            val results = ARTICLE_AI_FEATURES.mapNotNull { feature ->
                artifacts[feature.dbValue]?.let { feature to it }
            }
            if (results.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.ai_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = radarColors().textTertiary,
                    )
                }
            } else {
                LazyColumn(
                    // 必须 weight(1f)：Column 里不给权重的 LazyColumn 高度约束是"剩余空间"，
                    // 一旦前面内容偏高就会被压成 0，产物生成了也看不见。
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    items(results, key = { it.first.name }) { (feature, payload) ->
                        AiResultCard(feature = feature, payload = payload)
                    }
                }
            }
        }
    }
}

// ── 触发区 ──────────────────────────────────────────────────────────────────

@Composable
private fun FeatureButtonGrid(
    features: List<AiFeature>,
    artifacts: Map<Int, Any>,
    running: Set<Int>,
    enabled: Set<AiFeature>,
    onRun: (AiFeature) -> Unit,
) {
    val colors = radarColors()
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        features.forEach { feature ->
            val ready = feature.dbValue in artifacts
            val busy = feature.dbValue in running
            val on = feature in enabled
            // 三态视觉：已生成（accent 实底）/ 可用（surface2 正常字）/ 未开启（surface3 弱字 + 「未开启」）。
            // 未开启的仍然可点——点了会给出"去设置里打开"的指引，比让它变死按钮更好解释。
            val bg = when {
                ready -> colors.accent
                on -> colors.surface2
                else -> colors.surface3
            }
            val fg = when {
                ready -> colors.onAccent
                on -> colors.textSecondary
                else -> colors.textTertiary
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = bg,
                modifier = Modifier.clickable(enabled = !busy) { onRun(feature) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = when {
                            ready -> "${feature.label} ✓"
                            on -> feature.label
                            else -> stringResource(R.string.ai_feature_disabled, feature.label)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = fg,
                    )
                }
            }
        }
    }
}

/** 未配置 API Key 的醒目提示：放在面板最上面，避免用户逐个点按钮才发现全都跑不了。 */
@Composable
private fun NoKeyBanner() {
    val colors = radarColors()
    Surface(shape = RoundedCornerShape(12.dp), color = colors.surface2) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.ai_key_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.ai_key_needed_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable

/**
 * 一个输入框 + 两个动作（提问 / 解释术语）。
 *
 * 共用输入框而不是开两行：这两个动作的输入形态一样（一句话），
 * 分成两行会让面板多出一大块，而实际使用时一次只做一件事。
 */
private fun QuestionBar(
    value: String,
    onValueChange: (String) -> Unit,
    onAsk: () -> Unit,
    onExplain: () -> Unit,
    askRunning: Boolean,
    explainRunning: Boolean,
    askEnabled: Boolean,
    explainEnabled: Boolean,
) {
    val colors = radarColors()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            placeholder = {
                Text(stringResource(R.string.ai_ask_hint), color = colors.textTertiary, style = MaterialTheme.typography.bodyMedium)
            },
            maxLines = 3,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface2,
                unfocusedContainerColor = colors.surface2,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                cursorColor = colors.accent,
            ),
        )
        Spacer(Modifier.width(8.dp))
        TextButton(enabled = !askRunning, onClick = onAsk) {
            Text(
                text = if (askRunning) stringResource(R.string.ai_thinking) else stringResource(R.string.ai_ask),
                color = if (askEnabled) colors.accent else colors.textTertiary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.ai_term_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            TextButton(enabled = !explainRunning, onClick = onExplain) {
                Text(
                    text = if (explainRunning) stringResource(R.string.ai_verifying) else stringResource(R.string.ai_explain_term),
                    color = if (explainEnabled) colors.accent else colors.textTertiary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ── 产物卡片 ────────────────────────────────────────────────────────────────

@Composable
private fun AiResultCard(feature: AiFeature, payload: Any) {
    val colors = radarColors()
    Surface(shape = RoundedCornerShape(14.dp), color = colors.surface1) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = feature.label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.accent,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            // 渲染分发收敛到注册表：每项功能的渲染入口在 AI_RESULT_RENDERS 一行可查，
            // 新功能在映射里登记一行即可，不再往 when 里追加分支。
            AI_RESULT_RENDERS[feature]?.invoke(payload)
        }
    }
}
