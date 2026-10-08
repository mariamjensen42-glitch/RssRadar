package com.cycling.rssradar.ui.article

import android.text.format.DateUtils
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.core.model.ExtractionIssue
import com.cycling.rssradar.core.model.FetchFailure
import com.cycling.rssradar.core.data.platform.openUrl
import com.cycling.rssradar.core.ui.theme.LocalReadingPrefs

/**
 * 正文槽位：五条渲染路径的唯一实现（原视口与整页两份逐行重复的 when 已合并）。
 *
 * 走哪条路由 [BodyPlan] 给定（纯函数 [resolveBodyPlan] 算好并 memo 过），本组合函数
 * 不再自己拼判据——判据错了是 [BodyModeTest] 的事，不是这里的事。
 * [viewport] 只决定触摸与滚动的归属；宽度由内部 fillMaxWidth 统一。
 */
@Composable

internal fun BodyContent(
    article: ArticleWithFeed,
    isFetchingContent: Boolean,
    /** 译文分段（[BodyMode.TRANSLATION] 用）。 */
    translationSegments: List<TranslationSegmentUi>,
    plan: BodyPlan,
    viewport: Boolean,
    imageUrls: List<String>,
    /** 视口模式的滚动回调：(滚动量, 可滚动上限)。上限为 0 = 内容不足一屏。 */
    onHeaderScroll: (Int, Int) -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 页内查找的活跃项：原生渲染器据此叠查找底色。 */
    find: ReadingFind = ReadingFind(),
    /** 命中总数上报（WebView 路自己算命中数，需要出口）。 */
    onFindCount: (Int) -> Unit = {},
    /**
     * 本次打开要恢复的阅读位置（比例）。只对视口模式生效——整页模式的滚动归外层
     * Compose，由 [ReadingBody] 自己恢复；WebView 只在自己滚动的视口模式里滚得动。
     */
    restoreRatio: Float? = null,
    /** 到顶 / 到底的跳转请求：同样只对视口模式有意义，整页模式由外层 Compose 滚。 */
    jumpRequest: JumpRequest? = null,
) {
    val context = LocalContext.current
    when (plan.mode) {
        // 渐进/已完成的译文：原生分段渲染（渐进显示 + 双语对照，翻译功能 v2）
        BodyMode.TRANSLATION -> TranslationReader(
            segments = translationSegments,
            onLinkClick = { context.openUrl(it) },
            onImageClick = onImageClick,
            modifier = modifier.fillMaxWidth(),
        )
        // 译文分段解析全空的兜底：整页 WebView 显示已完成译文（或原文）
        BodyMode.TRANSLATION_FALLBACK -> ArticleWebView(
            html = plan.fallbackHtml ?: article.article.summary.orEmpty(),
            imageUrls = imageUrls,
            passThroughTouch = !viewport,
            onScroll = if (viewport) onHeaderScroll else null,
            restoreRatio = if (viewport) restoreRatio else null,
            jumpRequest = if (viewport) jumpRequest else null,
            onImageClick = onImageClick,
            findQuery = find.query,
            findCursor = find.cursor,
            onFindCount = onFindCount,
            modifier = modifier.fillMaxWidth(),
        )
        // 原生渲染器：中间树非空才走到这个模式
        BodyMode.NATIVE -> CompositionLocalProvider(LocalFindHighlight provides find.highlight) {
            ArticleNativeReader(
                nodes = plan.nativeNodes,
                onLinkClick = { context.openUrl(it) },
                onImageClick = onImageClick,
                modifier = modifier.fillMaxWidth(),
                focus = find.focus,
            )
        }
        BodyMode.WEBVIEW -> ArticleWebView(
            html = article.article.content ?: article.article.summary.orEmpty(),
            imageUrls = imageUrls,
            passThroughTouch = !viewport,
            onScroll = if (viewport) onHeaderScroll else null,
            restoreRatio = if (viewport) restoreRatio else null,
            jumpRequest = if (viewport) jumpRequest else null,
            onImageClick = onImageClick,
            findQuery = find.query,
            findCursor = find.cursor,
            onFindCount = onFindCount,
            modifier = modifier.fillMaxWidth(),
        )
        BodyMode.NO_CONTENT -> NoContentBody(
            summary = article.article.summary,
            isFetchingContent = isFetchingContent,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

@Composable

internal fun BodyParagraph(text: String) {
    val style = LocalReadingPrefs.current.style
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = style.fontSize.sp,
            lineHeight = (style.fontSize * style.lineHeight).sp,
            fontFamily = style.fontFamily.toComposeFontFamily(),
        ),
    )
}

@Composable

internal fun formatDate(ts: Long?): String =
    ts?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: stringResource(R.string.unknown_time)
