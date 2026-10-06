package com.cycling.rssradar.ui.article

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import com.cycling.rssradar.core.model.ReadingImageState
import com.cycling.rssradar.core.model.ReadingStyleState
import com.cycling.rssradar.core.ui.components.ShimmerOverlay
import com.cycling.rssradar.core.ui.components.rememberImageLoadState
import com.cycling.rssradar.core.ui.theme.LocalReducedMotion
import com.cycling.rssradar.core.ui.theme.crossfadeMotion
import com.cycling.rssradar.core.ui.theme.radarColors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal const val BLOCK_GAP_DP = 12

/** 图片显示高度上限（dp），与 NodeImage 的 heightIn 同源。 */
private const val IMAGE_MAX_HEIGHT_DP = 4000

/**
 * 图片 HTML 未声明尺寸时的占位高度。
 *
 * 比例无从预知，只能垫固定一块——这类图加载完仍会有位移，是占位方案的固有代价。
 * 声明了尺寸的图走 [imagePlaceholderSize]：占位高度与真图一致，加载完成不产生位移。
 */
private const val IMAGE_PLACEHOLDER_HEIGHT_DP = 220

/**
 * 加载中占位块的尺寸。
 *
 * 声明了原始像素尺寸的图按比例撑开，占位高度 = 真图高度 ⇒ 整段内容不上下跳；
 * 没声明（或声明残缺）的退回固定高度。
 *
 * 这只约束**占位块**，不碰真图自身的尺寸约束：`<img>` 声明的尺寸偶尔与实物不符
 * （响应式图常见），拿它去卡真图会裁切或留白。
 */
private fun imagePlaceholderSize(node: NodeImage): Modifier {
    val w = node.width
    val h = node.height
    return if (w != null && h != null && h > 0) {
        Modifier
            .aspectRatio(w.toFloat() / h)
            .heightIn(max = IMAGE_MAX_HEIGHT_DP.dp)
    } else {
        Modifier.height(IMAGE_PLACEHOLDER_HEIGHT_DP.dp)
    }
}

// ———————————————————————————————————————————————
// 图片解码防线
// ———————————————————————————————————————————————

