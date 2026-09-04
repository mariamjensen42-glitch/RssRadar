package com.cycling.rssradar.ui.subscriptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.core.data.db.DEFAULT_GROUP
import com.cycling.rssradar.core.data.db.FeedEntity
import com.cycling.rssradar.core.ui.theme.Danger
import com.composables.icons.lucide.Eraser
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import com.cycling.rssradar.core.ui.theme.radarColors


/**
 * 订阅源操作（重命名 / 移动分组 / 删除）的 Nav 目的地内容。
 * 原 SubscriptionsScreen 的私有 FeedActionSheet 提升为 Nav 目的地（ADR-0002 #31）：
 * feed 与 groupOptions 由传入的 SubscriptionsViewModel 解析，重命名子对话框自包含，
 * 关闭统一走 onDismiss（= 目的地出栈）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedActionScreen(
    feedId: Long,
    viewModel: SubscriptionsViewModel,
    onDismiss: () -> Unit,
) {
    val feed by viewModel.getFeed(feedId).collectAsState()
    val groupOptions by viewModel.groupsList.collectAsState()
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    feed?.let { f ->
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = radarColors().surface1,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text(
                    text = f.title,
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = f.url.removePrefix("https://").removePrefix("http://"),
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "移动到分组",
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groupOptions.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { group ->
                                val selected = group == f.groupName.ifBlank { DEFAULT_GROUP }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (selected) radarColors().accent else radarColors().surface2,
                                    modifier = Modifier.clickable { viewModel.onIntent(SubscriptionsIntent.MoveFeed(f.id, group)); onDismiss() },
                                ) {
                                    Text(
                                        text = group,
                                        color = if (selected) radarColors().onAccent else radarColors().textPrimary,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "同步与预设",
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(8.dp))
                // 自动同步开关（issue #58）：屏蔽后不参与后台自动同步，手动刷新照常
                SwitchRow(
                    title = "参与自动同步",
                    subtitle = "关闭后此订阅源不再后台自动刷新",
                    checked = f.syncEnabled,
                    onCheckedChange = { v ->
                        viewModel.onIntent(SubscriptionsIntent.SetSyncEnabled(f.id, v))
                    },
                )
                Spacer(Modifier.height(4.dp))
                // 全文抓取开关（issue #9）：关闭后详情页不再自动抓原网页正文
                SwitchRow(
                    title = "自动抓取全文",
                    subtitle = "关闭后详情页只显示订阅源自带内容",
                    checked = f.fullContentEnabled,
                    onCheckedChange = { v ->
                        viewModel.onIntent(SubscriptionsIntent.SetFullContentEnabled(f.id, v))
                    },
                )
                Spacer(Modifier.height(4.dp))
                // 通知开关（#31）：Feed 级第二道闸；全局通知开关关时一律不发
                SwitchRow(
                    title = "新文章通知",
                    subtitle = "关闭后此订阅源的新文章不进系统通知",
                    checked = f.notificationsEnabled,
                    onCheckedChange = { v ->
                        viewModel.onIntent(SubscriptionsIntent.SetNotificationsEnabled(f.id, v))
                    },
                )
                Spacer(Modifier.height(12.dp))
                // 内容类型（ADR-0014）：决定列表浏览形态（图片画廊/视频音频卡），订阅时已按信号预判
                Text(
                    text = "内容类型",
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        FeedEntity.CONTENT_TYPE_ARTICLE to "文章",
                        FeedEntity.CONTENT_TYPE_IMAGE to "图片",
                        FeedEntity.CONTENT_TYPE_VIDEO to "视频",
                        FeedEntity.CONTENT_TYPE_AUDIO to "音频",
                    ).forEach { (type, label) ->
                        val selected = f.contentType == type
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selected) radarColors().accent else radarColors().surface2,
                            modifier = Modifier.clickable {
                                viewModel.onIntent(SubscriptionsIntent.SetContentType(f.id, type))
                            },
                        ) {
                            Text(
                                text = label,
                                color = if (selected) radarColors().onAccent else radarColors().textPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = radarColors().surface2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { renameTarget = f.title },
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Lucide.Pencil, contentDescription = null, tint = radarColors().textSecondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("重命名", color = radarColors().textPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                // 清空文章（issue #8）：只删文章保留订阅源，与「删除订阅（含其文章）」区分
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = radarColors().surface2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { confirmClear = true },
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Lucide.Eraser, contentDescription = null, tint = radarColors().textSecondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("清空文章（保留订阅）", color = radarColors().textPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = radarColors().surface2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onIntent(SubscriptionsIntent.DeleteFeed(f.id, f.title)); onDismiss() },
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Lucide.Trash2, contentDescription = null, tint = radarColors().textTertiary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("删除订阅（含其文章）", color = radarColors().textTertiary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
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
            title = { Text("重命名订阅", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = radarColors().surface2,
                        unfocusedContainerColor = radarColors().surface2,
                        focusedBorderColor = radarColors().accent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = radarColors().textPrimary,
                        unfocusedTextColor = radarColors().textPrimary,
                        cursorColor = radarColors().accent,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(SubscriptionsIntent.RenameFeed(feedId, value)); renameTarget = null; onDismiss() }) {
                    Text("保存", color = radarColors().accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("取消", color = radarColors().textTertiary)
                }
            },
        )
    }

    // 清空文章二次确认（issue #8）：删的是文章，订阅源保留
    if (confirmClear) {
        val title = feed?.title.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = radarColors().surface1,
            titleContentColor = radarColors().textPrimary,
            textContentColor = radarColors().textSecondary,
            title = { Text("清空文章", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "将删除「$title」的全部文章，收藏与稍后读保留，操作不可撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onIntent(SubscriptionsIntent.ClearFeedArticles(feedId, title))
                        confirmClear = false
                        onDismiss()
                    },
                ) {
                    Text("清空", color = Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消", color = radarColors().textTertiary) }
            },
        )
    }
}

/** 带副标题的开关行：Feed 级预设统一形态（issue #9）。 */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = radarColors().textPrimary, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, color = radarColors().textTertiary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = radarColors().onAccent,
                checkedTrackColor = radarColors().accent,
            ),
        )
    }
}
