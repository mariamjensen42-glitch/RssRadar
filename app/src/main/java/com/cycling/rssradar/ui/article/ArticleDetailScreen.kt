package com.cycling.rssradar.ui.article

import android.text.format.DateUtils
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.SubcomposeAsyncImage
import com.cycling.rssradar.LocalDarkTheme
import com.cycling.rssradar.data.db.ArticleWithFeed
import com.cycling.rssradar.ui.components.FeedIcon
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.BgRoot
import com.cycling.rssradar.ui.theme.OnAccent
import com.cycling.rssradar.ui.theme.Surface1
import com.cycling.rssradar.ui.theme.Surface2
import com.cycling.rssradar.ui.theme.TextPrimary
import com.cycling.rssradar.ui.theme.TextSecondary
import com.cycling.rssradar.ui.theme.TextTertiary
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Star

import com.cycling.rssradar.ui.subscriptions.String

@Composable
fun ArticleDetailScreen(
    viewModel: ArticleDetailViewModel,
    articleId: Long,
    onBack: () -> Unit,
    onOpenOriginal: (String) -> Unit = {},
) {
    val article by viewModel.article.collectAsState()
    val isFetchingContent by viewModel.isFetchingContent.collectAsState()
    LaunchedEffect(articleId) { viewModel.load(articleId) }

    Scaffold(
        containerColor = BgRoot,
        topBar = { ArticleDetailTopBar(onBack = onBack) },
        bottomBar = {
            article?.let { item ->
                ArticleActionsBar(
                    isStarred = item.article.isStarred,
                    isBookmarked = item.article.isBookmarked,
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
private fun ArticleDetailTopBar(onBack: () -> Unit) {
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
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { /* TODO: 更多菜单 */ }) {
            Icon(Lucide.EllipsisVertical, contentDescription = "更多", tint = TextPrimary)
        }
    }
}

@Composable
private fun ArticleDetailContent(
    article: ArticleWithFeed,
    isFetchingContent: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FeedIcon(title = article.feedTitle, size = 22.dp, cornerRadius = 6.dp)
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
        )

        // 封面：取到了且为有效地址才显示真图；否则整块不渲染（不留空白占位）
        article.article.coverUrl?.takeIf { it.isNotBlank() }?.let { url ->
            Spacer(Modifier.height(12.dp))
            ArticleCoverImage(url = url)
        }

        Spacer(Modifier.height(12.dp))
        when {
            // feed 自带或已抓取的正文：WebView 渲染净化 HTML（内部滚动，模板注入深色主题）
            article.article.content != null -> ArticleWebView(
                html = article.article.content!!,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            // 无正文：显示摘要；按需抓取中给出轻提示，失败静默（"查看原文"兜底）
            else -> Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                BodyParagraph(text = article.article.summary ?: "本文没有可显示的正文，可查看原文。")
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
        Spacer(Modifier.height(12.dp)) // 避让底部操作栏
    }
}

/** 净化后的正文 HTML 用 WebView 渲染：模板注入主题样式，与全局一致。 */
@Composable
private fun ArticleWebView(html: String, modifier: Modifier = Modifier) {
    // 用主题宿主注入的实际深色状态（跟随系统或用户强制），不是系统值
    val darkTheme = LocalDarkTheme.current
    val styledHtml = remember(html, darkTheme) { buildStyledContentHtml(html, darkTheme) }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
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

/**
 * 渲染模板。正文 HTML 在解析层已经净化（去 script/style/iframe/事件属性，见 RssParser），
 * 这里再包一层静态 CSS：深色黑底白字 / 浅色白底黑字，图片限宽，链接用主题紫。
 */
private fun buildStyledContentHtml(contentHtml: String, darkTheme: Boolean): String {
    val bg: String
    val fg: String
    val muted: String
    val codeBg: String
    val border: String
    val link: String
    if (darkTheme) {
        bg = "#000000"; fg = "#FFFFFF"; muted = "#B0B0B6"; codeBg = "#1C1C1E"; border = "#3A3A3C"; link = "#9B9CFF"
    } else {
        bg = "#FFFFFF"; fg = "#1A1A1E"; muted = "#55555C"; codeBg = "#F0F0F4"; border = "#D9D9E0"; link = "#5B5BD6"
    }
    return """
    <!DOCTYPE html>
    <html><head><meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        body { background:$bg; color:$fg; font-size:16px; line-height:1.7;
               font-family:-apple-system,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;
               margin:0; padding:0; word-break:break-word; }
        img { max-width:100%; height:auto; border-radius:8px; }
        a { color:$link; text-decoration:none; }
        p { margin:0 0 1em 0; }
        blockquote { margin:0 0 1em 0; padding:4px 12px; border-left:3px solid $border; color:$muted; }
        pre { background:$codeBg; padding:10px; border-radius:8px; overflow-x:auto; }
        code { font-family:Menlo,Consolas,monospace; font-size:13px; }
        h1,h2,h3 { line-height:1.4; }
        figure { margin:0 0 1em 0; }
    </style></head>
    <body>$contentHtml</body></html>
""".trimIndent()
}

@Composable
private fun ArticleCoverImage(url: String) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = "文章封面",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp)),
        // 加载中 / 失败：画可见的 Surface2 占位，避免透明空洞（Coil 3 默认加载态不绘制）
        loading = { Box(Modifier.fillMaxSize().background(Surface2)) },
        error = { Box(Modifier.fillMaxSize().background(Surface2)) },
    )
}

@Composable
private fun BodyParagraph(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun ArticleActionsBar(
    isStarred: Boolean,
    isBookmarked: Boolean,
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
            ActionIcon(icon = Lucide.Star, checked = isStarred, contentDescription = "收藏", onClick = onStar)
            Spacer(Modifier.width(8.dp))
            ActionIcon(icon = Lucide.Bookmark, checked = isBookmarked, contentDescription = "稍后读", onClick = onBookmark)
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onOpenOriginal,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
) {
    val bg = if (checked) Accent else Surface2
    val fg = if (checked) OnAccent else TextPrimary
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
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
