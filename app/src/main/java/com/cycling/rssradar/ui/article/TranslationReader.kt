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
import com.cycling.rssradar.data.store.BilingualLayout
import com.cycling.rssradar.data.store.TranslationViewMode
import com.cycling.rssradar.ui.theme.Divider
import com.cycling.rssradar.ui.theme.LocalReadingStyle
import com.cycling.rssradar.ui.theme.LocalTranslationDisplay

/**
 * 译文渲染区（翻译功能 v2）：渐进显示 + 双语对照的唯一实现。
 *
 * 为什么不走 WebView：渐进显示要求"翻完一段亮一段"，WebView 每次 reload 整页
 * 会闪烁且滚动位置丢失；Compose 按段重组则天然增量。因此译文一律经
 * [ReadingNodes.parse] 解析后走原生渲染（ADR-0009 的渲染半边复用，
 * 图片/媒体卡/链接点击行为与正文原生路一致）。
 *
 * - 纯译文：完成段显示译文；未完成段显示原文淡显（dimmed），翻到哪亮到哪。
 * - 双语·上下：原文列（淡显）与译文列纵向堆叠，按翻译分段成对。
 * -双语·左右：原文列与译文列各占一半宽度，中间细分隔线；窄屏下原文列
 *   可读性略降（用户显式选择的布局，如实呈现）。
 *
 * 空段（解析一无所获）跳过——调用方（ReadingBody）已保证至少一段可渲染，
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = style.horizontalPadding.dp),
    ) {
        segments.forEachIndexed { index, segment ->
            // 每段解析结果按 HTML 串缓存：渐进更新只重组变化的段，已翻完的段不重复解析
            val originalNodes = remember(segment.originalHtml) { ReadingNodes.parse(segment.originalHtml) }
            val translatedNodes = remember(segment.translatedHtml) {
                segment.translatedHtml?.let { ReadingNodes.parse(it) }?.takeIf { it.isNotEmpty() }
            }
            val hasPair = translatedNodes != null
            when (display.viewMode) {
                TranslationViewMode.TRANSLATION_ONLY -> {
                    // 未完成段：原文淡显占位，翻完即被译文替换
                    NativeNodesColumn(
                        nodes = translatedNodes ?: originalNodes,
                        onLinkClick = onLinkClick,
                        onImageClick = onImageClick,
                        dimmed = translatedNodes == null,
                    )
                }
                TranslationViewMode.BILINGUAL -> when (display.bilingualLayout) {
                    BilingualLayout.STACKED -> {
                        NativeNodesColumn(
                            nodes = originalNodes,
                            onLinkClick = onLinkClick,
                            onImageClick = onImageClick,
                            dimmed = true,
                        )
                        Spacer(Modifier.height(4.dp))
                        if (hasPair) {
                            NativeNodesColumn(
                                nodes = translatedNodes!!,
                                onLinkClick = onLinkClick,
                                onImageClick = onImageClick,
                            )
                        }
                    }
                    BilingualLayout.SIDE_BY_SIDE -> {
                        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            Box(modifier = Modifier.weight(1f)) {
                                NativeNodesColumn(
                                    nodes = originalNodes,
                                    onLinkClick = onLinkClick,
                                    onImageClick = onImageClick,
                                    dimmed = true,
                                )
                            }
                            if (hasPair) {
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(Divider),
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    NativeNodesColumn(
                                        nodes = translatedNodes!!,
                                        onLinkClick = onLinkClick,
                                        onImageClick = onImageClick,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (index < segments.lastIndex) Spacer(Modifier.height(PAIR_GAP_DP.dp))
        }
    }
}

/** 相邻翻译分段（原文/译文对）之间的间距。 */
private const val PAIR_GAP_DP = 18
