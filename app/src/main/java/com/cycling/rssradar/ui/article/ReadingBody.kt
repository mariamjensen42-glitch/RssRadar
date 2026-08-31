package com.cycling.rssradar.ui.article

import android.text.format.DateUtils
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cycling.rssradar.data.db.ArticleWithFeed
import com.cycling.rssradar.data.store.BilingualLayout
import com.cycling.rssradar.data.store.ReadingFontFamily
import com.cycling.rssradar.data.store.ReadingRenderer
import com.cycling.rssradar.data.store.ReadingStyleState
import com.cycling.rssradar.data.store.TranslationDisplayState
import com.cycling.rssradar.data.store.TranslationViewMode
import com.cycling.rssradar.ui.components.FeedIcon
import com.cycling.rssradar.ui.components.openUrl
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.BgRoot
import com.cycling.rssradar.ui.theme.Link
import com.cycling.rssradar.ui.theme.LocalReadingImage
import com.cycling.rssradar.ui.theme.LocalReadingRenderer
import com.cycling.rssradar.ui.theme.LocalReadingStyle
import com.cycling.rssradar.ui.theme.LocalTranslationDisplay
import com.cycling.rssradar.ui.theme.OnAccent
import com.cycling.rssradar.ui.theme.Surface1
import com.cycling.rssradar.ui.theme.Surface2
import com.cycling.rssradar.ui.theme.TextPrimary
import com.cycling.rssradar.ui.theme.TextSecondary
import com.cycling.rssradar.ui.theme.TextTertiary
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import kotlin.math.roundToInt

/**
 * 阅读页正文渲染深模块：头部（源名/标题/AI 摘要卡/译文横幅）+ 正文两种渲染模式的
 * 全部分支细节都收在这里，调用方（ArticleDetailScreen）只见一个入口。
 *
 * 模式选择（ADR-0007 混合渲染，内存约束不动）：
 * - 有图正文 → 视口渲染：WebView 内部滚动驱动头部折叠（layout modifier 只动高度
 *   不改渲染模式），触摸归 WebView。
 * - 纯文字 → 整页渲染：单滚动容器，头部随正文滚出，触摸穿透给 Compose。
 *
 * 顶栏「标题滚出视口才补位」所需的折叠量与量测经 [onHeaderScroll]/[onTitleMeasured]
 * 上抛，滚动状态本身留在 Screen。
 */
