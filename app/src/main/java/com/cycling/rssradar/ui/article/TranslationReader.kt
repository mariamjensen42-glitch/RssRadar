package com.cycling.rssradar.ui.article

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.data.ai.TranslationBlockPair
import com.cycling.rssradar.data.ai.TranslationPairInput
import com.cycling.rssradar.data.ai.TranslationSegments
import com.cycling.rssradar.data.store.BilingualLayout
import com.cycling.rssradar.data.store.TranslationViewMode
import com.cycling.rssradar.ui.theme.Divider
import com.cycling.rssradar.ui.theme.LocalReadingPrefs

/**
 * 译文渲染区（翻译功能 v2）：渐进显示 + 双语对照的唯一实现。
 *
 * 为什么不走 WebView：渐进显示要求"翻完一段亮一段"，WebView 每次 reload 整页
 * 会闪烁且滚动位置丢失；Compose 按块重组则天然增量。因此译文一律经
 * [ReadingNodes.parse] 解析后走原生渲染（ADR-0009 的渲染半边复用，
 * 图片/媒体卡/链接点击行为与正文原生路一致）。
 *
 * 配对单位是**块**（[TranslationSegments] 两级切分里的顶层块，不是翻译分块）：
 * 双语对照严格"一段原文 → 它自己的译文"交替出现，标题/引用/代码块各自独立成对，
 * 不合并、不拆分，因此不会出现连续多段同为原文或同为译文的堆叠。
 * 列表块进一步拆到条目级（项一原文→项一译文→项二原文…），见 [buildRenderUnits]。
 *
 * 两级单位（chunk = API 往返单位、block = 配对单位）都由 [TranslationSegments] 定：
 * 原文块边界随 [TranslationSegmentUi.blocks] 带过来，这里只负责把**译文**切成块
 * 再按下标配对，不再自己重切原文。
 *
 * - 纯译文：有译文显示译文，未翻出的块显示原文淡显（翻到哪亮到哪）。
 * - 双语·上下：原文块（淡显）在上、其译文块紧贴在下，成对向下推进。
 * - 双语·左右：同一对的原文/译文各占半宽，中间细分隔线；逐对纵向堆叠。
 * - 双语模式的原文列经 [ReadingNodes.stripVisualDuplicates] 剥掉图片与代码块：
 *   这类块翻不翻都一样，双语下并排渲染成两份纯属噪音（用户反馈"图重复了"）；
 *   剥完为空（整块只有图/代码）就退化成只显示一份。
 *
 * 空块（解析一无所获）跳过——调用方（ReadingBody）已保证至少一块可渲染，
 * 否则整篇回退 WebView 路径。
 */
