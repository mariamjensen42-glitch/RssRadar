package com.cycling.rssradar.ui.article

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Type
import com.cycling.rssradar.core.model.ExtractionIssue
import com.cycling.rssradar.core.model.FetchFailure
import com.cycling.rssradar.core.model.BilingualLayout
import com.cycling.rssradar.core.model.TranslationDisplayState
import com.cycling.rssradar.core.model.TranslationViewMode
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.labels.uiRes
import com.cycling.rssradar.core.ui.theme.LocalReadingPrefs

/**
 * 抓取结果横幅：把「为什么这篇没有正文」说给读者听（ReadYou 的 Error 态同款）。
 *
 * 以前失败原因只写进抓取日志，只有诊断页看得到，阅读页静默降级——
 * 于是「我为什么只有摘要」成了无解的问题。这里把两种需要解释的结果摊到正文上方：
 * - 失败：中文原因 + 重试按钮（原因来自 [FetchFailure.label]）；
 * - 不完整：哪一类不完整（过短 / 脚本渲染 / 付费墙…），不假装这就是全文。
 */
@Composable
internal fun FetchStateBanner(
    state: ContentFetchState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is ContentFetchState.Failed ->
            FetchFailedBanner(reason = state.reason, onRetry = onRetry, modifier = modifier)

        is ContentFetchState.Incomplete ->
            IncompleteContentBanner(issue = state.issue, modifier = modifier)

        ContentFetchState.Idle, ContentFetchState.Loading, ContentFetchState.Ready -> Unit
    }
}

@Composable
private fun FetchFailedBanner(
    reason: FetchFailReason,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = when (reason) {
        is FetchFailReason.FromFailure ->
            stringResource(R.string.article_fetch_failed, stringResource(reason.failure.uiRes()))
        FetchFailReason.FeedDisabled -> stringResource(R.string.article_fetch_feed_disabled)
        FetchFailReason.ArticleMissing -> stringResource(R.string.article_not_found)
        is FetchFailReason.ShorterThanExisting ->
            stringResource(R.string.article_fetch_shorter, reason.got, reason.existing)
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = radarColors().surface2,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Lucide.CircleAlert,
                contentDescription = null,
                tint = radarColors().textTertiary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = message,
                color = radarColors().textTertiary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.retry),
                    style = MaterialTheme.typography.labelMedium,
                    color = radarColors().accent,
                )
            }
        }
    }
}

/**
 * 摘要态提示：读者手动切回订阅源摘要后常驻一行，说明「你现在看的不是全文」
 * 并给出回退出口。没有这行，切过去的人隔天回来看见短正文只会以为是抓取坏了。
 */
@Composable
internal fun SummaryModeBanner(
    onShowFull: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = radarColors().surface2,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Lucide.Type,
                contentDescription = null,
                tint = radarColors().textTertiary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.summary_banner),
                color = radarColors().textTertiary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onShowFull) {
                Text(
                    text = stringResource(R.string.see_full),
                    style = MaterialTheme.typography.labelMedium,
                    color = radarColors().accent,
                )
            }
        }
    }
}

/** 无正文分支的兜底提示：正文被判「不完整」时挂在头部下方（ADR-0012）。 */
@Composable
private fun IncompleteContentBanner(
    issue: ExtractionIssue?,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = radarColors().surface2,
        modifier = modifier.fillMaxWidth(),
    ) {
        // issue 的枚举先在组合作用域翻成当前语言，再喂给带占位符的资源
        val issueText = when (val i = issue) {
            null -> stringResource(ExtractionIssue.NONE.uiRes())
            else -> stringResource(i.uiRes())
        }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Lucide.CircleAlert,
                contentDescription = null,
                tint = radarColors().textTertiary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.article_incomplete, issueText),
                color = radarColors().textTertiary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** 无正文分支：显示摘要；按需抓取中给出轻提示，失败静默（"查看原文"兜底）。 */
@Composable
internal fun NoContentBody(
    summary: String?,
    isFetchingContent: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        BodyParagraph(text = summary ?: stringResource(R.string.body_empty))
        if (isFetchingContent) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = radarColors().accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.fetching_full),
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** 摘要折叠阈值的近似字符数：超过才给标题行的展开/收起入口，3 行 bodyMedium 中文约 60-70 字/3 行 ×2 缓冲。 */
internal const val SUMMARY_COLLAPSE_CHARS = 120

/**
 * 译文状态条（翻译功能 v2）：渐进中显示「翻译中 x/y 段」+ 转圈；完成后提供
 * 双语/纯译文切换、上下/左右排布切换（双语时）、重译与切回原文。
 * 显示偏好经 [onDisplayChange] 写回持久化 Store（用户级偏好，记住上次选择）。
 */
@Composable
internal fun TranslationBanner(
    state: TranslationState,
    onRetranslate: () -> Unit,
    onShowOriginal: () -> Unit,
    onDisplayChange: (TranslationDisplayState) -> Unit,
) {
    val display = LocalReadingPrefs.current.translation
    val progressing = state is TranslationState.Progressing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Languages, contentDescription = null, tint = radarColors().accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (progressing) {
                stringResource(R.string.article_translating_progress, (state as TranslationState.Progressing).doneCount, state.total)
            } else {
                stringResource(R.string.ai_translation_deepseek)
            },
            color = radarColors().textTertiary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        if (progressing) {
            CircularProgressIndicator(color = radarColors().accent, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
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
                    text = if (display.viewMode == TranslationViewMode.TRANSLATION_ONLY) stringResource(R.string.bilingual) else stringResource(R.string.translation_only),
                    color = radarColors().accent,
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
                        text = if (display.bilingualLayout == BilingualLayout.STACKED) stringResource(R.string.side_by_side) else stringResource(R.string.stacked),
                        color = radarColors().accent,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            TextButton(onClick = onRetranslate, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(stringResource(R.string.retranslate), color = radarColors().accent, style = MaterialTheme.typography.labelMedium)
            }
        }
        TextButton(onClick = onShowOriginal, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(stringResource(R.string.ai_back_to_original), color = radarColors().textSecondary, style = MaterialTheme.typography.labelMedium)
        }
    }
}