@Composable
internal fun ReadingBody(
    article: ArticleWithFeed,
    isFetchingContent: Boolean,
    aiSummaryState: AiSummaryState,
    translationState: TranslationState,
    scrollState: ScrollState,
    /** 视口模式的头部折叠量（= WebView 内部滚动量），随滚驱动。 */
    headerScrollY: Int,
    onHeaderScroll: (Int) -> Unit,
    onTitleMeasured: (Int) -> Unit,
    onGenerateSummary: () -> Unit,
    onRetranslate: () -> Unit,
    onShowOriginal: () -> Unit,
    /** 译文显示偏好（纯译文/双语、上下/左右）变化出口，VM 写回持久化 Store。 */
    onTranslationDisplayChange: (TranslationDisplayState) -> Unit,
    /**
     * 正文图片点击（ReadYou 差距表第 19 项）：收到的是图片地址，Screen 用它打开全屏查看页
     * 并定位到对应那张。WebView 路走"img 包 a + 拦截 URL"，原生路走 Compose clickable，
     * 两条路都收敛到这一个出口。
     */
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 水平边距不放在外层：正文 WebView 的边距由排版设置的 CSS padding 控制（issue #42），
    // 头部（源名/标题）保持固定 20dp 不随排版项变化。
    // 翻译激活（渐进中或已完成）时正文走 TranslationReader（原生分段渲染），
    // 强制整页分支——视口折叠依赖 WebView 内部滚动，原生路驱动不了（与正文原生路同因）。
    val translationUi = when (val state = translationState) {
        is TranslationState.Progressing -> state
        is TranslationState.Shown -> state
        else -> null
    }
    val translationSegments: List<TranslationSegmentUi> = when (val state = translationState) {
        is TranslationState.Progressing -> state.segments
        is TranslationState.Shown -> state.segments
        else -> emptyList()
    }
    val bodyHtml: String? = when {
        translationUi != null -> null
        article.article.content != null -> article.article.content
        else -> null
    }
    // 原生渲染器（ADR-0009）：解析出的中间树非空才走 Compose。空树 = 解析一无所获或解析失败
    // （ReadingNodes 会吞掉一切异常），此时自动回退 WebView——绝不把正文渲染成空白页。
    // 解析只在 renderer 为 NATIVE 时做，WebView 路不付这份开销。
    val renderer = LocalReadingRenderer.current.renderer
    val nativeHtml = if (translationUi == null) article.article.content else null
    val nativeNodes = remember(renderer, nativeHtml) {
        if (renderer == ReadingRenderer.NATIVE && nativeHtml != null) ReadingNodes.parse(nativeHtml) else emptyList()
    }
    val useNative = nativeNodes.isNotEmpty()
    // 译文分段全空（解析一无所获的怪 HTML）→ 回退 WebView 老路：显示已完成译文或原文。
    val translationFallbackHtml = remember(translationSegments, article.article.content, article.article.summary) {
        if (translationSegments.isNotEmpty() && translationSegments.all { seg ->
                val html = seg.translatedHtml ?: seg.originalHtml
                html.isBlank() || ReadingNodes.parse(html).isEmpty()
            }
        ) {
            translationSegments.mapNotNull { it.translatedHtml }.joinToString("").ifBlank { null }
                ?: (article.article.content ?: article.article.summary)
        } else {
            null
        }
    }
    // OOM 防线（闪退诊断）：整页包高的 WebView 会被 Chromium 视为全部内容可见，
    // 有图文章的所有图片同时解码进 Java 堆，图多必 OOM（256MB 堆几十秒吃满）。
    // 混合模式：有图 → 视口渲染；纯文字 → 整页渲染，文字栅格内存可控。
    val viewport = translationUi == null &&
        (if (useNative) false else bodyHtml?.contains("<img", ignoreCase = true) == true)
    // 全屏查看页的多图列表与点击分流共用这一份；空串/无图正文 → 空集合，两条路都自动静默。
    val imageUrls = remember(bodyHtml) { bodyHtml?.let { ReadingImages.extract(it) } ?: emptyList() }

    if (viewport) {
        Column(modifier = modifier.padding(vertical = 8.dp)) {
            // 视口模式的"随滚"体验（与整页模式对齐）：WebView 内部滚动量驱动头部向上折叠。
            // 只动布局高度不改渲染模式——不触碰 ADR-0007 的视口渲染与内存约束。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val visible = (placeable.height - headerScrollY).coerceAtLeast(0)
                        layout(placeable.width, visible) {
                            placeable.placeRelative(0, -headerScrollY)
                        }
                    },
            ) {
                ArticleHeader(
                    article = article,
                    aiSummaryState = aiSummaryState,
                    onGenerateSummary = onGenerateSummary,
                    translationUi = null,
                    onRetranslate = onRetranslate,
                    onShowOriginal = onShowOriginal,
                    onTranslationDisplayChange = onTranslationDisplayChange,
                    onTitleMeasured = onTitleMeasured,
                )
            }
            BodyContent(
                article = article,
                isFetchingContent = isFetchingContent,
                translationActive = false,
                translationSegments = emptyList(),
                translationFallbackHtml = null,
                nativeNodes = nativeNodes,
                viewport = true,
                imageUrls = imageUrls,
                onHeaderScroll = onHeaderScroll,
                onImageClick = onImageClick,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.height(12.dp)) // 避让底部操作栏
        }
    } else {
        // 整页单滚动容器（用户反馈）：标题 / AI 摘要卡片随正文一起滚出，WebView 包内容高度
        Column(
            modifier = modifier
                .padding(vertical = 8.dp)
                .verticalScroll(scrollState),
        ) {
            ArticleHeader(
                article = article,
                aiSummaryState = aiSummaryState,
                onGenerateSummary = onGenerateSummary,
                translationUi = translationUi,
                onRetranslate = onRetranslate,
                onShowOriginal = onShowOriginal,
                onTranslationDisplayChange = onTranslationDisplayChange,
                onTitleMeasured = onTitleMeasured,
            )
            // 正文不完整提示（ADR-0012）：抓到了内容但没过完整性校验（过短/无段落/JS 渲染/
            // 付费墙）。如实告知，不假装这就是全文，也不静默给个空白页。
            if (article.article.contentIncomplete && translationUi == null) {
                IncompleteContentBanner(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            BodyContent(
                article = article,
                isFetchingContent = isFetchingContent,
                translationActive = translationUi != null,
                translationSegments = translationSegments,
                translationFallbackHtml = translationFallbackHtml,
                nativeNodes = nativeNodes,
                viewport = false,
                imageUrls = imageUrls,
                onHeaderScroll = onHeaderScroll,
                onImageClick = onImageClick,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp)) // 避让底部操作栏
        }
    }
}

