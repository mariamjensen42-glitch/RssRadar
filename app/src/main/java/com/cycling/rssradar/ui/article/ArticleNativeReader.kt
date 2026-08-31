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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.cycling.rssradar.data.store.ReadingFontFamily
import com.cycling.rssradar.data.store.ReadingImageState
import com.cycling.rssradar.data.store.ReadingStyleState
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.Divider
import com.cycling.rssradar.ui.theme.Link
import com.cycling.rssradar.ui.theme.LocalReadingImage
import com.cycling.rssradar.ui.theme.LocalReadingStyle
import com.cycling.rssradar.ui.theme.Surface2
import com.cycling.rssradar.ui.theme.TextPrimary
import com.cycling.rssradar.ui.theme.TextSecondary
import kotlin.math.sqrt

/**
 * 原生 Compose 正文渲染器（ADR-0009 双渲染器）的渲染半边；解析在 [ReadingNodes]。
 *
 * 与 WebView 路的关键差异（原生路必须自己重做，库给不了）：
 * - 深色主题：WebView 靠注入 CSS 主题色；原生路直接读 RssRadarPalette getter 映射 TextStyle。
 * - 媒体占位卡：`<a class="media-card" href>` 用 Surface 卡片（▶ + 标签·域名）重画，点击外开。
 * - 图片：Coil AsyncImage（与 FeedIcon 同款 coil3），懒加载，不进 WebView 全高堆——避开 OOM。
 * - 文本天然可选中，顺手解决「阅读页闪烁时文本难选」的原始痛点。
 *
 * 退化已知（与决策一致，非 bug）：表格只给基础网格、内联样式/动画不还原、复杂排版不如 WebView。
 * 因此默认渲染器仍是 WEBVIEW，原生为 opt-in。
 *
 * [nodes]：[ReadingNodes.parse] 的产物。**空树不该走到这里**——调用方须在空树时回退 WebView，
 * 否则解析一无所获的文章会显示成空白页。
 * [onLinkClick]：所有外链（含媒体卡）的统一出口，调用方传 context.openUrl。
 * [onImageClick]：正文图片点击出口（ReadYou 差距表第 19 项）。图片圆角与"点图放大"
 * 开关直接读 LocalReadingImage——与 WebView 路读的是同一份偏好。
 */
@Composable
internal fun ArticleNativeReader(
    nodes: List<ReadingNode>,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = LocalReadingStyle.current
    NativeNodesColumn(
        nodes = nodes,
        onLinkClick = onLinkClick,
        onImageClick = onImageClick,
        modifier = modifier.padding(horizontal = style.horizontalPadding.dp),
    )
}

/**
 * 无自身边距的节点列渲染：供 [ArticleNativeReader] 与译文渲染区（TranslationReader，
 * 渐进/双语需要按段自由组合、外层统一控制边距与透明度）复用。
 * [dimmed] 整列压暗（graphicsLayer alpha），双语对照里原文列用它和译文区分层级。
 */
@Composable
internal fun NativeNodesColumn(
    nodes: List<ReadingNode>,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val style = LocalReadingStyle.current
    val image = LocalReadingImage.current
    // 链接点击统一走 LocalUriHandler：AnnotatedString 里的 LinkAnnotation.Url 默认由它打开，
    // 换成 onLinkClick 即改即生效，且不依赖 LinkInteractionListener 这种版本敏感 API。
    val handler = remember(onLinkClick) {
        object : UriHandler {
            override fun openUri(uri: String) = onLinkClick(uri)
        }
    }
    val alpha = if (dimmed) 0.62f else 1f

    CompositionLocalProvider(LocalUriHandler provides handler) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer { this.alpha = alpha },
        ) {
            nodes.forEach { node -> RenderNode(node, style, image, onLinkClick, onImageClick) }
        }
    }
}

// ———————————————————————————————————————————————
// 渲染：ReadingNode 树 → Compose
// ———————————————————————————————————————————————

private const val BLOCK_GAP_DP = 12

/** 图片显示高度上限（dp），与 NodeImage 的 heightIn 同源。 */
private const val IMAGE_MAX_HEIGHT_DP = 4000

// ———————————————————————————————————————————————
// 图片解码防线
// ———————————————————————————————————————————————

