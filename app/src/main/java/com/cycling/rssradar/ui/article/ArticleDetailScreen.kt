package com.cycling.rssradar.ui.article

import android.text.format.DateUtils
import android.view.MotionEvent
import android.webkit.WebView
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cycling.rssradar.LocalReadingStyle
import com.cycling.rssradar.data.ai.AiRepository
import com.cycling.rssradar.data.db.ArticleWithFeed
import com.cycling.rssradar.data.store.ReadingFontFamily
import com.cycling.rssradar.data.store.ReadingStyleState
import com.cycling.rssradar.data.store.coerceFontSize
import com.cycling.rssradar.data.store.coerceLineHeight
import com.cycling.rssradar.data.store.coercePadding
import com.cycling.rssradar.ui.components.FeedIcon
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.BgRoot
import com.cycling.rssradar.ui.theme.Link
import com.cycling.rssradar.ui.theme.OnAccent
import com.cycling.rssradar.ui.theme.Surface1
import com.cycling.rssradar.ui.theme.Surface2
import com.cycling.rssradar.ui.theme.TextPrimary
import com.cycling.rssradar.ui.theme.TextSecondary
import com.cycling.rssradar.ui.theme.TextTertiary
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Type
import kotlin.math.roundToInt


@Composable
fun ArticleDetailScreen(
    viewModel: ArticleDetailViewModel,
    articleId: Long,
    onBack: () -> Unit,
    onOpenOriginal: (String) -> Unit = {},
) {
    val article by viewModel.article.collectAsState()
    val isFetchingContent by viewModel.isFetchingContent.collectAsState()
    val aiSummaryState by viewModel.aiSummaryState.collectAsState()
    val translationState by viewModel.translationState.collectAsState()
    val neighbors by viewModel.neighbors.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showStyleSheet by remember { mutableStateOf(false) }
    // 整页滚动状态提升到 Screen：顶栏标题「滚出视口才出现」需要读滚动量
    val scrollState = rememberScrollState()
    // 标题完全滚出视口所需的滚动量（标题 top + 高度，onGloballyPositioned 量出）。
    // 初值 Int.MAX_VALUE = 未量出前顶栏不显标题。
    var titleHideOffset by remember { mutableStateOf(Int.MAX_VALUE) }
    LaunchedEffect(articleId) {
        viewModel.load(articleId)
        scrollState.scrollTo(0)
        titleHideOffset = Int.MAX_VALUE
    }
    // 翻译失败走 Snackbar（spec #44：正文保持原文，报错可重试）；按状态实例触发，不会重复弹
    LaunchedEffect(translationState) {
        if (translationState is TranslationState.Failed) {
            snackbarHostState.showSnackbar((translationState as TranslationState.Failed).message)
        }
    }

    Scaffold(
        containerColor = BgRoot,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ArticleDetailTopBar(
                title = article?.article?.title,
                showTitle = scrollState.value >= titleHideOffset,
                onBack = onBack,
                onOpenStyle = { showStyleSheet = true },
                onToggleTranslation = { viewModel.onIntent(ArticleDetailIntent.ToggleTranslation) },
                isShowingTranslation = translationState is TranslationState.Shown,
                isGeneratingTranslation = translationState is TranslationState.Generating,
            )
        },
        bottomBar = {
            article?.let { item ->
                ArticleActionsBar(
                    isStarred = item.article.isStarred,
                    isBookmarked = item.article.isBookmarked,
                    hasPrev = neighbors.prevId != null,
                    hasNext = neighbors.nextId != null,
                    onPrev = { neighbors.prevId?.let(viewModel::load) },
                    onNext = { neighbors.nextId?.let(viewModel::load) },
                    onStar = { viewModel.onIntent(ArticleDetailIntent.ToggleStarred) },
                    onBookmark = { viewModel.onIntent(ArticleDetailIntent.ToggleBookmarked) },
                    onOpenOriginal = { onOpenOriginal(item.article.link) },
                )
            }
        },
    ) { padding ->
        val current = article
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("文章不存在", color = TextSecondary)
            }
            return@Scaffold
        }
        ArticleDetailContent(
            article = current,
            isFetchingContent = isFetchingContent,
            aiSummaryState = aiSummaryState,
            translationState = translationState,
            scrollState = scrollState,
            onTitleMeasured = { titleHideOffset = it },
            onGenerateSummary = { viewModel.onIntent(ArticleDetailIntent.GenerateSummary) },
            onRetranslate = { viewModel.onIntent(ArticleDetailIntent.RetranslateArticle) },
            onShowOriginal = { viewModel.onIntent(ArticleDetailIntent.ToggleTranslation) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }

    if (showStyleSheet) {
        ReadingStyleSheet(
            onFontSize = { v -> viewModel.updateReadingStyle { it.copy(fontSize = v) } },
            onLineHeight = { v -> viewModel.updateReadingStyle { it.copy(lineHeight = v) } },
            onPadding = { v -> viewModel.updateReadingStyle { it.copy(horizontalPadding = v) } },
            onFontFamily = { v -> viewModel.updateReadingStyle { it.copy(fontFamily = v) } },
            onDismiss = { showStyleSheet = false },
        )
    }
}