/**
 * 正文槽位：译文/原文/无内容三分支的唯一实现（原视口与整页两份逐行重复的 when 已合并）。
 * [viewport] 只决定触摸与滚动的归属；宽度由内部 fillMaxWidth 统一。
 */
@Composable
private fun BodyContent(
    article: ArticleWithFeed,
    isFetchingContent: Boolean,
    /** 翻译激活（渐进中或已完成）。 */
    translationActive: Boolean,
    translationSegments: List<TranslationSegmentUi>,
    translationFallbackHtml: String?,
    nativeNodes: List<ReadingNode>,
    viewport: Boolean,
    imageUrls: List<String>,
    onHeaderScroll: (Int) -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    when {
        // 渐进/已完成的译文：原生分段渲染（渐进显示 + 双语对照，翻译功能 v2）
        translationActive && translationFallbackHtml == null ->
            TranslationReader(
                segments = translationSegments,                onLinkClick = { context.openUrl(it) },
                onImageClick = onImageClick,
                modifier = modifier.fillMaxWidth(),
            )
        // 译文分段解析全空的兜底：整页 WebView 显示已完成译文（或原文）
        translationFallbackHtml != null -> ArticleWebView(
            html = translationFallbackHtml,
            imageUrls = imageUrls,
            passThroughTouch = !viewport,
            onScroll = if (viewport) onHeaderScroll else null,
            onImageClick = onImageClick,
            modifier = modifier.fillMaxWidth(),
        )
        // 原生渲染器（ADR-0009）：中间树非空即走 Compose
        nativeNodes.isNotEmpty() -> {
            ArticleNativeReader(
                nodes = nativeNodes,
                onLinkClick = { context.openUrl(it) },
                onImageClick = onImageClick,
                modifier = modifier.fillMaxWidth(),
            )
        }
        article.article.content != null -> ArticleWebView(
            html = article.article.content!!,
            imageUrls = imageUrls,
            passThroughTouch = !viewport,
            onScroll = if (viewport) onHeaderScroll else null,
            onImageClick = onImageClick,
            modifier = modifier.fillMaxWidth(),
        )
        else -> NoContentBody(
            summary = article.article.summary,
            isFetchingContent = isFetchingContent,
            modifier = if (viewport) {
                modifier.verticalScroll(rememberScrollState())
            } else {
                Modifier.padding(horizontal = 20.dp)
            },
        )
    }
}