/**
 * 解码像素预算：≤5M px（ARGB_8888 ≈ 20MB）。
 *
 * 关键：显式 `.size()` 并不能封顶实际解码尺寸。Coil 3.3.0 的
 * `DecodeUtils.calculateInSampleSize` 用 `(src / dst).takeHighestOneBit()` 算采样率——
 * 只有 src 每边 ≥ 2×dst 才降采样；src 落在 (1×, 2×)dst 区间时 inSampleSize=1，按原图解码。
 * 因此最坏解码面积可达 4×dst，须保证 4×预算×4B < 100MB Canvas 上限 → 预算 ≤ 6.25M，取 5M 留余量。
 * 回归测试：ArticleImageDecodeTest。
 */
internal const val MAX_DECODE_PIXELS = 5_000_000

/**
 * 把期望解码尺寸收进像素预算。`Canvas: trying to draw too large(N bytes) bitmap` 的直接防线：
 * 只依赖布局约束降采样不可靠（Dialog 全屏、极端长图下 Coil 可能按原图解码），
 * 图片请求必须显式 `.size()`，且总像素不得突破单次绘制上限。
 */
internal fun clampDecodeSize(maxWidthPx: Int, maxHeightPx: Int): Size {
    val w = maxWidthPx.coerceAtLeast(1)
    val h = maxHeightPx.coerceAtLeast(1)
    val area = w.toLong() * h
    if (area <= MAX_DECODE_PIXELS) return Size(w, h)
    val scale = sqrt(MAX_DECODE_PIXELS.toDouble() / area)
    return Size((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
}

@Composable
private fun RenderNode(
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
                    color = TextPrimary,
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
                    color = TextPrimary,
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
                    color = Surface2,
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
                color = Surface2,
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
                        color = TextPrimary,
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
                    color = TextPrimary,
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
            val formulaBg = if (node.isFormula) Modifier.background(Surface2) else Modifier
            // 显式解码尺寸：宽度按屏、高度同 heightIn 上限，再过像素预算兜底。
            // 长图/大图按原图解码会直接撞 Canvas 上限崩溃（119MB bitmap 实案）。
            val context = LocalContext.current
            val screenWidthPx = with(LocalDensity.current) {
                LocalConfiguration.current.screenWidthDp.dp.roundToPx()
            }
            val maxHeightPx = with(LocalDensity.current) { IMAGE_MAX_HEIGHT_DP.dp.roundToPx() }
            val model = remember(node.src, screenWidthPx, maxHeightPx) {
                ImageRequest.Builder(context)
                    .data(node.src)
                    .size(clampDecodeSize(screenWidthPx, maxHeightPx))
                    .build()
            }
            AsyncImage(
                model = model,
                contentDescription = node.alt,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    // 极端长图（1×N 像素的追踪图/长条图）会把整屏撑爆，给个上限
                    .heightIn(max = 4000.dp)
                    .clip(RoundedCornerShape(image.cornerRadius.dp))
                    .then(formulaBg)
                    .then(click)
                    .padding(bottom = bottomPadding),
            )
            node.caption?.let { caption ->
                Text(
                    text = caption,
                    color = TextSecondary,
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
                    color = TextSecondary,
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
                            color = TextPrimary,
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
                            color = TextPrimary,
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
                color = Surface2,
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
                                ?: AnnotatedString("详情"),
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = style.fontSize.sp,
                                fontFamily = style.fontFamily.toComposeFontFamily(),
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Text(if (expanded) "−" else "+", color = TextSecondary)
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
        is NodeMediaCard -> {
            Surface(
                color = Surface2,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Divider),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLinkClick(node.url) }
                    .padding(bottom = bottomPadding),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp),
                ) {
                    Text("▶", color = Accent, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = node.label,
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
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
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Divider),
                        ) {
                        Column {
                            node.rows.forEachIndexed { idx, row ->
                                Row(
                                    modifier = Modifier.background(
                                        if (row.isHeader) Surface2 else Color.Unspecified,
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
                                                color = TextPrimary,
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
                                            .background(Divider),
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
                    .background(Divider),
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
                    color = TextSecondary,
                    fontSize = style.fontSize.sp,
                    fontFamily = style.fontFamily.toComposeFontFamily(),
                    modifier = Modifier.padding(end = 6.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    if (item.runs.isNotEmpty()) {
                        Text(
                            text = runsToAnnotated(item.runs, style),
                            color = TextPrimary,
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

/**
 * 行内片段 → AnnotatedString。
 * 链接用 LinkAnnotation.Url，点击经 LocalUriHandler（见 [ArticleNativeReader]）统一走 onLinkClick。
 */
private fun runsToAnnotated(runs: List<InlineRun>, style: ReadingStyleState): AnnotatedString =
    buildAnnotatedString {
        val baseFamily = style.fontFamily.toComposeFontFamily()
        for (run in runs) {
            when (run) {
                is InlineText -> {
                    pushStyle(
                        SpanStyle(
                            fontWeight = if (run.bold) FontWeight.Bold else null,
                            fontStyle = if (run.italic) FontStyle.Italic else null,
                            fontFamily = if (run.code) FontFamily.Monospace else baseFamily,
                            background = when {
                                run.code -> Surface2
                                run.mark -> MarkHighlight
                                else -> Color.Unspecified
                            },
                            color = run.color?.let { Color(it) } ?: Color.Unspecified,
                            textDecoration = when {
                                run.strike && run.underline -> TextDecoration.combine(
                                    listOf(TextDecoration.LineThrough, TextDecoration.Underline),
                                )
                                run.strike -> TextDecoration.LineThrough
                                run.underline -> TextDecoration.Underline
                                else -> null
                            },
                            // <sup>/<sub>：真上标/下标（脚注、化学式、指数），字号收一档
                            baselineShift = when (run.script) {
                                MathScript.NORMAL -> null
                                MathScript.SUPER -> BaselineShift.Superscript
                                MathScript.SUB -> BaselineShift.Subscript
                            },
                            fontSize = when {
                                run.script != MathScript.NORMAL -> (style.fontSize * SCRIPT_SIZE_FACTOR).sp
                                run.small -> (style.fontSize * SMALL_SIZE_FACTOR).sp
                                else -> TextUnit.Unspecified
                            },
                        ),
                    )
                    append(run.text)
                    pop()
                }
                is InlineMath -> mathSpans(run.spans, style)
                is InlineLink -> {
                    val start = length
                    pushStyle(
                        SpanStyle(
                            color = Link,
                            textDecoration = TextDecoration.Underline,
                            fontFamily = baseFamily,
                        ),
                    )
                    append(run.text)
                    pop()
                    addLink(LinkAnnotation.Url(run.url), start, length)
                }
            }
        }
    }

/** 上下标相对正文的字号比例。 */
private const val SCRIPT_SIZE_FACTOR = 0.75f

/** <small> 相对正文的字号比例。 */
private const val SMALL_SIZE_FACTOR = 0.85f

/** mark / style background-color 的高亮底色：半透明琥珀，深浅主题下都可见。 */
private val MarkHighlight = Color(0x66FFC107)

/** 解析端的段落对齐枚举 → Compose TextAlign。 */
private fun ParagraphAlign?.toCompose(): TextAlign? = when (this) {
    ParagraphAlign.LEFT -> TextAlign.Left
    ParagraphAlign.CENTER -> TextAlign.Center
    ParagraphAlign.RIGHT -> TextAlign.Right
    ParagraphAlign.JUSTIFY -> TextAlign.Justify
    null -> null
}

/** 公式片段 → SpanStyle 序列：变量斜体、上下标缩放。 */
private fun AnnotatedString.Builder.mathSpans(
    spans: List<MathSpan>,
    style: ReadingStyleState,
) {
    for (span in spans) {
        pushStyle(
            SpanStyle(
                fontStyle = if (span.italic) FontStyle.Italic else null,
                baselineShift = when (span.script) {
                    MathScript.NORMAL -> null
                    MathScript.SUPER -> BaselineShift.Superscript
                    MathScript.SUB -> BaselineShift.Subscript
                },
                fontSize = if (span.script == MathScript.NORMAL) {
                    TextUnit.Unspecified
                } else {
                    (style.fontSize * SCRIPT_SIZE_FACTOR).sp
                },
            ),
        )
        append(span.text)
        pop()
    }
}

/** 块级公式 → AnnotatedString。 */
private fun mathToAnnotated(spans: List<MathSpan>, style: ReadingStyleState): AnnotatedString =
    buildAnnotatedString { mathSpans(spans, style) }

/** Store 层的纯 JVM 字体族枚举 → Compose FontFamily。 */
private fun ReadingFontFamily.toComposeFontFamily(): FontFamily = when (this) {
    ReadingFontFamily.SYSTEM -> FontFamily.Default
    ReadingFontFamily.SERIF -> FontFamily.Serif
    ReadingFontFamily.MONOSPACE -> FontFamily.Monospace
}
