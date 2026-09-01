package com.cycling.rssradar.ui.article

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.data.db.ArticleWithFeed
import com.cycling.rssradar.data.store.ReadingFontFamily
import com.cycling.rssradar.data.store.ReadingImageState
import com.cycling.rssradar.data.store.ReadingRenderer
import com.cycling.rssradar.data.store.ReadingStyleState
import com.cycling.rssradar.data.store.coerceFontSize
import com.cycling.rssradar.data.store.coerceImageCornerRadius
import com.cycling.rssradar.data.store.coerceLineHeight
import com.cycling.rssradar.data.store.coercePadding
import com.cycling.rssradar.ui.components.AppSnackbarHost
import com.cycling.rssradar.ui.components.shareArticle
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.BgRoot
import com.cycling.rssradar.ui.theme.LocalReadingStyle
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
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Type
import kotlin.math.roundToInt


/**
 * 全屏查看页的入参（issue #60）：本文图片列表 + 起始下标。
 * 刷新列表在读屏时惰性算一次，只在用户真点图时才付 jsoup 解析的代价。
 */
private data class ImageViewer(val images: List<String>, val index: Int)

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
    val renderer by viewModel.readingRenderer.collectAsState()
    val imagePrefs by viewModel.readingImage.collectAsState()
    val linkShare by viewModel.linkShare.collectAsState()
    // 分享文章（#26）需要 Context 起系统分享面板
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showStyleSheet by remember { mutableStateOf(false) }
    // 全屏图片查看（issue #60）：瞬时 UI，不入路由、不占 back 栈
    var imageViewer by remember { mutableStateOf<ImageViewer?>(null) }
    // 整页滚动状态提升到 Screen：顶栏标题「滚出视口才出现」需要读滚动量
    val scrollState = rememberScrollState()
    // 视口模式（有图文章）的头部折叠量 = WebView 内部滚动量，同样驱动顶栏补位标题
    var headerScrollY by remember { mutableStateOf(0) }
    // 标题完全滚出视口所需的滚动量（标题 top + 高度，onGloballyPositioned 量出）。
    // 初值 Int.MAX_VALUE = 未量出前顶栏不显标题。
    var titleHideOffset by remember { mutableStateOf(Int.MAX_VALUE) }
    LaunchedEffect(articleId) {
        viewModel.load(articleId)
        scrollState.scrollTo(0)
        headerScrollY = 0
        titleHideOffset = Int.MAX_VALUE
        imageViewer = null
    }
    // 翻译失败走 Snackbar（spec #44：正文保持原文，报错可重试）；按状态实例触发，不会重复弹
    LaunchedEffect(translationState) {
        if (translationState is TranslationState.Failed) {
            snackbarHostState.showSnackbar((translationState as TranslationState.Failed).message)
        }
    }

    Scaffold(
        containerColor = BgRoot,
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            ArticleDetailTopBar(
                title = article?.article?.title,
                // 两种模式任一把标题滚出视口都补位显示
                showTitle = scrollState.value >= titleHideOffset ||
                    headerScrollY >= titleHideOffset,
                onBack = onBack,
                onOpenStyle = { showStyleSheet = true },
                onShare = {
                    article?.let { item ->
                        context.shareArticle(
                            title = item.article.title,
                            link = item.article.link,
                            summary = item.article.summary,
                            state = linkShare,
                        )
                    }
                },
                onToggleTranslation = { viewModel.onIntent(ArticleDetailIntent.ToggleTranslation) },
                isShowingTranslation = translationState is TranslationState.Shown ||
                    translationState is TranslationState.Progressing,
                isGeneratingTranslation = translationState is TranslationState.Progressing,
                aiSummary = article?.article?.aiSummary,
                aiSummaryState = aiSummaryState,
                onGenerateSummary = { viewModel.onIntent(ArticleDetailIntent.GenerateSummary) },
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
        ReadingBody(
            article = current,
            isFetchingContent = isFetchingContent,
            aiSummaryState = aiSummaryState,
            translationState = translationState,
            scrollState = scrollState,
            headerScrollY = headerScrollY,
            onHeaderScroll = { headerScrollY = it },
            onTitleMeasured = { titleHideOffset = it },
            onGenerateSummary = { viewModel.onIntent(ArticleDetailIntent.GenerateSummary) },
            onRetranslate = { viewModel.onIntent(ArticleDetailIntent.RetranslateArticle) },
            onShowOriginal = { viewModel.onIntent(ArticleDetailIntent.ToggleTranslation) },
            onTranslationDisplayChange = viewModel::updateTranslationDisplay,
            onImageClick = { url -> imageViewer = openImageViewer(current, url) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }

    imageViewer?.let { viewer ->
        ReaderImagePage(
            images = viewer.images,
            initialIndex = viewer.index,
            onDismiss = { imageViewer = null },
        )
    }

    if (showStyleSheet) {
        ReadingStyleSheet(
            renderer = renderer.renderer,
            image = imagePrefs,
            onRenderer = { viewModel.setRenderer(it) },
            onFontSize = { v -> viewModel.updateReadingStyle { it.copy(fontSize = v) } },
            onLineHeight = { v -> viewModel.updateReadingStyle { it.copy(lineHeight = v) } },
            onPadding = { v -> viewModel.updateReadingStyle { it.copy(horizontalPadding = v) } },
            onFontFamily = { v -> viewModel.updateReadingStyle { it.copy(fontFamily = v) } },
            onImageCornerRadius = { v -> viewModel.updateReadingImage { it.copy(cornerRadius = v) } },
            onImageMaximize = { v -> viewModel.updateReadingImage { it.copy(maximizeOnTap = v) } },
            onDismiss = { showStyleSheet = false },
        )
    }
}

/**
 * 点图 → 全屏查看：现提取本文图片列表（jsoup 解析，只在点击时付代价）并定位下标。
 * 提取不到（例如地址来自 srcset、被 sanitize 改过）时退化成"只看这一张"。
 */
private fun openImageViewer(article: ArticleWithFeed, url: String): ImageViewer {
    val images = ReadingImages.extract(article.article.content.orEmpty())
    return if (url in images) {
        ImageViewer(images, images.indexOf(url))
    } else {
        ImageViewer(listOf(url), 0)
    }
}

@Composable
private fun ArticleDetailTopBar(
    title: String?,
    showTitle: Boolean,
    onBack: () -> Unit,
    onOpenStyle: () -> Unit,
    /** 分享本文（#26）：内容格式由「我的」页偏好决定。 */
    onShare: () -> Unit,
    onToggleTranslation: () -> Unit,
    isShowingTranslation: Boolean,
    isGeneratingTranslation: Boolean,
    aiSummary: String?,
    aiSummaryState: AiSummaryState,
    onGenerateSummary: () -> Unit,
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
        // AI 摘要生成入口（用户反馈）：未生成/生成中在顶栏给 Sparkles 或转圈，不在正文占位卡片；
        // 有摘要且空闲时隐藏（卡片里已显示内容）。生成中转圈禁用，失败态保持可点重生成。
        if (aiSummaryState is AiSummaryState.Generating ||
            aiSummaryState is AiSummaryState.Failed ||
            aiSummary == null
        ) {
            if (aiSummaryState is AiSummaryState.Generating) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                IconButton(onClick = onGenerateSummary) {
                    Icon(
                        Lucide.Sparkles,
                        contentDescription = "生成 AI 摘要",
                        tint = if (aiSummaryState is AiSummaryState.Failed) Accent else TextPrimary,
                    )
                }
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
        // 分享（#26）
        IconButton(onClick = onShare) {
            Icon(Lucide.Share2, contentDescription = "分享", tint = TextPrimary)
        }
        // 排版设置入口（issue #42）
        IconButton(onClick = onOpenStyle) {
            Icon(Lucide.Type, contentDescription = "排版设置", tint = TextPrimary)
        }
    }
}

/**
 * 排版设置弹层（issue #42）：字号步进、行距/边距滑杆、字体族三选一。
 * 显示值读 LocalReadingStyle，写入经 VM 直达 ReadingStyleStore，无确认按钮即改即见。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingStyleSheet(
    renderer: ReadingRenderer,
    image: ReadingImageState,
    onRenderer: (ReadingRenderer) -> Unit,
    onFontSize: (Int) -> Unit,
    onLineHeight: (Float) -> Unit,
    onPadding: (Int) -> Unit,
    onFontFamily: (ReadingFontFamily) -> Unit,
    onImageCornerRadius: (Int) -> Unit,
    onImageMaximize: (Boolean) -> Unit,
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

            // 正文渲染器：WebView / 原生 Compose 二选一（ADR-0009）。
            // 原生路对表格/视频/内联样式退化，仅建议被 WebView 滚动闪烁困扰时启用。
            Text(
                text = "正文渲染器",
                color = TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingRenderer.entries.forEach { r ->
                    val selected = r == renderer
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (selected) Accent else Surface2,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onRenderer(r) },
                    ) {
                        Text(
                            text = r.label,
                            color = if (selected) OnAccent else TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

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
            Spacer(Modifier.height(12.dp))

            // 图片（issue #60）：圆角直接改 CSS/Compose 形状；点击放大关掉后，
            // 正文不再把 <img> 包成链接，点图在 WebView 里自然无反应。
            Text(
                text = "图片",
                color = TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "圆角",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.width(72.dp),
                )
                Slider(
                    value = image.cornerRadius.toFloat(),
                    onValueChange = { onImageCornerRadius(coerceImageCornerRadius(it.roundToInt())) },
                    valueRange = ReadingImageState.CORNER_RADIUS_MIN.toFloat()..
                        ReadingImageState.CORNER_RADIUS_MAX.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${image.cornerRadius}dp",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "点击放大",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = image.maximizeOnTap,
                    onCheckedChange = onImageMaximize,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnAccent,
                        checkedTrackColor = Accent,
                    ),
                )
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
