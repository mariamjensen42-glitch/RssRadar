package com.cycling.rssradar.ui.article

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.size.Size
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.theme.LocalReadingPrefs
import kotlin.math.sqrt

/**
 * 原生 Compose 正文渲染器（ADR-0009 双渲染器）的渲染半边；解析在 [ReadingNodes]。
 *
 * 与 WebView 路的关键差异（原生路必须自己重做，库给不了）：
 * - 深色主题：WebView 靠注入 CSS 主题色；原生路直接读 radarColors() 映射 TextStyle。
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
 * 开关直接读 LocalReadingPrefs——与 WebView 路读的是同一份偏好。
 */
@Composable
internal fun ArticleNativeReader(
    nodes: List<ReadingNode>,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 页内查找的定位请求（「下一处」滚到命中所在的顶层节点）。 */
    focus: AnchorFocus? = null,
) {
    val style = LocalReadingPrefs.current.style
    // 字间距与正文对齐靠 ProvideTextStyle 下发，而不是挨个改 RenderNode 里的 TextStyle：
    // Compose 的 Text 会把显式 style 与 LocalTextStyle 合并，未指定的字段继承这里的值。
    // 于是「节点自带 align」仍然优先（那是内容的一部分），没有声明的段落才吃全局偏好——
    // 与 WebView 路 CSS 的表现一致。改一个地方胜过改九处 TextStyle。
    ProvideTextStyle(
        LocalTextStyle.current.copy(
            letterSpacing = style.letterSpacing.sp,
            textAlign = style.textAlign.toComposeAlign(),
        ),
    ) {
        NativeNodesColumn(
            nodes = nodes,
            onLinkClick = onLinkClick,
            onImageClick = onImageClick,
            modifier = modifier.padding(horizontal = style.horizontalPadding.dp),
            focus = focus,
        )
    }
}

/**
 * 页内查找的定位请求。[token] 是命中序号——同一条命中按两次「下一处」也要重新滚一次，
 * 光看 [anchor] 不会触发重组。null = 不定位（未开查找或没有命中）。
 */
internal data class AnchorFocus(val anchor: Int, val token: Int)

/**
 * 无自身边距的节点列渲染：供 [ArticleNativeReader] 与译文渲染区（TranslationReader，
 * 渐进/双语需要按段自由组合、外层统一控制边距与透明度）复用。
 * [dimmed] 整列压暗（graphicsLayer alpha），双语对照里原文列用它和译文区分层级。
 * [focus] 有值时，命中的顶层节点会被滚进视口（页内查找的「下一处」）。
 */
@Composable
internal fun NativeNodesColumn(
    nodes: List<ReadingNode>,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    focus: AnchorFocus? = null,
) {
    val style = LocalReadingPrefs.current.style
    val image = LocalReadingPrefs.current.image
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
            nodes.forEachIndexed { index, node ->
                // 只有被命中的那个顶层节点需要挂 requester。整页模式没有虚拟化
                // （Column + verticalScroll），所有节点都已组合，滚过去就能落到实处。
                if (focus != null && focus.anchor == index) {
                    val requester = remember { BringIntoViewRequester() }
                    // key 用 token 而非 anchor：同一条命中连按「下一处」也要重新滚
                    key(focus.token) {
                        LaunchedEffect(focus.token) { requester.bringIntoView() }
                    }
                    Box(modifier = Modifier.bringIntoViewRequester(requester)) {
                        RenderNode(node, style, image, onLinkClick, onImageClick)
                    }
                } else {
                    RenderNode(node, style, image, onLinkClick, onImageClick)
                }
            }
        }
    }
}

// ———————————————————————————————————————————————
// 渲染：ReadingNode 树 → Compose
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
