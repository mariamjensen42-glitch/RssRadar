package com.cycling.rssradar.ui.article

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Highlighter
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Type
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun ArticleDetailTopBar(
    title: String?,
    showTitle: Boolean,
    onBack: () -> Unit,
    onOpenStyle: () -> Unit,
    /** 打开页内查找栏。 */
    onOpenFind: () -> Unit,
    /** 跳到标注列表页。 */
    onOpenAnnotations: () -> Unit,
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
            Icon(Lucide.ArrowLeft, contentDescription = stringResource(R.string.nav_back), tint = MaterialTheme.colorScheme.onSurface)
        }
        // 标题滚出视口后顶栏补位显示（用户反馈）；阅读中隐藏，不占阅读注意力
        Box(modifier = Modifier.weight(1f)) {
            if (showTitle && title != null) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
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
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                IconButton(onClick = onGenerateSummary) {
                    Icon(
                        Lucide.Sparkles,
                        contentDescription = stringResource(R.string.ai_gen_summary),
                        tint = if (aiSummaryState is AiSummaryState.Failed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        // AI 翻译开关（issue #44）：未显示译文时发起翻译，显示中切回原文；生成中禁用
        IconButton(onClick = onToggleTranslation, enabled = !isGeneratingTranslation) {
            Icon(
                Lucide.Languages,
                contentDescription = if (isShowingTranslation) stringResource(R.string.ai_back_to_original) else stringResource(R.string.ai_translate),
                tint = if (isShowingTranslation || isGeneratingTranslation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        // 分享与排版设置是低频操作：收进溢出菜单，顶栏图标从 4-5 个降到 2-3 个
        Box {
            var menuExpanded by remember { mutableStateOf(false) }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Lucide.EllipsisVertical, contentDescription = stringResource(R.string.more_actions), tint = MaterialTheme.colorScheme.onSurface)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.find_in_page)) },
                    leadingIcon = { Icon(Lucide.Search, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onOpenFind()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.annotations_title)) },
                    leadingIcon = { Icon(Lucide.Highlighter, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onOpenAnnotations()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share)) },
                    leadingIcon = { Icon(Lucide.Share2, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onShare()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.typography_settings)) },
                    leadingIcon = { Icon(Lucide.Type, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onOpenStyle()
                    },
                )
            }
        }
    }
}
