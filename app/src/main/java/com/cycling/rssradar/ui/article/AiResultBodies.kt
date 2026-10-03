package com.cycling.rssradar.ui.article

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.R
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
 * 各功能的产物渲染注册表：AiFeature → 渲染 composable。
 * 与 core 侧 [com.cycling.rssradar.core.data.ai.AiFeatureSpecs] 同一取向——
 * 每项功能的知识（core 侧行为 + app 侧渲染）各只有一处登记点。
 */
internal val AI_RESULT_RENDERS: Map<AiFeature, @Composable (Any) -> Unit> = mapOf(
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
    Text(stringResource(R.string.ai_topic_confidence, payload.topic, (payload.confidence * 100).toInt()), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
    if (payload.alternatives.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.ai_alternatives, payload.alternatives.joinToString(" / ")), style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
    }
}

@Composable
private fun SentimentBody(payload: AiSentimentPayload) {
    val colors = radarColors()
    val label = when (payload.polarity) {
        "POSITIVE" -> stringResource(R.string.ai_sentiment_positive)
        "NEGATIVE" -> stringResource(R.string.ai_sentiment_negative)
        else -> stringResource(R.string.ai_sentiment_neutral)
    }
    Text(stringResource(R.string.ai_sentiment_score, label, (payload.score * 100).toInt()), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
    if (payload.reason.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(payload.reason, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

@Composable
private fun QualityBody(payload: AiQualityPayload) {
    val colors = radarColors()
    Text(stringResource(R.string.ai_overall, payload.overall), style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    ScoreBar(stringResource(R.string.ai_info_density), payload.density)
    ScoreBar(stringResource(R.string.ai_originality), payload.originality)
    ScoreBar(stringResource(R.string.ai_evidence), payload.evidence)
    // 标题党是**反向指标**：越高越糟，这里用倒置后的长度显示，避免"条越长越好"的误读。
    ScoreBar(stringResource(R.string.ai_clickbait), payload.clickbait, inverted = true)
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
    Text(stringResource(R.string.ai_value, payload.value), style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
    if (payload.isNoise && payload.reasons.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.ai_noise), style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
        payload.reasons.forEach { reason ->
            Text("· $reason", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
    if (payload.keptPoints.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.ai_key_points), style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
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
            "FACT" -> stringResource(R.string.ai_fact)
            "DATA" -> stringResource(R.string.ai_data)
            else -> stringResource(R.string.ai_opinion)
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
                Text(stringResource(R.string.ai_claim_basis, claim.basis), style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
            }
        }
    }
}

@Composable
private fun CredibilityBody(payload: AiCredibilityPayload) {
    val colors = radarColors()
    val label = when (payload.level) {
        "HIGH" -> stringResource(R.string.ai_signal_strong)
        "MEDIUM" -> stringResource(R.string.ai_signal_medium)
        "LOW" -> stringResource(R.string.ai_signal_weak)
        else -> stringResource(R.string.ai_signal_insufficient)
    }
    Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
    if (payload.signals.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        payload.signals.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary) }
    }
    if (payload.doubts.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.ai_doubts, payload.doubts.joinToString("；")), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

@Composable
private fun ShareBody(payload: AiSharePayload) {
    val colors = radarColors()
    payload.variants.forEach { variant ->
        val label = when (variant.style) {
            "THREAD" -> stringResource(R.string.ai_style_long)
            "BULLET" -> stringResource(R.string.ai_style_bullets)
            else -> stringResource(R.string.ai_style_short)
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
        Text(stringResource(R.string.ai_basis), style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
        payload.quotes.forEach { quote ->
            Text("「$quote」", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
    if (payload.notFound) {
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.ai_not_in_article), style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
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
            payload.note.ifBlank { stringResource(R.string.ai_no_content) },
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        return
    }
    Text(
        stringResource(R.string.ai_fulltext_done, payload.html.length),
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
