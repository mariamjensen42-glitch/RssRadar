package com.cycling.rssradar.ui.article

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * AI 摘要常驻卡片（issue #44）：空态给生成按钮不藏功能；生成中转圈；
 * 有摘要显示内容；失败显示原因并可重试。空态/失败引导统一由 VM 给中文文案。
 *
 * 摘要默认折叠为 3 行（真机反馈：长摘要全量铺开霸屏，正文被顶出首屏）。展开/收起的入口
 * 固定在**标题行**（整行可点 + 行尾箭头），位置与展开与否无关；折叠态下卡片高度稳定，
 * 阅读动线不被摘要劫持。
 */
@Composable
internal fun AiSummaryCard(
    summary: String?,
    state: AiSummaryState,
    onGenerate: () -> Unit,
) {
    // 逐篇独立记忆；换文章（summary 变化）回到折叠态
    var expanded by remember(summary) { mutableStateOf(false) }
    // 摘要够长才需要折叠。切换入口固定在标题行——原先放在摘要下方的 TextButton，
    // 展开后会被全文推到卡片底部，得滚到底才收得起来（用户反馈「收起和展开不方便」）。
    val collapsible = summary != null && summary.length > SUMMARY_COLLAPSE_CHARS
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (collapsible) {
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { expanded = !expanded }
                                .padding(vertical = 6.dp)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Icon(Lucide.Sparkles, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.ai_summary),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (state is AiSummaryState.Generating) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                }
                if (collapsible) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Lucide.ChevronUp else Lucide.ChevronDown,
                        contentDescription = stringResource(
                            if (expanded) R.string.collapse else R.string.expand_full,
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            when {
                state is AiSummaryState.Generating -> {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.generating_summary), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                state is AiSummaryState.Failed -> {
                    Spacer(Modifier.height(8.dp))
                    Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onGenerate) {
                        Text(if (summary == null) stringResource(R.string.retry) else stringResource(R.string.regenerate), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
                summary != null -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = summary,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        // 折叠态 3 行截断；展开后全文。切换入口在标题行，不再压在正文末尾
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                else -> {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onGenerate) {
                        Text(stringResource(R.string.ai_gen_summary), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
