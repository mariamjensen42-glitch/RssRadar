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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.data.store.ReadingFontFamily
import com.cycling.rssradar.data.store.ReadingStyleState
import com.cycling.rssradar.data.store.coerceFontSize
import com.cycling.rssradar.data.store.coerceLineHeight
import com.cycling.rssradar.data.store.coercePadding
import com.cycling.rssradar.ui.components.AppSnackbarHost
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
