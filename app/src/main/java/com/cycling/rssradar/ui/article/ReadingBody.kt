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
import com.cycling.rssradar.data.store.ReadingFontFamily
import com.cycling.rssradar.data.store.ReadingStyleState
import com.cycling.rssradar.ui.components.FeedIcon
import com.cycling.rssradar.ui.components.openUrl
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.BgRoot
import com.cycling.rssradar.ui.theme.Link
import com.cycling.rssradar.ui.theme.LocalReadingStyle
import com.cycling.rssradar.ui.theme.OnAccent
import com.cycling.rssradar.ui.theme.Surface1
import com.cycling.rssradar.ui.theme.Surface2
import com.cycling.rssradar.ui.theme.TextPrimary
import com.cycling.rssradar.ui.theme.TextSecondary
import com.cycling.rssradar.ui.theme.TextTertiary
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
    modifier: Modifier = Modifier,
) {
    // 水平边距不放在外层：正文 WebView 的边距由排版设置的 CSS padding 控制（issue #42），
    // 头部（源名/标题）保持固定 20dp 不随排版项变化。
    val shownTranslation = translationState as? TranslationState.Shown
    val bodyHtml: String? = when {
        shownTranslation != null -> shownTranslation.html
        article.article.content != null -> article.article.content
        else -> null
    }
    // OOM 防线（闪退诊断）：整页包高的 WebView 会被 Chromium 视为全部内容可见，
    // 有图文章的所有图片同时解码进 Java 堆，图多必 OOM（256MB 堆几十秒吃满）。
    // 混合模式：有图 → 视口渲染；纯文字 → 整页渲染，文字栅格内存可控。
    val viewport = bodyHtml?.contains("<img", ignoreCase = true) == true

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
                    shownTranslation = shownTranslation,
                    onRetranslate = onRetranslate,
                    onShowOriginal = onShowOriginal,
                    onTitleMeasured = onTitleMeasured,
                )
            }
            BodyContent(
                article = article,
                isFetchingContent = isFetchingContent,
                shownTranslation = shownTranslation,
                translationState = translationState,
                viewport = true,
                onHeaderScroll = onHeaderScroll,
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
                shownTranslation = shownTranslation,
                onRetranslate = onRetranslate,
                onShowOriginal = onShowOriginal,
                onTitleMeasured = onTitleMeasured,
            )
            BodyContent(
                article = article,
                isFetchingContent = isFetchingContent,
                shownTranslation = shownTranslation,
                translationState = translationState,
                viewport = false,
                onHeaderScroll = onHeaderScroll,
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
    shownTranslation: TranslationState.Shown?,
    translationState: TranslationState,
    viewport: Boolean,
    onHeaderScroll: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        translationState is TranslationState.Generating ->
            TranslatingPlaceholder(modifier)
        shownTranslation != null -> ArticleWebView(
            html = shownTranslation.html,
            passThroughTouch = !viewport,
            onScroll = if (viewport) onHeaderScroll else null,
            modifier = modifier.fillMaxWidth(),
        )
        article.article.content != null -> ArticleWebView(
            html = article.article.content!!,
            passThroughTouch = !viewport,
            onScroll = if (viewport) onHeaderScroll else null,
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
    shownTranslation: TranslationState.Shown?,
    onRetranslate: () -> Unit,
    onShowOriginal: () -> Unit,
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

    // AI 摘要常驻卡片（issue #44，ADR-0005）：无摘要给按钮，生成中 loading，有摘要显示内容
    AiSummaryCard(
        summary = article.article.aiSummary,
        state = aiSummaryState,
        onGenerate = onGenerateSummary,
    )
    Spacer(Modifier.height(12.dp))

    if (shownTranslation != null) {
        TranslationBanner(
            onRetranslate = onRetranslate,
            onShowOriginal = onShowOriginal,
        )
        Spacer(Modifier.height(4.dp))
    }
}

/** 翻译生成中的占位。视口模式撑满剩余空间，整页模式固定高度居中。 */
@Composable
private fun TranslatingPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            Spacer(Modifier.height(10.dp))
            Text("正在翻译…", color = TextTertiary, style = MaterialTheme.typography.labelMedium)
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

/** 译文状态条（issue #44）：标明当前显示 AI 译文，提供重译与切回原文。 */
@Composable
private fun TranslationBanner(
    onRetranslate: () -> Unit,
    onShowOriginal: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Languages, contentDescription = null, tint = Accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = "AI 译文（DeepSeek）",
            color = TextTertiary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetranslate) {
            Text("重新翻译", color = Accent, style = MaterialTheme.typography.labelMedium)
        }
        TextButton(onClick = onShowOriginal) {
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
 */
@Composable
private fun ArticleWebView(
    html: String,
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
    val styledHtml = remember(html, style, bg, fg, muted, codeBg, border, link) {
        ReadingContentHtml.build(html, style, bg, fg, muted, codeBg, border, link)
    }
    // factory 只跑一次，回调经 updated 引用保持最新
    val currentOnScroll by rememberUpdatedState(onScroll)
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
                        if (request.url.scheme == "http" || request.url.scheme == "https") {
                            context.openUrl(request.url.toString())
                        }
                        return true
                    }
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, styledHtml, "text/html", "utf-8", null)
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
