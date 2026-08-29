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
import com.cycling.rssradar.data.db.DEFAULT_GROUP
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.OnAccent
import com.cycling.rssradar.ui.theme.Surface1
import com.cycling.rssradar.ui.theme.Surface2
import com.cycling.rssradar.ui.theme.TextPrimary
import com.cycling.rssradar.ui.theme.TextSecondary
import com.cycling.rssradar.ui.theme.TextTertiary
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2


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

    feed?.let { f ->
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = Surface1,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text(
                    text = f.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = f.url.removePrefix("https://").removePrefix("http://"),
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "移动到分组",
                    color = TextTertiary,
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
                                    color = if (selected) Accent else Surface2,
                                    modifier = Modifier.clickable { viewModel.onIntent(SubscriptionsIntent.MoveFeed(f.id, group)); onDismiss() },
                                ) {
                                    Text(
                                        text = group,
                                        color = if (selected) OnAccent else TextPrimary,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Surface2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { renameTarget = f.title },
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Lucide.Pencil, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("重命名", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Surface2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onIntent(SubscriptionsIntent.DeleteFeed(f.id, f.title)); onDismiss() },
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Lucide.Trash2, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("删除订阅（含其文章）", color = TextTertiary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    renameTarget?.let { initial ->
        var value by remember { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = Surface1,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("重命名订阅", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Surface2,
                        unfocusedContainerColor = Surface2,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Accent,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(SubscriptionsIntent.RenameFeed(feedId, value)); renameTarget = null; onDismiss() }) {
                    Text("保存", color = Accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("取消", color = TextTertiary)
                }
            },
        )
    }
}
