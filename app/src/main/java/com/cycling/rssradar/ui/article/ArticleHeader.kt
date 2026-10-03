package com.cycling.rssradar.ui.article

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Sparkles
import com.cycling.rssradar.R
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.core.model.TranslationDisplayState
import com.cycling.rssradar.core.ui.components.FeedIcon
import com.cycling.rssradar.core.ui.theme.radarColors
import kotlin.math.roundToInt

/** 详情页头部：源名行 + 标题 + AI 摘要卡 + 译文横幅。两种正文渲染模式共用。 */
@Composable
internal fun ArticleHeader(
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
            color = radarColors().textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        Text("·", color = radarColors().textTertiary)
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatDate(article.article.publishedAt),
            color = radarColors().textTertiary,
            style = MaterialTheme.typography.labelMedium,
        )
        // 阅读时长：只有真实正文字数算出来的才显示。取不到就不显示，不虚构。
        article.article.readingMinutes?.let { minutes ->
            Spacer(Modifier.width(8.dp))
            Text("·", color = radarColors().textTertiary)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.article_reading_time, minutes),
                color = radarColors().textTertiary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }

    // 压薄头部：标题用 titleLarge（比 headlineSmall 矮一档），间距收紧，减少固定占用
    Spacer(Modifier.height(10.dp))
    Text(
        text = article.article.title,
        color = radarColors().textPrimary,
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