/** 详情页头部：源名行 + 标题 + AI 摘要卡 + 译文横幅。两种正文渲染模式共用。 */
@Composable
private fun ArticleHeader(
    article: ArticleWithFeed,
    aiSummaryState: AiSummaryState,
    onGenerateSummary: () -> Unit,
    /** 翻译过程态：Progressing / Shown 时显示译文横幅，其余 null 不显示。 */
    translationUi: TranslationState?,
    onRetranslate: () -> Unit,
    onShowOriginal: () -> Unit,
    onTranslationDisplayChange: (TranslationDisplayState) -> Unit,
    onTitleMeasured: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 20.dp),
    ) {
        FeedIcon(title = article.feedTitle, iconUrl = article.feedIconUrl, size = 22.dp, cornerRadius = 6.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = article.feedTitle,
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        Text("·", color = TextTertiary)
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatDate(article.article.publishedAt),
            color = TextTertiary,
            style = MaterialTheme.typography.labelMedium,
        )
        // 阅读时长：只有真实正文字数算出来的才显示。取不到就不显示，不虚构。
        article.article.readingMinutes?.let { minutes ->
            Spacer(Modifier.width(8.dp))
            Text("·", color = TextTertiary)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "阅读约 $minutes 分钟",
                color = TextTertiary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }

    // 压薄头部：标题用 titleLarge（比 headlineSmall 矮一档），间距收紧，减少固定占用
    Spacer(Modifier.height(10.dp))
    Text(
        text = article.article.title,
        color = TextPrimary,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            // 量出「标题完全滚出视口」的滚动量（标题 top + 高度，相对所在容器顶部）；
            // 整页模式 = 相对滚动内容，视口模式 = 相对折叠容器。滚动量达到该值顶栏补位标题。
            .onGloballyPositioned { coords ->
                onTitleMeasured(coords.positionInParent().y.roundToInt() + coords.size.height)
            },
    )

    Spacer(Modifier.height(12.dp))

    // AI 摘要卡片：仅在有摘要或生成失败（需告知原因并可重试）时显示；
    // 空态/生成中不放卡片（用户反馈：未生成时不要占阅读空间，生成入口移到顶栏 Sparkles）。
    val showAiSummaryCard = article.article.aiSummary != null || aiSummaryState is AiSummaryState.Failed
    if (showAiSummaryCard) {
        AiSummaryCard(
            summary = article.article.aiSummary,
            state = aiSummaryState,
            onGenerate = onGenerateSummary,
        )
        Spacer(Modifier.height(12.dp))
    }

    if (translationUi != null) {
        TranslationBanner(
            state = translationUi,
            onRetranslate = onRetranslate,
            onShowOriginal = onShowOriginal,
            onDisplayChange = onTranslationDisplayChange,
        )
        Spacer(Modifier.height(4.dp))
    }
}

