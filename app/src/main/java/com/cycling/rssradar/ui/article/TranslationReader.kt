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
import com.cycling.rssradar.data.ai.TranslationSegments
import com.cycling.rssradar.data.store.BilingualLayout
import com.cycling.rssradar.data.store.TranslationViewMode
import com.cycling.rssradar.ui.theme.Divider
import com.cycling.rssradar.ui.theme.LocalReadingStyle
import com.cycling.rssradar.ui.theme.LocalTranslationDisplay

/**
 * 译文渲染区（翻译功能 v2）：渐进显示 + 双语对照的唯一实现。
 *
 * 为什么不走 WebView：渐进显示要求"翻完一段亮一段"，WebView 每次 reload 整页
 * 会闪烁且滚动位置丢失；Compose 按块重组则天然增量。因此译文一律经
 * [ReadingNodes.parse] 解析后走原生渲染（ADR-0009 的渲染半边复用，
 * 图片/媒体卡/链接点击行为与正文原生路一致）。
 *
 * 配对单位是**块**（[TranslationSegments.splitBlocks] 的顶层块，不是翻译分块）：
 * 双语对照严格"一段原文 → 它自己的译文"交替出现，标题/引用/代码块各自独立成对，
 * 不合并、不拆分，因此不会出现连续多段同为原文或同为译文的堆叠。
 * 列表块进一步拆到条目级（项一原文→项一译文→项二原文…），见 [buildRenderUnits]。
 * 翻译 API 仍按 ~1800 字的分块往返（省请求），块级配对在渲染侧摊平
 * （[TranslationSegments.pairBlocks]）。
 *
 * - 纯译文：有译文显示译文，未翻出的块显示原文淡显（翻到哪亮到哪）。
 * - 双语·上下：原文块（淡显）在上、其译文块紧贴在下，成对向下推进。
 * - 双语·左右：同一对的原文/译文各占半宽，中间细分隔线；逐对纵向堆叠。
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
    val display = LocalTranslationDisplay.current
    val style = LocalReadingStyle.current
    // 块级配对：原文块与其译文块一一对应。分段内容变了才重算（渐进更新不重排已翻部分）。
    val pairs: List<TranslationBlockPair> = remember(segments) {
        TranslationSegments.pairBlocks(segments.map { it.originalHtml to it.translatedHtml })
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
            units.forEachIndexed { unitIndex, (sourceNodes, targetNodes) ->
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
                    TranslationViewMode.BILINGUAL -> when (display.bilingualLayout) {
                        BilingualLayout.STACKED -> {
                            NativeNodesColumn(
                                nodes = sourceNodes,
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
                        BilingualLayout.SIDE_BY_SIDE -> {
                            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    NativeNodesColumn(
                                        nodes = sourceNodes,
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

/**
 * 渲染单元：默认整块一个单元；列表块且原文/译文条目数一致时拆到条目级，
 * 让"项一原文→项一译文→项二原文→项二译文"严格交替。
 * 条目数对不上（模型没守住结构）就退回整块配对——宁可整块，也不做错位配对。
 */
private fun buildRenderUnits(
    original: List<ReadingNode>,
    translated: List<ReadingNode>?,
): List<Pair<List<ReadingNode>, List<ReadingNode>?>> {
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
            source to target
        }
    }
    return listOf(original to translated)
}

/** 一对内原文与译文的间距。 */
private const val INNER_GAP_DP = 6

/** 同一块被拆成多个单元时（列表条目）的单元间距。 */
private const val ITEM_GAP_DP = 4

/** 双语模式相邻两对之间的间距（分隔线上下各一份）。 */
private const val PAIR_GAP_DP = 10

/** 纯译文模式相邻块之间的间距。 */
private const val BLOCK_GAP_DP = 8
