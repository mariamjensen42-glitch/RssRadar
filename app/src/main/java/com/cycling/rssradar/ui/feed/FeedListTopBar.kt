package com.cycling.rssradar.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CheckCheck
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.SlidersHorizontal
import com.cycling.rssradar.R
import com.cycling.rssradar.core.model.ListViewMode
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.i18n.labelRes

@Composable

internal fun FeedListTopBar(
    onOpenSearch: () -> Unit,
    onOpenFilter: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenViewMode: () -> Unit,
    viewMode: ListViewMode,
    filterActive: Boolean,
) {
    // 顶栏只留高频的搜索，其余低频操作（标记已读/视图模式/分组筛选）收进溢出菜单：
    // 4 个无标签图标并排的可发现性差，新用户不可能逐个试
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "RssRadar",
            color = radarColors().textPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenSearch) {
            Icon(Lucide.Search, contentDescription = stringResource(R.string.action_search), tint = radarColors().textPrimary)
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Lucide.EllipsisVertical, contentDescription = stringResource(R.string.more_actions), tint = radarColors().textPrimary)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mark_read_title)) },
                    leadingIcon = { Icon(Lucide.CheckCheck, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onMarkAllRead()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                R.string.view_mode_value,
                                stringResource(viewMode.labelRes()),
                            ),
                        )
                    },
                    leadingIcon = { Icon(Lucide.LayoutGrid, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onOpenViewMode()
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (filterActive) stringResource(R.string.group_filter_active) else stringResource(R.string.group_filter)) },
                    leadingIcon = { Icon(Lucide.SlidersHorizontal, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onOpenFilter()
                    },
                )
            }
        }
    }
}

@Composable

internal fun FeedListTabRow(
    selected: FeedTab,
    unreadCount: Int,
    /** 实际渲染的 tab（推荐流可关，ADR-0013）。 */
    tabs: List<FeedTab> = FeedTab.entries,
    onSelect: (FeedTab) -> Unit,
) {
    // 5 个 tab 在 360dp 窄屏上约需 380dp，固定 Row 会把末尾 chip 裁掉（看着像少了一个 tab）。
    // 改成横向滚动，只在还能往右滚时叠一层右侧渐隐，提示后面还有内容。
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEach { tab ->
                val label = when (tab) {
                    FeedTab.All -> stringResource(R.string.filter_all)
                    FeedTab.Unread -> stringResource(R.string.tab_unread_count, unreadCount)
                    FeedTab.Starred -> stringResource(R.string.tab_starred)
                    FeedTab.Bookmarked -> stringResource(R.string.tab_read_later)
                    FeedTab.Recommended -> stringResource(R.string.tab_recommended)
                }
                FilterChip(
                    label = label,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                )
            }
        }
        if (scrollState.canScrollForward) {
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(28.dp)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, radarColors().bgRoot))),
                )
            }
        }
    }
}

@Composable

internal fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    // 轻量化选中样式：选中 = 低透明度 accent 底 + accent 文字（不再整块实色填充），
    // 未选中 = 透明底 + 次级文字，仅留可点区域。整体视觉重量比旧胶囊低一档。
    val bg = if (selected) radarColors().accent.copy(alpha = 0.16f) else Color.Transparent
    val fg = if (selected) radarColors().accent else radarColors().textSecondary
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}
