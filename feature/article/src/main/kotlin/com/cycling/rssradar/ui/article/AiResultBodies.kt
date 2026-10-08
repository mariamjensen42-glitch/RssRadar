package com.cycling.rssradar.ui.article

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.cycling.rssradar.core.data.ai.AiGlossaryPayload
import com.cycling.rssradar.core.data.ai.AiKeywordsPayload
import com.cycling.rssradar.core.data.ai.AiNoisePayload
import com.cycling.rssradar.core.data.ai.AiOutlinePayload
import com.cycling.rssradar.core.data.ai.AiQaPayload
import com.cycling.rssradar.core.data.ai.AiSharePayload
import com.cycling.rssradar.core.model.AiFeature

/**
 * 结果卡上的动作出口。
 *
 * 这张表补的是本模块最大的一个缺口：**AI 的结论原来全是终点**——
 * 12 张卡只有文本，点了没有任何反应（话题、标签、关键词、大纲、分享文案
 * 在设计文档里都写了"点击可……"，代码里一处 `clickable` 都没有）。
 * 算力已经花掉了，把结论接回阅读流才有价值：词能去搜、段能跳、文案能直接用。
 */
internal class AiResultActions(
    /** 把一个词送进搜索页（话题 / 标签 / 关键词）。 */
    val onSearch: (String) -> Unit = {},
    /** 复制到剪贴板。 */
    val onCopy: (String) -> Unit = {},
    /** 唤起系统分享。 */
    val onShare: (String) -> Unit = {},
    /** 跳到正文章节的第 [index] 节（0 基，共 [count] 节）。 */
    val onJumpToSection: (Int, Int) -> Unit = { _, _ -> },
)

/**
 * 各功能的产物渲染注册表：AiFeature → 渲染 composable。
 * 与 core 侧 [com.cycling.rssradar.core.data.ai.AiFeatureSpecs] 同一取向——
 * 每项功能的知识（core 侧行为 + app 侧渲染）各只有一处登记点。
 * 第二个参数是动作出口，不用的渲染函数写成 `{ p, _ -> … }`。
 */
internal val AI_RESULT_RENDERS: Map<AiFeature, @Composable (Any, AiResultActions) -> Unit> = mapOf(
    AiFeature.KEYWORDS to { p, a -> ChipFlow((p as AiKeywordsPayload).keywords, a) },
    AiFeature.NOISE to { p, _ -> NoiseBody(p as AiNoisePayload) },
    AiFeature.OUTLINE to { p, a -> OutlineBody(p as AiOutlinePayload, a) },
    AiFeature.SHARE_COPY to { p, a -> ShareBody(p as AiSharePayload, a) },
    AiFeature.QA to { p, _ -> QaBody(p as AiQaPayload) },
    AiFeature.GLOSSARY to { p, _ -> GlossaryBody(p as AiGlossaryPayload) },
)

/** 可点击的实心小胶囊（比裸文字多一个明确的点击面，比按钮轻）。 */
@Composable
private fun TinyAction(text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = colors.surfaceContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(items: List<String>, actions: AiResultActions) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { text ->
            TinyAction(text = text, onClick = { actions.onSearch(text) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoiseBody(payload: AiNoisePayload) {
    val colors = MaterialTheme.colorScheme
    Text(stringResource(R.string.ai_value, payload.value), style = MaterialTheme.typography.titleSmall, color = colors.onSurface, fontWeight = FontWeight.SemiBold)
    if (payload.isNoise && payload.reasons.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.ai_noise), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        payload.reasons.forEach { reason ->
            Text("· $reason", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
    if (payload.keptPoints.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.ai_key_points), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        payload.keptPoints.forEach { point ->
            Text("· $point", style = MaterialTheme.typography.bodySmall, color = colors.onSurface)
        }
    }
}

/**
 * 大纲：每节都可点，跳到正文对应位置。
 *
 * 比例按「节序号 / 节数」折算，**不用产物里的 anchor 文字去精确定位**——
 * anchor → 全文偏移的映射只存在于原生渲染器的文本映射里，视口模式（WebView 自己滚）
 * 拿不到；而两路都必须能用。这里是近似落点，但"点得动"胜过"精确却只有一半模式有效"。
 */
@Composable
private fun OutlineBody(payload: AiOutlinePayload, actions: AiResultActions) {
    val colors = MaterialTheme.colorScheme
    if (payload.gist.isNotBlank()) {
        Text(payload.gist, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
        Spacer(Modifier.height(8.dp))
    }
    val count = payload.sections.size
    payload.sections.forEachIndexed { index, section ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { actions.onJumpToSection(index, count) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                section.heading,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(96.dp),
            )
            Text(
                section.summary,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Lucide.ChevronRight,
                contentDescription = stringResource(R.string.ai_jump_to_section),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * 分享文案：每版各带「复制」与「分享」。
 *
 * 文案生成了却只能看不能拿走，等于白生成——这三个版本的存在意义就是被贴到别处去。
 */
@Composable
private fun ShareBody(payload: AiSharePayload, actions: AiResultActions) {
    val colors = MaterialTheme.colorScheme
    var copiedIndex by remember { mutableStateOf(-1) }
    val shareLabel = stringResource(R.string.ai_share)
    payload.variants.forEachIndexed { index, variant ->
        val label = when (variant.style) {
            "THREAD" -> stringResource(R.string.ai_style_long)
            "BULLET" -> stringResource(R.string.ai_style_bullets)
            else -> stringResource(R.string.ai_style_short)
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                TinyAction(text = if (copiedIndex == index) stringResource(R.string.ai_copied) else stringResource(R.string.ai_copy)) {
                    actions.onCopy(variant.text)
                    copiedIndex = index
                }
                Spacer(Modifier.width(6.dp))
                TinyAction(text = shareLabel) { actions.onShare(variant.text) }
            }
            Spacer(Modifier.height(4.dp))
            Text(variant.text, style = MaterialTheme.typography.bodySmall, color = colors.onSurface)
        }
    }
}

@Composable
private fun QaBody(payload: AiQaPayload) {
    val colors = MaterialTheme.colorScheme
    Text(payload.answer, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
    if (payload.quotes.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.ai_basis), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        payload.quotes.forEach { quote ->
            Text("「$quote」", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
    if (payload.notFound) {
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.ai_not_in_article), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun GlossaryBody(payload: AiGlossaryPayload) {
    val colors = MaterialTheme.colorScheme
    if (payload.term.isNotBlank()) {
        Text(
            payload.term,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
    }
    Text(payload.explanation, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
}