@Composable
internal fun RenderNode(
    node: ReadingNode,
    style: ReadingStyleState,
    image: ReadingImageState,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    depth: Int = 0,
    bottomPadding: Dp = BLOCK_GAP_DP.dp,
) {
    // 组合也是递归：深度超限就停，防御解析端漏网的怪树（解析有 [ReadingNodes.MAX_DEPTH]，
    // 这里是渲染侧的同一道闸）。
    if (depth > ReadingNodes.MAX_DEPTH) return

    when (node) {
        is NodeParagraph -> {
            val annotated = runsToAnnotated(node.runs, style)
            if (annotated.text.isNotBlank()) {
                Text(
                    text = annotated,
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = style.fontSize.sp,
                        lineHeight = (style.fontSize * style.lineHeight).sp,
                        fontFamily = style.fontFamily.toComposeFontFamily(),
                    ),
                    textAlign = node.align.toCompose(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = bottomPadding),
                )
            }
        }
        is NodeHeading -> {
            val annotated = runsToAnnotated(node.runs, style)
            if (annotated.text.isNotBlank()) {
                val scale = when (node.level) {
                    1 -> 1.45f
                    2 -> 1.28f
                    3 -> 1.12f
                    else -> 1.0f
                }
                Text(
                    text = annotated,
                    color = radarColors().textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = (style.fontSize * scale).sp,
                    lineHeight = (style.fontSize * scale * 1.4f).sp,
                    fontFamily = style.fontFamily.toComposeFontFamily(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = if (node.level <= 2) 16.dp else 10.dp,
                            bottom = bottomPadding,
                        ),
                )
            }
        }
        is NodeList -> RenderList(node, style, depth, bottomPadding, onLinkClick)
        is NodeQuote -> {
            if (node.blocks.isNotEmpty()) {
                Surface(
                    color = radarColors().surface2,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = bottomPadding),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        node.blocks.forEachIndexed { index, block ->
                            RenderNode(
                                node = block,
                                style = style,
                                image = image,
                                onLinkClick = onLinkClick,
                                onImageClick = onImageClick,
                                depth = depth + 1,
                                // 最后一块不再留底距，否则卡片底部空一截
                                bottomPadding = if (index == node.blocks.lastIndex) 0.dp else 8.dp,
                            )
                        }
                    }
                }
            }
        }
        is NodeCode -> {
            Surface(
                color = radarColors().surface2,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = bottomPadding),
            ) {
                Box(
                    modifier = Modifier
                        // 先 padding 再 scroll：留白属于可滚动内容，横向滚到底也有边距
                        .padding(12.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = node.code,
                        color = radarColors().textPrimary,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        is NodeMath -> {
            val annotated = mathToAnnotated(node.spans, style)
            if (annotated.text.isNotBlank()) {
                Text(
                    text = annotated,
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = style.fontSize.sp,
                        lineHeight = (style.fontSize * style.lineHeight).sp,
                        fontFamily = style.fontFamily.toComposeFontFamily(),
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                )
            }
        }
        is NodeImage -> {
            // 图片点击分流：开了"点图放大"就放大；没开且图本身是链接（<a href><img></a>）
            // 就还原链接语义，与 WebView 路"已经是链接的图不抢它点击"同一原则。
            val click = when {
                image.maximizeOnTap -> Modifier.clickable { onImageClick(node.src) }
                node.href != null -> Modifier.clickable { onLinkClick(node.href) }
                else -> Modifier
            }
            // 公式图（LaTeX CDN）是黑字透明底：不垫浅色底，深色主题下直接隐形
            val formulaBg = if (node.isFormula) Modifier.background(radarColors().surface2) else Modifier
            // 显式解码尺寸：宽度按屏、高度同 heightIn 上限，再过像素预算兜底。
            // 长图/大图按原图解码会直接撞 Canvas 上限崩溃（119MB bitmap 实案）。
            val context = LocalContext.current
            val reducedMotion = LocalReducedMotion.current
            val screenWidthPx = with(LocalDensity.current) {
                LocalConfiguration.current.screenWidthDp.dp.roundToPx()
            }
            val maxHeightPx = with(LocalDensity.current) { IMAGE_MAX_HEIGHT_DP.dp.roundToPx() }
            // 加载态驱动 shimmer 占位：声明了尺寸的图按比例撑开（占位与真图等高，加载完不跳），
            // 没声明的只能垫固定高度（那种图加载完仍会有位移）。
            // 判定收敛到 ImageLoadState：正文按需抓取完成后整篇重建，图片节点会跟着重新请求，
            // 而 shimmer 是不透明底 —— 回退一次就把已显示的图整块盖住（「一闪又没了」的成因）。
            val imageLoadState = rememberImageLoadState(node.src)
            val model = remember(node.src, screenWidthPx, maxHeightPx, reducedMotion) {
                ImageRequest.Builder(context)
                    .data(node.src)
                    .size(clampDecodeSize(screenWidthPx, maxHeightPx))
                    // crossfade 200ms（docs/motion.md #3）；reduce-motion 关掉渐显
                    .crossfadeMotion(reducedMotion)
                    .build()
            }
            Box(Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = model,
                    contentDescription = node.alt,
                    contentScale = ContentScale.FillWidth,
                    onState = imageLoadState::onState,
                    modifier = Modifier
                        .fillMaxWidth()
                        // 极端长图（1×N 像素的追踪图/长条图）会把整屏撑爆，给个上限
                        .heightIn(max = 4000.dp)
                        // 块间距必须留在 clip 之外：padding 落在裁剪内层时，图片底边被推离
                        // 圆角区，会变成只有上面两角圆、下面两角直角
                        .padding(bottom = bottomPadding)
                        .clip(RoundedCornerShape(image.cornerRadius.dp))
                        .then(formulaBg)
                        .then(click),
                )
                if (imageLoadState.loading && !reducedMotion) {
                    ShimmerOverlay(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(imagePlaceholderSize(node))
                            .padding(bottom = bottomPadding)
                            .clip(RoundedCornerShape(image.cornerRadius.dp)),
                    )
                }
            }
            node.caption?.let { caption ->
                Text(
                    text = caption,
                    color = radarColors().textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = bottomPadding),
                )
            }
        }
        is NodeCaption -> {
            val annotated = runsToAnnotated(node.runs, style)
            if (annotated.text.isNotBlank()) {
                Text(
                    text = annotated,
                    color = radarColors().textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = bottomPadding),
                )
            }
        }
        is NodeDefList -> {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding)) {
                node.items.forEachIndexed { index, item ->
                    if (item.termRuns.isNotEmpty()) {
                        Text(
                            text = runsToAnnotated(item.termRuns, style),
                            color = radarColors().textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = style.fontSize.sp,
                                fontFamily = style.fontFamily.toComposeFontFamily(),
                            ),
                        )
                    }
                    if (item.descRuns.isNotEmpty()) {
                        Text(
                            text = runsToAnnotated(item.descRuns, style),
                            color = radarColors().textPrimary,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = style.fontSize.sp,
                                lineHeight = (style.fontSize * style.lineHeight).sp,
                                fontFamily = style.fontFamily.toComposeFontFamily(),
                            ),
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                    if (index < node.items.lastIndex) Spacer(Modifier.height(6.dp))
                }
            }
        }
        is NodeDetails -> {
            var expanded by remember { mutableStateOf(false) }
            Surface(
                color = radarColors().surface2,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = bottomPadding),
            ) {
                Column(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = node.summaryRuns
                                ?.let { runsToAnnotated(it, style) }
                                ?.takeIf { it.text.isNotBlank() }
                                ?: AnnotatedString(stringResource(R.string.details)),
                            color = radarColors().textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = style.fontSize.sp,
                                fontFamily = style.fontFamily.toComposeFontFamily(),
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Text(if (expanded) "−" else "+", color = radarColors().textSecondary)
                    }
                    if (expanded) {
                        Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
                            node.blocks.forEachIndexed { index, block ->
                                RenderNode(
                                    node = block,
                                    style = style,
                                    image = image,
                                    onLinkClick = onLinkClick,
                                    onImageClick = onImageClick,
                                    depth = depth + 1,
                                    // 块间保留默认间距，最后一块交给卡片的 bottom padding
                                    bottomPadding = if (index < node.blocks.lastIndex) BLOCK_GAP_DP.dp else 0.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
        // 正文媒体（ADR-0018）：直链 video/audio 内嵌播放，iframe 保持外跳卡。
        // 分派全在 InlineMediaNode 里，这里只透传块间距与外链出口。
        is NodeMediaCard -> InlineMediaNode(node, onLinkClick, bottomPadding)
        is NodeTable -> {
            if (node.rows.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = bottomPadding),
                ) {
                    Column {
                        node.caption?.let { caption ->
                            Text(
                                text = caption,
                                color = radarColors().textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, radarColors().divider),
                        ) {
                        Column {
                            node.rows.forEachIndexed { idx, row ->
                                Row(
                                    modifier = Modifier.background(
                                        if (row.isHeader) radarColors().surface2 else Color.Unspecified,
                                    ),
                                ) {
                                    row.cells.forEach { cellRuns ->
                                        Box(
                                            modifier = Modifier
                                                .widthIn(min = 80.dp, max = 240.dp)
                                                .padding(8.dp),
                                        ) {
                                            Text(
                                                text = runsToAnnotated(cellRuns, style),
                                                color = radarColors().textPrimary,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                }
                                if (idx < node.rows.lastIndex) {
                                    Spacer(
                                        modifier = Modifier
                                            .height(1.dp)
                                            .fillMaxWidth()
                                            .background(radarColors().divider),
                                    )
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
        is NodeRule -> {
            // padding 必须在 background 之前：写反了会画成一条 17dp 高的粗杠
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .height(1.dp)
                    .background(radarColors().divider),
            )
        }
        is NodeGroup -> {
            Column(Modifier.fillMaxWidth()) {
                node.nodes.forEach { child ->
                    RenderNode(child, style, image, onLinkClick, onImageClick, depth + 1)
                }
            }
        }
    }
}

@Composable
private fun RenderList(
    node: NodeList,
    style: ReadingStyleState,
    depth: Int,
    bottomPadding: Dp,
    onLinkClick: (String) -> Unit,
) {
    // 组合也是递归：嵌套列表的深度由解析器封顶，这里再加一道闸，防御漏网的怪树。
    if (depth > ReadingNodes.MAX_DEPTH) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        node.items.forEachIndexed { index, item ->
            val prefix = if (node.ordered) "${index + 1}. " else "• "
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = prefix,
                    color = radarColors().textSecondary,
                    fontSize = style.fontSize.sp,
                    fontFamily = style.fontFamily.toComposeFontFamily(),
                    modifier = Modifier.padding(end = 6.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    if (item.runs.isNotEmpty()) {
                        Text(
                            text = runsToAnnotated(item.runs, style),
                            color = radarColors().textPrimary,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = style.fontSize.sp,
                                lineHeight = (style.fontSize * style.lineHeight).sp,
                                fontFamily = style.fontFamily.toComposeFontFamily(),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    // 嵌套列表缩进一级渲染（旧实现把它压成同一行的文本）
                    if (item.nested != null) {
                        RenderList(
                            node = item.nested,
                            style = style,
                            depth = depth + 1,
                            bottomPadding = 0.dp,
                            onLinkClick = onLinkClick,
                        )
                    }
                }
            }
        }
    }
}

/** 当前块内的标注与查找命中区间。三者任一变了才重算，滚动时零开销。 */
@Composable
internal fun rememberBlockOverlays(runs: List<InlineRun>): List<ReadingAnnotations.Overlay> {
    val annotations = LocalReadingAnnotations.current
    val find = LocalFindHighlight.current
    return remember(runs, annotations, find) {
        val text = ReadingAnnotations.blockText(runs)
        // 查找命中排在标注之后：同一区间重叠时，临时的查找态盖住持久的标注态
        ReadingAnnotations.overlays(text, annotations) +
            (find?.let { ReadingAnnotations.findOverlays(text, it) } ?: emptyList())
    }
}