/** 无正文分支的兜底提示：正文被判「不完整」时挂在头部下方（ADR-0012）。 */
@Composable
private fun IncompleteContentBanner(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Surface2,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Lucide.CircleAlert,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "正文可能不完整（站点限制或动态加载），可查看原文",
                color = TextTertiary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** 无正文分支：显示摘要；按需抓取中给出轻提示，失败静默（"查看原文"兜底）。 */
@Composable
private fun NoContentBody(
    summary: String?,
    isFetchingContent: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        BodyParagraph(text = summary ?: "本文没有可显示的正文，可查看原文。")
        if (isFetchingContent) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = Accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "正在获取全文…",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/**
 * AI 摘要常驻卡片（issue #44）：空态给生成按钮不藏功能；生成中转圈；
 * 有摘要显示内容；失败显示原因并可重试。空态/失败引导统一由 VM 给中文文案。
 */
@Composable
private fun AiSummaryCard(
    summary: String?,
    state: AiSummaryState,
    onGenerate: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Surface1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.Sparkles, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "AI 摘要",
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (state is AiSummaryState.Generating) {
                    CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                }
            }
            when {
                state is AiSummaryState.Generating -> {
                    Spacer(Modifier.height(8.dp))
                    Text("正在生成摘要…", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                }
                state is AiSummaryState.Failed -> {
                    Spacer(Modifier.height(8.dp))
                    Text(state.message, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onGenerate) {
                        Text(if (summary == null) "重试" else "重新生成", color = Accent, fontWeight = FontWeight.SemiBold)
                    }
                }
                summary != null -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = summary,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onGenerate) {
                        Text("生成摘要", color = Accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * 译文状态条（翻译功能 v2）：渐进中显示「翻译中 x/y 段」+ 转圈；完成后提供
 * 双语/纯译文切换、上下/左右排布切换（双语时）、重译与切回原文。
 * 显示偏好经 [onDisplayChange] 写回持久化 Store（用户级偏好，记住上次选择）。
 */
@Composable
private fun TranslationBanner(
    state: TranslationState,
    onRetranslate: () -> Unit,
    onShowOriginal: () -> Unit,
    onDisplayChange: (TranslationDisplayState) -> Unit,
) {
    val display = LocalTranslationDisplay.current
    val progressing = state is TranslationState.Progressing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Languages, contentDescription = null, tint = Accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (progressing) {
                "翻译中 ${(state as TranslationState.Progressing).doneCount}/${state.total} 段"
            } else {
                "AI 译文（DeepSeek）"
            },
            color = TextTertiary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        if (progressing) {
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        } else {
            TextButton(
                onClick = {
                    onDisplayChange(
                        display.copy(
                            viewMode = if (display.viewMode == TranslationViewMode.TRANSLATION_ONLY) {
                                TranslationViewMode.BILINGUAL
                            } else {
                                TranslationViewMode.TRANSLATION_ONLY
                            },
                        ),
                    )
                },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    text = if (display.viewMode == TranslationViewMode.TRANSLATION_ONLY) "双语" else "纯译文",
                    color = Accent,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (display.viewMode == TranslationViewMode.BILINGUAL) {
                TextButton(
                    onClick = {
                        onDisplayChange(
                            display.copy(
                                bilingualLayout = if (display.bilingualLayout == BilingualLayout.STACKED) {
                                    BilingualLayout.SIDE_BY_SIDE
                                } else {
                                    BilingualLayout.STACKED
                                },
                            ),
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        text = if (display.bilingualLayout == BilingualLayout.STACKED) "左右" else "上下",
                        color = Accent,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            TextButton(onClick = onRetranslate, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("重新翻译", color = Accent, style = MaterialTheme.typography.labelMedium)
            }
        }
        TextButton(onClick = onShowOriginal, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("切回原文", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * 净化后的正文 HTML 用 WebView 渲染：排版参数与主题色注入 CSS（issue #42）。
 * 模板构建在 [ReadingContentHtml]（纯函数，JVM 单测覆盖）；本组合函数只负责
 * 从 RssRadarPalette / LocalReadingStyle 读实时值。
 *
 * [passThroughTouch]：整页模式（高度包内容）为 true——触摸穿透给外层 Compose 滚动，
 * 否则 WebView 会吞掉滑动手势；视口模式（有图文章，内部滚动）为 false——
 * WebView 必须自己消费触摸才能滚动。
 *
 * [onScroll]：视口模式头部折叠用，回调 WebView 内部滚动量（px）。
 *
 * [imageUrls]：本文图片地址（[ReadingImages.extract] 的结果）。"点击放大"开启时，
 * build 阶段会把 <img> 包成指向自身的 <a class="img-link">，于是点击图片和点击链接
 * 走同一条 shouldOverrideUrlLoading 通道——地址命中本集合就交给 [onImageClick]
 * （全屏查看），否则照旧开浏览器。**全程不开 JS**（ADR-0007 不动，详见 ADR-0011）。
 */
@Composable
private fun ArticleWebView(
    html: String,
    imageUrls: List<String>,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    passThroughTouch: Boolean = true,
    onScroll: ((Int) -> Unit)? = null,
) {
    // 颜色读自 RssRadarPalette（getter 代理 mutableStateOf），主题切换自动重组
    val bg = toCssColor(BgRoot)
    val fg = toCssColor(TextPrimary)
    val muted = toCssColor(TextSecondary)
    val codeBg = toCssColor(Surface2)
    val border = toCssColor(Surface1)
    val link = toCssColor(Link)
    val style = LocalReadingStyle.current
    val image = LocalReadingImage.current
    // 关闭"点击放大"就传空集合：正文不包链接，图片点击在 WebView 里自然无反应。
    val linkedImages = if (image.maximizeOnTap) imageUrls.toSet() else emptySet()
    val styledHtml = remember(html, style, image, linkedImages, bg, fg, muted, codeBg, border, link) {
        ReadingContentHtml.build(
            contentHtml = html,
            style = style,
            bg = bg,
            fg = fg,
            muted = muted,
            codeBg = codeBg,
            border = border,
            link = link,
            imageUrls = linkedImages,
            imageCorners = image.cornerRadius,
        )
    }
    // factory 只跑一次，回调经 updated 引用保持最新
    val currentOnScroll by rememberUpdatedState(onScroll)
    val currentOnImageClick by rememberUpdatedState(onImageClick)
    val currentImageUrls by rememberUpdatedState(linkedImages)
    // 闪烁修复（用户反馈）：AndroidView 的 update 在每次父重组时都会跑，而 ArticleWebView
    // 的父（ReadingBody）会因顶栏 showTitle 翻转而重组 → 不加守卫就会每帧 reload 整页 HTML。
    // 用非 State 容器记住"已加载的 HTML 串"，只有内容真变才 reload。
    val lastLoaded = remember { arrayOf<String?>(null) }
    AndroidView(
        factory = { context ->
            object : WebView(context) {
                override fun onTouchEvent(event: MotionEvent): Boolean =
                    if (passThroughTouch) false else super.onTouchEvent(event)

                override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
                    super.onScrollChanged(l, t, oldl, oldt)
                    currentOnScroll?.invoke(t)
                }
            }.apply {
                settings.javaScriptEnabled = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                // 链接接管（视口模式生效；整页模式触摸穿透点不到，见 ADR-0007）：
                // 一律不进 WebView 导航，http(s) 外链交系统浏览器（与"查看原文"一致），
                // 其余 scheme 静默丢弃——顺带消灭"原地导航把正文顶掉"的默认行为。
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val url = request.url.toString()
                        if (url in currentImageUrls) {
                            currentOnImageClick(url)
                            return true
                        }
                        if (request.url.scheme == "http" || request.url.scheme == "https") {
                            context.openUrl(url)
                        }
                        return true
                    }
                }
            }
        },
        update = { webView ->
            if (lastLoaded[0] != styledHtml) {
                webView.loadDataWithBaseURL(null, styledHtml, "text/html", "utf-8", null)
                lastLoaded[0] = styledHtml
            }
        },
        modifier = modifier,
    )
}

/** Compose Color → CSS #RRGGBB。 */
private fun toCssColor(color: androidx.compose.ui.graphics.Color): String =
    "#%06X".format(color.toArgb() and 0xFFFFFF)

/** Store 层的纯 JVM 字体族枚举 → Compose FontFamily（摘要分支用）。 */
private fun ReadingFontFamily.toComposeFontFamily(): FontFamily = when (this) {
    ReadingFontFamily.SYSTEM -> FontFamily.Default
    ReadingFontFamily.SERIF -> FontFamily.Serif
    ReadingFontFamily.MONOSPACE -> FontFamily.Monospace
}

@Composable
private fun BodyParagraph(text: String) {
    val style = LocalReadingStyle.current
    Text(
        text = text,
        color = TextPrimary,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = style.fontSize.sp,
            lineHeight = (style.fontSize * style.lineHeight).sp,
            fontFamily = style.fontFamily.toComposeFontFamily(),
        ),
    )
}

private fun formatDate(ts: Long?): String =
    ts?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: "未知时间"
