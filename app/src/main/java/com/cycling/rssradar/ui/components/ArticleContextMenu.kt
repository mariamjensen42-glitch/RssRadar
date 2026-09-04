package com.cycling.rssradar.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.core.ui.theme.Danger
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.ThumbsDown
import com.composables.icons.lucide.Trash2
import com.cycling.rssradar.core.ui.theme.radarColors

/** 菜单项标准高度 48dp；容器上下 padding + 描边约 16dp。 */
private const val MENU_ITEM_HEIGHT_DP = 48
private const val MENU_CONTAINER_EXTRA_DP = 16

/**
 * 计算菜单偏移，让菜单贴着长按手指出现：
 * - 手指下方放得下整条菜单 → 向下开，菜单顶边 = 手指；
 * - 放不下 → 向上开，菜单底边 = 手指。
 * 必须在此预判翻转方向：M3 DropdownMenuPositionProvider 向上翻转时偏移符号反转
 * （bottom = anchorTop - offsetY），直接传负偏移会让菜单飞到卡片上方很远。
 *
 * [pressPos] 为卡片内坐标（px）；[cardTopInWindowPx] 为卡片左上角在窗口中的 y（px）；
 * [menuItemCount] 为实际渲染的菜单项数（决定估算高度）。
 */
fun articleMenuOffset(
    pressPos: Offset,
    cardTopInWindowPx: Float,
    cardHeightPx: Int,
    menuItemCount: Int,
    windowHeightPx: Float,
    density: Density,
): DpOffset {
    val menuHeightPx = with(density) {
        (menuItemCount * MENU_ITEM_HEIGHT_DP + MENU_CONTAINER_EXTRA_DP).dp.toPx()
    }
    val fingerAbsY = cardTopInWindowPx + pressPos.y
    val belowFits = fingerAbsY + menuHeightPx <= windowHeightPx
    return with(density) {
        // DropdownMenu 默认锚在锚点 Box 左下角展开：
        // 向下 → offset.y = press.y - cardHeight，把菜单顶边顶回手指处；
        // 向上 → M3 翻转分支为 bottom = anchorTop - offsetY，取 -press.y 让底边贴手指。
        DpOffset(
            pressPos.x.toDp(),
            (if (belowFits) pressPos.y - cardHeightPx else -pressPos.y).toDp(),
        )
    }
}

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
    /**
     * 「减少此类」（ADR-0013）：非空时菜单里出现该项，点击后该文章所属订阅源
     * 在推荐流里降权。只有推荐流传这个回调——常规列表不做推荐负反馈。
     */
    val onReduceSuch: (() -> Unit)? = null,
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
    /**
     * 菜单相对锚点（卡片 Box）默认落点（左下角）的偏移。
     * 调用方传长按手指位置换算出的偏移，让菜单出现在手指处；
     * 越出屏幕时 PopupPositionProvider 自动翻转/收回，无需调用方操心。
     */
    offset: DpOffset = DpOffset.Zero,
) {
    val context = LocalContext.current
    val hasLink = actions.link.isNotBlank()
    // iOS Dark 风：radarColors().surface1 卡片面 + 14dp 圆角 + radarColors().divider 细描边，与设计稿卡片语言一致
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        shape = RoundedCornerShape(14.dp),
        containerColor = radarColors().surface1,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, radarColors().divider),
    ) {
        DropdownMenuItem(
            text = { Text(if (actions.isRead) "标为未读" else "标为已读") },
            leadingIcon = { MenuIcon(if (actions.isRead) Lucide.EyeOff else Lucide.Eye) },
            colors = normalItemColors(),
            onClick = { onDismiss(); actions.onToggleRead() },
        )
        DropdownMenuItem(
            text = { Text(if (actions.isStarred) "取消收藏" else "收藏") },
            leadingIcon = { MenuIcon(Lucide.Star) },
            colors = normalItemColors(),
            onClick = { onDismiss(); actions.onToggleStarred() },
        )
        DropdownMenuItem(
            text = { Text(if (actions.isBookmarked) "移出稍后读" else "稍后读") },
            leadingIcon = { MenuIcon(Lucide.Bookmark) },
            colors = normalItemColors(),
            onClick = { onDismiss(); actions.onToggleBookmarked() },
        )
        DropdownMenuItem(
            text = { Text("复制链接") },
            leadingIcon = { MenuIcon(Lucide.Link) },
            enabled = hasLink,
            colors = normalItemColors(),
            onClick = { onDismiss(); copyLink(context, actions.link) },
        )
        DropdownMenuItem(
            text = { Text("分享") },
            leadingIcon = { MenuIcon(Lucide.Share2) },
            enabled = hasLink,
            colors = normalItemColors(),
            onClick = { onDismiss(); shareLink(context, actions.link) },
        )
        DropdownMenuItem(
            text = { Text("查看原文") },
            leadingIcon = { MenuIcon(Lucide.ExternalLink) },
            enabled = hasLink,
            colors = normalItemColors(),
            onClick = { onDismiss(); openInBrowser(context, actions.link) },
        )
        actions.onReduceSuch?.let { reduce ->
            DropdownMenuItem(
                text = { Text("减少此类") },
                leadingIcon = { MenuIcon(Lucide.ThumbsDown) },
                colors = normalItemColors(),
                onClick = { onDismiss(); reduce() },
            )
        }
        DropdownMenuItem(
            text = { Text("删除") },
            leadingIcon = { MenuIcon(Lucide.Trash2) },
            colors = dangerItemColors(),
            onClick = { onDismiss(); actions.onDelete() },
        )
    }
}

/** 普通项：文字 radarColors().textPrimary、图标 radarColors().textSecondary（禁用时文字/图标都用 radarColors().textTertiary）。 */
@Composable
private fun normalItemColors() = MenuItemColors(
    textColor = radarColors().textPrimary,
    leadingIconColor = radarColors().textSecondary,
    trailingIconColor = radarColors().textSecondary,
    disabledTextColor = radarColors().textTertiary,
    disabledLeadingIconColor = radarColors().textTertiary,
    disabledTrailingIconColor = radarColors().textTertiary,
)

/** 破坏性项（删除）：Danger 红，与 App 内危险操作色一致。 */
@Composable
private fun dangerItemColors() = MenuItemColors(
    textColor = Danger,
    leadingIconColor = Danger,
    trailingIconColor = Danger,
    disabledTextColor = radarColors().textTertiary,
    disabledLeadingIconColor = radarColors().textTertiary,
    disabledTrailingIconColor = radarColors().textTertiary,
)

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
