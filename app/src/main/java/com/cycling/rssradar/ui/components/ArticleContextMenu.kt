package com.cycling.rssradar.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Trash2

/**
 * 文章项上下文菜单的动作集与状态（长按菜单，issue #46）。
 * 由持有文章数据的调用方构造；菜单组件只负责呈现。
 */
data class ArticleMenuActions(
    val isRead: Boolean,
    val isStarred: Boolean,
    val isBookmarked: Boolean,
    val link: String,
    val onToggleRead: () -> Unit,
    val onToggleStarred: () -> Unit,
    val onToggleBookmarked: () -> Unit,
    val onDelete: () -> Unit,
)

/**
 * 文章项长按上下文菜单（DropdownMenu，issue #46）。
 * 信息流 ArticleCard 与搜索结果行共用，两处「文章项」行为一致。
 * 已读/未读、收藏、稍后读按当前状态动态显示；链接类动作在无链接时禁用。
 */
@Composable
fun ArticleContextMenu(
    expanded: Boolean,
    actions: ArticleMenuActions,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val hasLink = actions.link.isNotBlank()
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (actions.isRead) "标为未读" else "标为已读") },
            leadingIcon = { MenuIcon(if (actions.isRead) Lucide.EyeOff else Lucide.Eye) },
            onClick = { onDismiss(); actions.onToggleRead() },
        )
        DropdownMenuItem(
            text = { Text(if (actions.isStarred) "取消收藏" else "收藏") },
            leadingIcon = { MenuIcon(Lucide.Star) },
            onClick = { onDismiss(); actions.onToggleStarred() },
        )
        DropdownMenuItem(
            text = { Text(if (actions.isBookmarked) "移出稍后读" else "稍后读") },
            leadingIcon = { MenuIcon(Lucide.Bookmark) },
            onClick = { onDismiss(); actions.onToggleBookmarked() },
        )
        DropdownMenuItem(
            text = { Text("复制链接") },
            leadingIcon = { MenuIcon(Lucide.Link) },
            enabled = hasLink,
            onClick = { onDismiss(); copyLink(context, actions.link) },
        )
        DropdownMenuItem(
            text = { Text("分享") },
            leadingIcon = { MenuIcon(Lucide.Share2) },
            enabled = hasLink,
            onClick = { onDismiss(); shareLink(context, actions.link) },
        )
        DropdownMenuItem(
            text = { Text("查看原文") },
            leadingIcon = { MenuIcon(Lucide.ExternalLink) },
            enabled = hasLink,
            onClick = { onDismiss(); openInBrowser(context, actions.link) },
        )
        DropdownMenuItem(
            text = { Text("删除") },
            leadingIcon = { MenuIcon(Lucide.Trash2) },
            onClick = { onDismiss(); actions.onDelete() },
        )
    }
}

@Composable
private fun MenuIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
}

private fun copyLink(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("link", url))
    Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
}

private fun shareLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, url)
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
        .onFailure { Toast.makeText(context, "无法分享", Toast.LENGTH_SHORT).show() }
}

private fun openInBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure { Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show() }
}
