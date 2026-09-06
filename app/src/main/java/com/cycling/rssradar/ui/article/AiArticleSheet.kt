package com.cycling.rssradar.ui.article

import androidx.compose.foundation.background
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.cycling.rssradar.core.data.ai.AiBriefPayload
import com.cycling.rssradar.core.data.ai.AiClassifyPayload
import com.cycling.rssradar.core.data.ai.AiCredibilityPayload
import com.cycling.rssradar.core.data.ai.AiFeature
import com.cycling.rssradar.core.data.ai.AiFulltextPayload
import com.cycling.rssradar.core.data.ai.AiGlossaryPayload
import com.cycling.rssradar.core.data.ai.AiKeywordsPayload
import com.cycling.rssradar.core.data.ai.AiNoisePayload
import com.cycling.rssradar.core.data.ai.AiOpinionPayload
import com.cycling.rssradar.core.data.ai.AiOutlinePayload
import com.cycling.rssradar.core.data.ai.AiQaPayload
import com.cycling.rssradar.core.data.ai.AiQualityPayload
import com.cycling.rssradar.core.data.ai.AiSentimentPayload
import com.cycling.rssradar.core.data.ai.AiSharePayload
import com.cycling.rssradar.core.data.ai.AiTagsPayload
import com.cycling.rssradar.core.ui.theme.radarColors


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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                    text = "AI 分析",
                    style = MaterialTheme.typography.titleMedium,
                    color = radarColors().textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "按需生成，生成后保存在本地，刷新不会覆盖。",
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
                        "还没有生成任何分析，点上面的按钮试试",
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
                            else -> "${feature.label} · 未开启"
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
                text = "未配置 API Key",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "AI 功能需要你自己的 DeepSeek Key。到「我的 → AI 与诊断」里填入后即可使用。",
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
                Text("问这篇文章…", color = colors.textTertiary, style = MaterialTheme.typography.bodyMedium)
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
                text = if (askRunning) "思考中" else "提问",
                color = if (askEnabled) colors.accent else colors.textTertiary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "也可以输入一个术语，解释它在本文中的含义",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            TextButton(enabled = !explainRunning, onClick = onExplain) {
                Text(
                    text = if (explainRunning) "查证中" else "解释术语",
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

/**
 * 各功能的产物渲染注册表：AiFeature → 渲染 composable。
 * 与 core 侧 [com.cycling.rssradar.core.data.ai.AiFeatureSpecs] 同一取向——
 * 每项功能的知识（core 侧行为 + app 侧渲染）各只有一处登记点。
 */
private val AI_RESULT_RENDERS: Map<AiFeature, @Composable (Any) -> Unit> = mapOf(
    AiFeature.TAGS to { p -> ChipFlow((p as AiTagsPayload).tags) },
    AiFeature.KEYWORDS to { p -> ChipFlow((p as AiKeywordsPayload).keywords) },
    AiFeature.CLASSIFY to { p -> ClassifyBody(p as AiClassifyPayload) },
    AiFeature.SENTIMENT to { p -> SentimentBody(p as AiSentimentPayload) },
    AiFeature.QUALITY to { p -> QualityBody(p as AiQualityPayload) },
    AiFeature.NOISE to { p -> NoiseBody(p as AiNoisePayload) },
    AiFeature.OUTLINE to { p -> OutlineBody(p as AiOutlinePayload) },
    AiFeature.OPINION to { p -> OpinionBody(p as AiOpinionPayload) },
    AiFeature.CREDIBILITY to { p -> CredibilityBody(p as AiCredibilityPayload) },
    AiFeature.SHARE_COPY to { p -> ShareBody(p as AiSharePayload) },
    AiFeature.QA to { p -> QaBody(p as AiQaPayload) },
    AiFeature.DAILY_BRIEF to { p -> BriefBody(p as AiBriefPayload) },
    AiFeature.GLOSSARY to { p -> GlossaryBody(p as AiGlossaryPayload) },
    AiFeature.FULLTEXT to { p -> FulltextBody(p as AiFulltextPayload) },
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(items: List<String>) {
    val colors = radarColors()
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { text ->
            Surface(shape = RoundedCornerShape(6.dp), color = colors.surface2) {
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun ClassifyBody(payload: AiClassifyPayload) {
    val colors = radarColors()
    Text("${payload.topic}  ·  置信度 ${(payload.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
    if (payload.alternatives.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text("也可能是：${payload.alternatives.joinToString(" / ")}", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
    }
}

@Composable
private fun SentimentBody(payload: AiSentimentPayload) {
    val colors = radarColors()
    val label = when (payload.polarity) {
        "POSITIVE" -> "偏正面"
        "NEGATIVE" -> "偏负面"
        else -> "中性"
    }
    Text("$label  ·  强度 ${(payload.score * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
    if (payload.reason.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(payload.reason, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

@Composable
private fun QualityBody(payload: AiQualityPayload) {
    val colors = radarColors()
    Text("综合 ${payload.overall} / 100", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    ScoreBar("信息密度", payload.density)
    ScoreBar("原创性", payload.originality)
    ScoreBar("证据充分性", payload.evidence)
    // 标题党是**反向指标**：越高越糟，这里用倒置后的长度显示，避免"条越长越好"的误读。
    ScoreBar("标题党程度", payload.clickbait, inverted = true)
    if (payload.note.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(payload.note, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

@Composable
private fun ScoreBar(label: String, value: Int, inverted: Boolean = false) {
    val colors = radarColors()
    val ratio = value.coerceIn(0, 100) / 100f
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                "$value",
                style = MaterialTheme.typography.bodySmall,
                color = if (inverted && value >= 60) colors.textSecondary else colors.textTertiary,
            )
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).background(colors.surface3, RoundedCornerShape(2.dp))) {
            Box(
                Modifier
                    .fillMaxWidth(if (inverted) (1f - ratio) else ratio)
                    .height(4.dp)
                    .background(colors.accent, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun NoiseBody(payload: AiNoisePayload) {
    val colors = radarColors()
    Text("信息价值 ${payload.value} / 100", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
    if (payload.isNoise && payload.reasons.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("噪声信号", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
        payload.reasons.forEach { reason ->
            Text("· $reason", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
    if (payload.keptPoints.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("实质要点", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
        payload.keptPoints.forEach { point ->
            Text("· $point", style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
        }
    }
}

@Composable
private fun OutlineBody(payload: AiOutlinePayload) {
    val colors = radarColors()
    if (payload.gist.isNotBlank()) {
        Text(payload.gist, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        Spacer(Modifier.height(8.dp))
    }
    payload.sections.forEach { section ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(
                section.heading,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(96.dp),
            )
            Text(
                section.summary,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OpinionBody(payload: AiOpinionPayload) {
    val colors = radarColors()
    payload.claims.forEach { claim ->
        val kind = when (claim.kind) {
            "FACT" -> "事实"
            "DATA" -> "数据"
            else -> "观点"
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(4.dp), color = colors.surface2) {
                    Text(
                        kind,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    claim.claim,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
            if (claim.basis.isNotBlank()) {
                Text("依据：${claim.basis}", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
            }
        }
    }
}

@Composable
private fun CredibilityBody(payload: AiCredibilityPayload) {
    val colors = radarColors()
    val label = when (payload.level) {
        "HIGH" -> "信号较强"
        "MEDIUM" -> "信号一般"
        "LOW" -> "信号较弱"
        else -> "信息不足"
    }
    Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
    if (payload.signals.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        payload.signals.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary) }
    }
    if (payload.doubts.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text("存疑点：${payload.doubts.joinToString("；")}", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

@Composable
private fun ShareBody(payload: AiSharePayload) {
    val colors = radarColors()
    payload.variants.forEach { variant ->
        val label = when (variant.style) {
            "THREAD" -> "长推"
            "BULLET" -> "要点体"
            else -> "短评"
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
            Spacer(Modifier.height(2.dp))
            Text(variant.text, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
        }
    }
}

@Composable
private fun QaBody(payload: AiQaPayload) {
    val colors = radarColors()
    Text(payload.answer, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
    if (payload.quotes.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("依据", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
        payload.quotes.forEach { quote ->
            Text("「$quote」", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
    if (payload.notFound) {
        Spacer(Modifier.height(6.dp))
        Text("文中未提及", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
    }
}

@Composable
private fun GlossaryBody(payload: AiGlossaryPayload) {
    val colors = radarColors()
    if (payload.term.isNotBlank()) {
        Text(
            payload.term,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
    }
    Text(payload.explanation, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
}

/**
 * 全文提取的结果**不在这里展示正文**——它已经写回文章了，再贴一遍会撑爆面板。
 * 这里只如实回报提取到多少字，让"成功/失败"这件事对可见。
 */
@Composable
private fun FulltextBody(payload: AiFulltextPayload) {
    val colors = radarColors()
    if (!payload.ok) {
        Text(
            payload.note.ifBlank { "未能从该页面提取到正文" },
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        return
    }
    Text(
        "已提取 ${payload.html.length} 字并写入正文，向上滚动即可阅读",
        style = MaterialTheme.typography.bodySmall,
        color = colors.textPrimary,
    )
}

@Composable
private fun BriefBody(payload: AiBriefPayload) {
    val colors = radarColors()
    if (payload.headline.isNotBlank()) {
        Text(payload.headline, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        Spacer(Modifier.height(8.dp))
    }
    payload.items.forEach { item ->
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text("· ${item.title}", style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
            if (item.why.isNotBlank()) {
                Text(item.why, style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
            }
        }
    }
}