@Composable
private fun ArticleDetailTopBar(
    title: String?,
    showTitle: Boolean,
    onBack: () -> Unit,
    onOpenStyle: () -> Unit,
    onToggleTranslation: () -> Unit,
    isShowingTranslation: Boolean,
    isGeneratingTranslation: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Lucide.ArrowLeft, contentDescription = "返回", tint = TextPrimary)
        }
        // 标题滚出视口后顶栏补位显示（用户反馈）；阅读中隐藏，不占阅读注意力
        Box(modifier = Modifier.weight(1f)) {
            if (showTitle && title != null) {
                Text(
                    text = title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }
        // AI 翻译开关（issue #44）：未显示译文时发起翻译，显示中切回原文；生成中禁用
        IconButton(onClick = onToggleTranslation, enabled = !isGeneratingTranslation) {
            Icon(
                Lucide.Languages,
                contentDescription = if (isShowingTranslation) "切回原文" else "AI 翻译",
                tint = if (isShowingTranslation || isGeneratingTranslation) Accent else TextPrimary,
            )
        }
        // 排版设置入口（issue #42）
        IconButton(onClick = onOpenStyle) {
            Icon(Lucide.Type, contentDescription = "排版设置", tint = TextPrimary)
        }
    }
}

@Composable
private fun ArticleDetailContent(
    article: ArticleWithFeed,
    isFetchingContent: Boolean,
    aiSummaryState: AiSummaryState,
    translationState: TranslationState,
    scrollState: ScrollState,
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
    // 混合模式：有图 → 视口渲染（头部固定、WebView 内部滚动、触摸正常）；
    // 纯文字 → 整页渲染（头部随正文滚出的体验保留），文字栅格内存可控。
    val hasImages = bodyHtml?.contains("<img", ignoreCase = true) == true

    if (hasImages) {
        Column(modifier = modifier.padding(vertical = 8.dp)) {
            ArticleHeader(
                article = article,
                aiSummaryState = aiSummaryState,
                onGenerateSummary = onGenerateSummary,
                shownTranslation = shownTranslation,
                onRetranslate = onRetranslate,
                onShowOriginal = onShowOriginal,
                onTitleMeasured = onTitleMeasured,
            )
            when {
                translationState is TranslationState.Generating ->
                    TranslatingPlaceholder(Modifier.fillMaxWidth().weight(1f))
                shownTranslation != null -> ArticleWebView(
                    html = shownTranslation.html,
                    passThroughTouch = false,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                article.article.content != null -> ArticleWebView(
                    html = article.article.content!!,
                    passThroughTouch = false,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                else -> NoContentBody(
                    summary = article.article.summary,
                    isFetchingContent = isFetchingContent,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                )
            }
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
            when {
                translationState is TranslationState.Generating ->
                    TranslatingPlaceholder(Modifier.fillMaxWidth().height(180.dp))
                shownTranslation != null -> ArticleWebView(
                    html = shownTranslation.html,
                    modifier = Modifier.fillMaxWidth(),
                )
                article.article.content != null -> ArticleWebView(
                    html = article.article.content!!,
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> NoContentBody(
                    summary = article.article.summary,
                    isFetchingContent = isFetchingContent,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            Spacer(Modifier.height(12.dp)) // 避让底部操作栏
        }
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
            // 量出「标题完全滚出视口」的滚动量（标题 top + 高度，相对滚动内容顶部）；
            // 视口模式下页面不滚，此值不会被触发，无害
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
 */
@Composable
private fun ArticleWebView(
    html: String,
    modifier: Modifier = Modifier,
    passThroughTouch: Boolean = true,
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
    AndroidView(
        factory = { context ->
            object : WebView(context) {
                override fun onTouchEvent(event: MotionEvent): Boolean =
                    if (passThroughTouch) false else super.onTouchEvent(event)
            }.apply {
                settings.javaScriptEnabled = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
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

/**
 * 排版设置弹层（issue #42）：字号步进、行距/边距滑杆、字体族三选一。
 * 显示值读 LocalReadingStyle，写入经 VM 直达 ReadingStyleStore，无确认按钮即改即见。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingStyleSheet(
    onFontSize: (Int) -> Unit,
    onLineHeight: (Float) -> Unit,
    onPadding: (Int) -> Unit,
    onFontFamily: (ReadingFontFamily) -> Unit,
    onDismiss: () -> Unit,
) {
    val style = LocalReadingStyle.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                text = "排版设置",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))

            // 字号：步进
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "字号",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onFontSize(coerceFontSize(style.fontSize - 1)) }) {
                    Icon(Lucide.Minus, contentDescription = "减小字号", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = "${style.fontSize}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(40.dp),
                )
                IconButton(onClick = { onFontSize(coerceFontSize(style.fontSize + 1)) }) {
                    Icon(Lucide.Plus, contentDescription = "增大字号", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }
            }

            // 行距：滑杆（0.8–2.5）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "行距",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.width(72.dp),
                )
                Slider(
                    value = style.lineHeight,
                    onValueChange = { onLineHeight(coerceLineHeight(it)) },
                    valueRange = ReadingStyleState.LINE_HEIGHT_MIN..ReadingStyleState.LINE_HEIGHT_MAX,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "%.1f".format(style.lineHeight),
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp),
                )
            }

            // 边距：滑杆（0–48dp）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "边距",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.width(72.dp),
                )
                Slider(
                    value = style.horizontalPadding.toFloat(),
                    onValueChange = { onPadding(coercePadding(it.roundToInt())) },
                    valueRange = ReadingStyleState.PADDING_MIN.toFloat()..ReadingStyleState.PADDING_MAX.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${style.horizontalPadding}dp",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            // 字体族：三选一
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingFontFamily.entries.forEach { family ->
                    val selected = family == style.fontFamily
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (selected) Accent else Surface2,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = { onFontFamily(family) }),
                    ) {
                        Text(
                            text = family.label,
                            color = if (selected) OnAccent else TextPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleActionsBar(
    isStarred: Boolean,
    isBookmarked: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onStar: () -> Unit,
    onBookmark: () -> Unit,
    onOpenOriginal: () -> Unit,
) {
    val insets = WindowInsets.navigationBars.asPaddingValues()
    Surface(color = BgRoot) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp + insets.calculateBottomPadding()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 上一篇 = 发布更早（列表序更靠后），下一篇 = 更新一篇
            ActionIcon(
                icon = Lucide.ChevronLeft,
                checked = false,
                contentDescription = "上一篇",
                enabled = hasPrev,
                size = 40.dp,
                onClick = onPrev,
            )
            Spacer(Modifier.width(6.dp))
            ActionIcon(
                icon = Lucide.ChevronRight,
                checked = false,
                contentDescription = "下一篇",
                enabled = hasNext,
                size = 40.dp,
                onClick = onNext,
            )
            Spacer(Modifier.width(10.dp))
            ActionIcon(icon = Lucide.Star, checked = isStarred, contentDescription = "收藏", size = 40.dp, onClick = onStar)
            Spacer(Modifier.width(6.dp))
            ActionIcon(icon = Lucide.Bookmark, checked = isBookmarked, contentDescription = "稍后读", size = 40.dp, onClick = onBookmark)
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onOpenOriginal,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = OnAccent,
                ),
            ) {
                Icon(
                    Lucide.ExternalLink,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "查看原文",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 48.dp,
) {
    val bg = if (checked) Accent else Surface2
    val fg = when {
        checked -> OnAccent
        !enabled -> TextTertiary
        else -> TextPrimary
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg.copy(alpha = if (enabled || checked) 1f else 0.5f),
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick, enabled = enabled),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = fg,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun formatDate(ts: Long?): String =
    ts?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: "未知时间"
