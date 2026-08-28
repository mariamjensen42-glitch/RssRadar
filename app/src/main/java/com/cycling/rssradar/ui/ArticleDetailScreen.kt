package com.cycling.rssradar.ui

import android.text.format.DateUtils
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.data.ArticleWithFeed
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

@Composable
fun ArticleDetailScreen(
    viewModel: ArticleDetailViewModel,
    articleId: Long,
    onBack: () -> Unit,
    onOpenOriginal: (String) -> Unit = {},
) {
    val article by viewModel.article.collectAsState()
    LaunchedEffect(articleId) { viewModel.load(articleId) }

    Scaffold(
        containerColor = BgRoot,
        topBar = { ArticleDetailTopBar(onBack = onBack) },
        bottomBar = {
            article?.let { item ->
                ArticleActionsBar(
                    isStarred = item.article.isStarred,
                    isBookmarked = item.article.isBookmarked,
                    onStar = { viewModel.toggleStarred() },
                    onBookmark = { viewModel.toggleBookmarked() },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
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
private fun ArticleDetailContent(article: ArticleWithFeed, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FeedIcon(title = article.feedTitle, size = 24.dp, cornerRadius = 6.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = article.feedTitle,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text("·", color = TextTertiary)
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatDate(article.article.publishedAt),
                color = TextTertiary,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.width(8.dp))
            Text("·", color = TextTertiary)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "阅读约 ${article.article.readingMinutes ?: estimateReadingMinutes(article.article.summary)} 分钟",
                color = TextTertiary,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = article.article.title,
            color = TextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(16.dp))
        BodyParagraph(article.article.summary ?: "正文内容")

        if (article.article.coverUrl != null) {
            Spacer(Modifier.height(20.dp))
            ArticleCoverPlaceholder()
        }

        Spacer(Modifier.height(20.dp))
        BodyParagraph(article.article.summary ?: "本文转自 ${article.feedTitle}。")

        Spacer(Modifier.height(120.dp)) // 避让底部操作栏
    }
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
private fun ArticleCoverPlaceholder() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Surface1,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("文章配图", color = TextTertiary, style = MaterialTheme.typography.titleMedium)
        }
    }
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

private fun estimateReadingMinutes(summary: String?): Int {
    val len = summary?.length ?: 0
    return ((len / 200) + 1).coerceAtLeast(1)
}
