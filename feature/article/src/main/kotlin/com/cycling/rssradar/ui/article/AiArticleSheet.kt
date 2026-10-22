package com.cycling.rssradar.ui.article

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.cycling.rssradar.core.data.platform.shareText
import com.cycling.rssradar.core.model.AiFeature
import com.cycling.rssradar.core.ui.theme.radarOutlinedTextFieldColors

/**
 * 阅读页的 AI 面板：按用途分组的功能按钮 + 已生成产物的展示 + 文章问答。
 *
 * 四条设计取向：
 * 1. **按钮按「读懂 / 评估 / 整理 / 分享」分组**。十个按钮平铺时，用户得逐个读完
 *    才能找到自己要的那个；加一层组标题后，"我想看看这篇值不值得读"直连「评估」组。
 * 2. **产物卡片一律先说结论、再说依据**——用户滑到这里是想"值不值得读"，
 *    不是想欣赏模型的分析过程。质量分、情感档位放最上面，理由折叠在下。
 * 3. **没生成的功能显示为可点的按钮而不是空白**——一片空白会让用户以为功能坏了，
 *    一个"点击生成"反而说明白了这是按需付费的动作。
 * 4. **整块内容一条滚动流**。原先按钮区 + 输入区固定在上面、结果区吃剩下的高度，
 *    十个按钮换行加上输入框之后，360dp 屏上结果区会被压到几乎看不见；
 *    改成整页滚动后，结果永远滚得到，且按钮滚走之后正好把屏幕让给结果。
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
    /** 把结果卡上的词送去搜索页（话题 / 标签 / 关键词）。 */
    onSearch: (String) -> Unit = {},
    /** 大纲跳段：第 index 节（0 基，共 count 节）。 */
    onJumpToSection: (Int, Int) -> Unit = { _, _ -> },
    onConsumeMessage: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    var question by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val resultActions = AiResultActions(
        onSearch = onSearch,
        onCopy = { text -> clipboard.setText(AnnotatedString(text)) },
        onShare = { text -> context.shareText(text, context.getString(R.string.ai_share)) },
        // 跳到正文就顺手收掉面板：用户点它的意图是回去读那一段。
        onJumpToSection = { index, count ->
            onJumpToSection(index, count)
            onDismiss()
        },
    )

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    // 组标题是普通 String（要传给非组合作用域的结果排序与 key），先在组合作用域取好。
    val groupTitles = AI_BUTTON_GROUPS.associate { it to stringResource(it.titleRes) }
    // 固定按分组顺序排结果：按钮在哪个位置，结果就在哪个位置，不用回头找。
    val results = ARTICLE_AI_FEATURES.mapNotNull { feature ->
        artifacts[feature.dbValue]?.let { feature to it }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
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
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.ai_analysis),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.ai_on_demand_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            if (!keyConfigured) {
                NoKeyBanner()
                Spacer(Modifier.height(12.dp))
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item(key = "legend") { StateLegend() }

                AI_BUTTON_GROUPS.forEach { group ->
                    item(key = "g-${group.titleRes}") {
                        ButtonGroup(
                            title = groupTitles.getValue(group),
                            features = group.features,
                            artifacts = artifacts,
                            running = running,
                            enabled = enabled,
                            onRun = {
                                onConsumeMessage()
                                onRun(it)
                            },
                        )
                    }
                }

                item(key = "ask") {
                    AskBlock(
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
                }

                // 提示必须显眼：这是"点了没反应"的唯一解释出口，藏在弱色小字里等于没有。
                message?.let { text ->
                    item(key = "message") { MessageBanner(text) }
                }

                item(key = "results-header") {
                    Text(
                        text = stringResource(R.string.ai_results_header, results.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (results.isEmpty()) {
                    item(key = "results-empty") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.ai_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(results, key = { it.first.name }) { (feature, payload) ->
                        AiResultCard(feature = feature, payload = payload, actions = resultActions)
                    }
                }
            }
        }
    }
}

// ── 触发区 ──────────────────────────────────────────────────────────────────

/**
 * 面板按钮的分组表（顺序 = [ARTICLE_AI_BUTTONS] 的顺序 = 产物卡片的顺序）。
 *
 * 这张表与 [ARTICLE_AI_BUTTONS] 必须是同一批功能，漏一个按钮或漏一个分组都会静默少一个入口。
 */
internal val AI_BUTTON_GROUPS: List<AiButtonGroup> = listOf(
    AiButtonGroup(R.string.ai_group_read, listOf(AiFeature.OUTLINE, AiFeature.NOISE, AiFeature.KEYWORDS)),
    AiButtonGroup(R.string.ai_group_share, listOf(AiFeature.SHARE_COPY)),
)

internal data class AiButtonGroup(@StringRes val titleRes: Int, val features: List<AiFeature>)

/** 三态按钮的含义说明。灰底按钮看起来像"禁用"，不解释一句就会被当成死按钮。 */
@Composable
private fun StateLegend() {
    Text(
        text = stringResource(R.string.ai_panel_states_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonGroup(
    title: String,
    features: List<AiFeature>,
    artifacts: Map<Int, Any>,
    running: Set<Int>,
    enabled: Set<AiFeature>,
    onRun: (AiFeature) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
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
                    ready -> colors.primary
                    on -> colors.surfaceContainer
                    else -> colors.surfaceContainerHighest
                }
                val fg = when {
                    ready -> colors.onPrimary
                    on -> colors.onSurfaceVariant
                    else -> colors.onSurfaceVariant
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = bg,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = !busy) { onRun(feature) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                color = colors.primary,
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
}

/** 未配置 API Key 的醒目提示：放在面板最上面，避免用户逐个点按钮才发现全都跑不了。 */
@Composable
private fun NoKeyBanner() {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceContainer) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.ai_key_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.ai_key_needed_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageBanner(text: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 一个输入框 + 两个动作（提问 / 解释术语）。
 *
 * 共用输入框而不是开两行输入框：这两个动作的输入形态一样（一句话），
 * 分成两个输入框会让面板多出一大块。但**动作的区别必须写出来**——
 * 只写一个「提问」按钮和一个孤零零的「解释术语」按钮，用户不知道后者读的是哪个输入。
 */
@Composable
private fun AskBlock(
    value: String,
    onValueChange: (String) -> Unit,
    onAsk: () -> Unit,
    onExplain: () -> Unit,
    askRunning: Boolean,
    explainRunning: Boolean,
    askEnabled: Boolean,
    explainEnabled: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text(
            text = stringResource(R.string.ai_ask_section),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.ai_ask_desc),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            placeholder = {
                Text(stringResource(R.string.ai_ask_hint), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            },
            maxLines = 3,
            shape = RoundedCornerShape(12.dp),
            colors = radarOutlinedTextFieldColors(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(enabled = !askRunning, onClick = onAsk) {
                Text(
                    text = if (askRunning) stringResource(R.string.ai_thinking) else stringResource(R.string.ai_ask),
                    color = if (askEnabled) colors.primary else colors.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(enabled = !explainRunning, onClick = onExplain) {
                Text(
                    text = if (explainRunning) stringResource(R.string.ai_verifying) else stringResource(R.string.ai_explain_term),
                    color = if (explainEnabled) colors.primary else colors.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            text = stringResource(R.string.ai_term_desc),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

// ── 产物卡片 ────────────────────────────────────────────────────────────────

@Composable
private fun AiResultCard(feature: AiFeature, payload: Any, actions: AiResultActions) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(14.dp), color = colors.surfaceContainerLowest) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = feature.label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            // 渲染分发收敛到注册表：每项功能的渲染入口在 AI_RESULT_RENDERS 一行可查，
            // 新功能在映射里登记一行即可，不再往 when 里追加分支。
            AI_RESULT_RENDERS[feature]?.invoke(payload, actions)
        }
    }
}