@Composable
internal fun TranslationReader(
    segments: List<TranslationSegmentUi>,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val display = LocalReadingPrefs.current.translation
    val style = LocalReadingPrefs.current.style
    // 块级配对：原文块与其译文块一一对应。分段内容变了才重算（渐进更新不重排已翻部分）。
    // 原文块边界由分段计划给定，这里只切译文侧。
    val pairs: List<TranslationBlockPair> = remember(segments) {
        TranslationSegments.pair(
            segments.map { TranslationPairInput(it.blocks, it.translatedHtml) },
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = style.horizontalPadding.dp),
    ) {
        pairs.forEachIndexed { index, pair ->
            // 每块解析结果按 HTML 串缓存：渐进更新只重组变化的块，已翻完的块不重复解析
            val originalNodes = remember(pair.originalHtml) {
                if (pair.originalHtml.isBlank()) emptyList() else ReadingNodes.parse(pair.originalHtml)
            }
            val translatedNodes = remember(pair.translatedHtml) {
                pair.translatedHtml?.takeIf { it.isNotBlank() }
                    ?.let { ReadingNodes.parse(it) }
                    ?.takeIf { it.isNotEmpty() }
            }
            // 列表块拆到条目级：项一原文→项一译文→项二原文→项二译文…
            val units = remember(pair.originalHtml, pair.translatedHtml) {
                buildRenderUnits(originalNodes, translatedNodes)
            }
            units.forEachIndexed { unitIndex, unit ->
                val sourceNodes = unit.source
                val targetNodes = unit.target
                // 双语模式的原文列剥掉图片/代码块（译文里原样出现，不该渲染第二份）；
                // 剥完为空 = 该块只有图片/代码 → 退化成只显示一份
                val bilingualSource = if (targetNodes != null) unit.dedupedSource else sourceNodes
                val imageOnlyBlock = targetNodes != null && bilingualSource.isEmpty()
                when (display.viewMode) {
                    TranslationViewMode.TRANSLATION_ONLY -> {
                        // 未翻出的块：原文淡显占位，翻完即被译文替换
                        NativeNodesColumn(
                            nodes = targetNodes ?: sourceNodes,
                            onLinkClick = onLinkClick,
                            onImageClick = onImageClick,
                            dimmed = targetNodes == null,
                        )
                    }
                    TranslationViewMode.BILINGUAL -> when {
                        // 纯图片/代码块：整块只渲染一次，不做无意义的"对照"
                        imageOnlyBlock -> NativeNodesColumn(
                            nodes = targetNodes!!,
                            onLinkClick = onLinkClick,
                            onImageClick = onImageClick,
                        )
                        display.bilingualLayout == BilingualLayout.STACKED -> {
                            NativeNodesColumn(
                                nodes = bilingualSource,
                                onLinkClick = onLinkClick,
                                onImageClick = onImageClick,
                                dimmed = true,
                            )
                            if (targetNodes != null) {
                                Spacer(Modifier.height(INNER_GAP_DP.dp))
                                NativeNodesColumn(
                                    nodes = targetNodes,
                                    onLinkClick = onLinkClick,
                                    onImageClick = onImageClick,
                                )
                            }
                        }
                        else -> {
                            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    NativeNodesColumn(
                                        nodes = bilingualSource,
                                        onLinkClick = onLinkClick,
                                        onImageClick = onImageClick,
                                        dimmed = true,
                                    )
                                }
                                if (targetNodes != null) {
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .fillMaxHeight()
                                            .background(Divider),
                                    )
                                    Box(modifier = Modifier.weight(1f)) {
                                        NativeNodesColumn(
                                            nodes = targetNodes,
                                            onLinkClick = onLinkClick,
                                            onImageClick = onImageClick,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (unitIndex < units.lastIndex) Spacer(Modifier.height(ITEM_GAP_DP.dp))
            }
            // 双语模式：成对之间给一道细分隔，视觉上把"原文+译文"这一对框起来，
            // 避免相邻两对的原文与译文在视觉上连成一片。
            if (index < pairs.lastIndex) {
                if (display.viewMode == TranslationViewMode.BILINGUAL) {
                    Spacer(Modifier.height(PAIR_GAP_DP.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Divider),
                    )
                    Spacer(Modifier.height(PAIR_GAP_DP.dp))
                } else {
                    Spacer(Modifier.height(BLOCK_GAP_DP.dp))
                }
            }
        }
    }
}

/** 一个双语渲染单元：原文侧节点、译文侧节点、以及原文侧去重后的节点。 */
private data class RenderUnit(
    val source: List<ReadingNode>,
    val target: List<ReadingNode>?,
    /** 双语模式的原文列：剥掉图片/代码块后的版本（[ReadingNodes.stripVisualDuplicates]）。 */
    val dedupedSource: List<ReadingNode>,
)

/**
 * 渲染单元：默认整块一个单元；列表块且原文/译文条目数一致时拆到条目级，
 * 让"项一原文→项一译文→项二原文→项二译文"严格交替。
 * 条目数对不上（模型没守住结构）就退回整块配对——宁可整块，也不做错位配对。
 */
private fun buildRenderUnits(
    original: List<ReadingNode>,
    translated: List<ReadingNode>?,
): List<RenderUnit> {
    val sourceList = original.singleOrNull() as? NodeList
    val targetList = translated?.singleOrNull() as? NodeList
    if (sourceList != null && targetList != null &&
        sourceList.items.size > 1 && sourceList.items.size == targetList.items.size
    ) {
        return sourceList.items.mapIndexed { index, item ->
            val source = listOf<ReadingNode>(NodeList(sourceList.ordered, listOf(item)))
            val target = listOf<ReadingNode>(
                NodeList(targetList.ordered, listOf(targetList.items[index])),
            )
            RenderUnit(source, target, ReadingNodes.stripVisualDuplicates(source))
        }
    }
    return listOf(RenderUnit(original, translated, ReadingNodes.stripVisualDuplicates(original)))
}

/** 一对内原文与译文的间距。 */
private const val INNER_GAP_DP = 6

/** 同一块被拆成多个单元时（列表条目）的单元间距。 */
private const val ITEM_GAP_DP = 4

/** 双语模式相邻两对之间的间距（分隔线上下各一份）。 */
private const val PAIR_GAP_DP = 10

/** 纯译文模式相邻块之间的间距。 */
private const val BLOCK_GAP_DP = 8
