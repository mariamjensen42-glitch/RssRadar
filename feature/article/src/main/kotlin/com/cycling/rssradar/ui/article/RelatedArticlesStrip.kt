package com.cycling.rssradar.ui.article

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed

/**
 * 相关阅读横滑条：与本文内容最相近的近期文章（本地 bigram 相似度，needsLlm=false）。
 *
 * 刻意的呈现决定：
 * - **无候选时整体不渲染**而不是显示「暂无相关」——阅读页寸土寸金，
 *   一块永远写着"没有"的常驻面板只会消耗注意力。
 * - 卡片只放标题与来源，不放相似度分数——0.37 vs 0.41 对用户没有意义，
 *   排序已经把"更相关"表达完了，再亮数字就是拿实现细节打扰阅读。
 */
@Composable
internal fun RelatedArticlesStrip(
    items: List<ArticleWithFeed>,
    onOpen: (Long) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.related_reads),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        // 右缘渐隐：提示右侧还有卡片可滑，卡片文字截断不再显得"被裁掉"
        val stripScroll = rememberScrollState()
        Box(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(stripScroll)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surfaceContainerLowest,
                        modifier = Modifier
                            .width(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpen(item.article.id) },
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = item.article.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurface,
                                fontWeight = FontWeight.Medium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = item.feedTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (stripScroll.canScrollForward) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(32.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, colors.surface),
                            ),
                        ),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}
