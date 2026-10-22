package com.cycling.rssradar.ui.subscriptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.SquareCheckBig
import com.composables.icons.lucide.X
import com.cycling.rssradar.core.data.db.entity.FeedEntity
import com.cycling.rssradar.core.ui.components.FeedIcon
import com.cycling.rssradar.core.ui.components.pressScale
import com.cycling.rssradar.core.ui.theme.Danger

/** 多选态顶栏：已选计数 + 执行移动/删除 + 退出。 */
@Composable
internal fun SelectionTopBar(
    selectedCount: Int,
    canMove: Boolean,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "已选择 $selectedCount 个订阅",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onMove, enabled = canMove) {
            Text("移动到", color = if (canMove) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onDelete, enabled = canMove) {
            Text("删除", color = if (canMove) Danger else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = onCancel) {
            Icon(Lucide.X, contentDescription = "退出多选", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
internal fun GroupHeader(
    title: String,
    feedCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 长按分组行 = 编辑（重命名/清空/删除）；行尾铅笔图标已删，减少视觉噪音
            .combinedClickable(onClick = onToggle, onLongClick = onEdit)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) Lucide.ChevronDown else Lucide.ChevronRight,
            contentDescription = if (expanded) "折叠" else "展开",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$feedCount 个订阅",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
internal fun FeedRow(
    item: FeedWithUnread,
    onClick: () -> Unit,
    onMore: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
) {
    // 按压缩放（docs/motion.md #2）：source 与 clickable 共用同一实例
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clip(RoundedCornerShape(12.dp))
            // 整行点击进「订阅源文章列表」（issue #51）；管理入口仍是行尾"⋯"
            .clickable(interactionSource = interactionSource, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 多选态：行首勾选框，图标与 tint 直接反映选中态
            if (selectionMode) {
                Icon(
                    imageVector = if (selected) Lucide.SquareCheckBig else Lucide.Square,
                    contentDescription = if (selected) "取消选择" else "选择",
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            FeedIcon(title = item.feed.title, iconUrl = item.feed.iconUrl, size = 32.dp, cornerRadius = 8.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.feed.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.feed.url.withoutScheme(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 类型标记：RSSHub 路由和常规 RSS 一眼区分
            if (item.feed.sourceType == FeedEntity.SOURCE_TYPE_RSSHUB) {
                Spacer(Modifier.width(6.dp))
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Text(
                        text = "RSSHub",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            // 失效标记（#82）：原因 + 连续失败次数，红色角标一眼定位伤员
            item.failure?.let { failure ->
                Spacer(Modifier.width(6.dp))
                Surface(shape = RoundedCornerShape(50), color = Danger.copy(alpha = 0.14f)) {
                    Text(
                        text = "${failure.label} · 连续 ${item.feed.consecutiveFailures} 次",
                        color = Danger,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            UnreadBadge(count = item.unreadCount)
            // 多选态隐藏"⋯"：勾选才是当前主要动作，避免点错进操作页
            if (!selectionMode) {
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onMore, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Lucide.Ellipsis,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    // 无未读不留任何徽标（UI 审计 F1）：灰色「已读」徽标无信息价值，还与状态标签混淆
    if (count <= 0) return
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
        Text(
            text = count.coerceAtMost(999).toString(),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
internal fun CreateGroupRow(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Lucide.Plus,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "新建分组",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
