package com.cycling.rssradar.ui.subscriptions

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.core.data.db.DEFAULT_GROUP
import com.cycling.rssradar.core.ui.theme.Danger
import com.composables.icons.lucide.Eraser
import com.composables.icons.lucide.FolderX
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.theme.radarOutlinedTextFieldColors

/**
 * 分组操作底栏（issue #8）：重命名 / 清空分组文章 / 删除分组内全部订阅 / 删除分组。
 *
 * 四条动作的语义各不相同，文案里必须说清删的是什么：
 * - 清空文章：保源、保分组，只删文章；
 * - 删除分组内全部订阅：删源（文章级联），**分组保留**；
 * - 删除分组：**只把订阅移进默认分组**，一个都不删。
 *
 * 与 [FeedActionScreen] 同一套形态（composable + ModalBottomSheet，ADR-0002 #31）：
 * 纯弹层不进导航栈，关闭统一走 onDismiss。
 * 除重命名外都要二次确认——它们动的分别是文章、订阅源、订阅归属，均无撤销。
 *
 * [feedCount] 由调用方按当前分组现算（列表状态派生），用于如实报出影响范围、
 * 并在空分组时**整个隐藏**「删除全部订阅」——没有意义的按钮不该存在。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupActionSheet(
    group: String,
    feedCount: Int,
    viewModel: SubscriptionsViewModel,
    onDismiss: () -> Unit,
) {
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDeleteFeeds by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = radarColors().surface1,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                text = group,
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            ActionRow(
                icon = { Icon(Lucide.Pencil, contentDescription = null, tint = radarColors().textSecondary, modifier = Modifier.size(18.dp)) },
                title = "重命名分组",
                onClick = { renameTarget = group },
            )
            // 空分组时下面两条整块不出现：没有订阅源就没有文章可清，
            // "删除 0 个"的按钮没有意义（UI 铁律）。两条一起门禁，不留一条在一条不在。
            if (feedCount > 0) {
                Spacer(Modifier.height(8.dp))
                ActionRow(
                    icon = { Icon(Lucide.Eraser, contentDescription = null, tint = radarColors().textSecondary, modifier = Modifier.size(18.dp)) },
                    title = "清空分组文章",
                    subtitle = "删除本组所有订阅的文章，收藏与稍后读保留",
                    onClick = { confirmClear = true },
                )
                Spacer(Modifier.height(8.dp))
                ActionRow(
                    icon = { Icon(Lucide.FolderX, contentDescription = null, tint = Danger, modifier = Modifier.size(18.dp)) },
                    title = "删除分组内全部订阅",
                    subtitle = "删除本组 $feedCount 个订阅源及其文章，分组保留",
                    titleColor = Danger,
                    onClick = { confirmDeleteFeeds = true },
                )
            }
            // 默认分组是 feed 的兜底归属，删掉它没有语义
            if (group != DEFAULT_GROUP) {
                Spacer(Modifier.height(8.dp))
                ActionRow(
                    icon = { Icon(Lucide.Trash2, contentDescription = null, tint = Danger, modifier = Modifier.size(18.dp)) },
                    title = "删除分组",
                    subtitle = "分组内的订阅移入$DEFAULT_GROUP 分组",
                    titleColor = Danger,
                    onClick = { confirmDelete = true },
                )
            }
        }
    }

    renameTarget?.let { initial ->
        var value by remember { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = radarColors().surface1,
            titleContentColor = radarColors().textPrimary,
            textContentColor = radarColors().textSecondary,
            title = {
                Text(
                    "重命名分组",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    colors = radarOutlinedTextFieldColors(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onIntent(SubscriptionsIntent.RenameGroup(initial, value))
                        renameTarget = null
                        onDismiss()
                    },
                ) {
                    Text("保存", color = radarColors().accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消", color = radarColors().textTertiary) }
            },
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "清空分组文章",
            text = "将删除「$group」下所有订阅的文章，收藏与稍后读保留，操作不可撤销。",
            confirmText = "清空",
            onDismiss = { confirmClear = false },
            onConfirm = {
                viewModel.onIntent(SubscriptionsIntent.ClearGroupArticles(group))
                confirmClear = false
                onDismiss()
            },
        )
    }

    if (confirmDeleteFeeds) {
        ConfirmDialog(
            title = "删除分组内全部订阅",
            text = "将删除「$group」下的 $feedCount 个订阅源及其全部文章，此操作不可撤销。",
            confirmText = "全部删除",
            onDismiss = { confirmDeleteFeeds = false },
            onConfirm = {
                viewModel.onIntent(SubscriptionsIntent.DeleteGroupFeeds(group))
                confirmDeleteFeeds = false
                onDismiss()
            },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "删除分组",
            text = "「$group」下的订阅将移入$DEFAULT_GROUP 分组，订阅与其文章都不会被删除。",
            confirmText = "删除分组",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                viewModel.onIntent(SubscriptionsIntent.DeleteGroup(group))
                confirmDelete = false
                onDismiss()
            },
        )
    }
}

@Composable
private fun ActionRow(
    icon: @Composable () -> Unit,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    titleColor: Color = radarColors().textPrimary,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = radarColors().surface2,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = titleColor, style = MaterialTheme.typography.bodyMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = radarColors().surface1,
        titleContentColor = radarColors().textPrimary,
        textContentColor = radarColors().textSecondary,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = Danger, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = radarColors().textTertiary) }
        },
    )
}
