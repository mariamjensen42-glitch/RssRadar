package com.cycling.rssradar.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CheckCheck
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Newspaper
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.Video
import com.cycling.rssradar.core.model.ListViewMode
import com.cycling.rssradar.core.ui.theme.RssRadarTheme
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.labels.labelRes

@Composable

internal fun FeedListTopBar(
    onOpenSearch: () -> Unit,
    onOpenFilter: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenViewMode: () -> Unit,
    viewMode: ListViewMode,
    filterActive: Boolean,
    selectedTab: FeedTab,
    unreadCount: Int,
    tabs: List<FeedTab>,
    onSelectTab: (FeedTab) -> Unit,
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
        FeedTabMenuButton(
            selected = selectedTab,
            unreadCount = unreadCount,
            tabs = tabs,
            onSelect = onSelectTab,
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

/**
 * 顶栏的视图切换（状态 tab）。原本是常驻首页的一排 chip，与分区行叠成两条筛选带、
 * 占去顶部两层；收进顶栏后只剩一层。
 *
 * 刻意不做成「塞进 ⋮ 菜单的第 4~8 项」：未读计数是首页最该一直露在外面的信息，
 * 再藏一层就等于每次都要点开菜单才知道有没有新东西。这里当前视图名（含计数）常驻，
 * 点击就地下拉、不遮屏，切换同样两次点击但不必先找菜单。
 * tabs 由调用方传入（ADR-0013 推荐流可关）。
 */
@Composable

private fun FeedTabMenuButton(
    selected: FeedTab,
    unreadCount: Int,
    tabs: List<FeedTab>,
    onSelect: (FeedTab) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = feedTabLabel(selected, unreadCount),
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Icon(
                imageVector = Lucide.ChevronDown,
                contentDescription = null,
                tint = radarColors().textSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            tabs.forEach { tab ->
                DropdownMenuItem(
                    text = { Text(feedTabLabel(tab, unreadCount)) },
                    trailingIcon = {
                        if (tab == selected) {
                            Icon(Lucide.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(tab)
                    },
                )
            }
        }
    }
}

/**
 * tab 文案的唯一出处：顶栏按钮与下拉项共用。两处各写一遍 when 的话，
 * 「未读」的计数格式改了只改一处也不会报错，只会静默不一致。
 */
@Composable

private fun feedTabLabel(tab: FeedTab, unreadCount: Int): String = when (tab) {
    FeedTab.All -> stringResource(R.string.filter_all)
    FeedTab.Unread -> stringResource(R.string.tab_unread_count, unreadCount)
    FeedTab.Starred -> stringResource(R.string.tab_starred)
    FeedTab.Bookmarked -> stringResource(R.string.tab_read_later)
    FeedTab.Recommended -> stringResource(R.string.tab_recommended)
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
                FilterChip(
                    label = feedTabLabel(tab, unreadCount),
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
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
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

/**
 * 主页一行分区栏（Folo 式）：选中项展开为「图标 + 文字」实心胶囊，未选中项收缩为纯图标圆钮。
 *
 * 高度对标 M3 图标按钮的 40dp（32dp 被真机判「太小」、48dp 又被判「太高」，取标准档）。
 * 选中胶囊的**宽**由左右各 30dp 的内边距撑出来 —— Folo 的选中项正是靠这份大留白与未选中项
 * 拉开差距，而不是靠放大字号或图标；垂直内边距把高度撑到与未选中项顶底齐平。
 * 四项合计约 265dp，360dp 窄屏仍放得下。
 * 状态自解释：选中态本身就是「当前在看哪个分区」的指示，不必再另做提示。
 */
@Composable

internal fun ContentTypeFilterRow(
    selected: ContentTypeFilter,
    onSelect: (ContentTypeFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val labels = ContentTypeFilter.entries.associateWith { stringResource(it.labelRes()) }
        ContentTypeFilter.entries.forEach { type ->
            ContentTypeOption(
                icon = type.mediaIcon(),
                label = labels.getValue(type),
                selected = type == selected,
                onClick = { onSelect(type) },
            )
        }
    }
}

/**
 * 选中态刻意用实心 accent（而非上排状态 chip 的 16% 淡底）：两行语义正交，
 * 视觉强度不同才不会连成一片被读成同一个九选一的单选组。
 *
 * 未选中项取大圆角方形而非正圆：形状与 Folo 一致。未选中项只留图标，
 * 故 contentDescription 取分区名，否则这几个图形在无障碍下全是空的。
 */
@Composable

private fun ContentTypeOption(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Surface(
            shape = RoundedCornerShape(50),
            color = radarColors().accent,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onClick),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = 30.dp,
                    vertical = (ContentTypeOptionHeight - CapsuleContentHeight) / 2,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = radarColors().onAccent,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = label,
                    color = radarColors().onAccent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    } else {
        Surface(
            shape = ContentTypeOptionShape,
            color = radarColors().surface2,
            modifier = Modifier
                .clip(ContentTypeOptionShape)
                .clickable(onClick = onClick),
        ) {
            Box(
                modifier = Modifier.size(ContentTypeOptionHeight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = radarColors().textSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** 分区项高度：对标 M3 图标按钮的 40dp（真机上 32dp 判「太小」、48dp 判「太高」，取标准档）。 */
private val ContentTypeOptionHeight = 40.dp

/** 胶囊内容的自然高度：图标 20dp 与 labelLarge 行高 20dp 取大者（字体放大时实际会更高，故不写死高度）。 */
private val CapsuleContentHeight = 20.dp

/** 未选中项的形状：大圆角方形（Folo 的按钮形状），圆角按约 1/3 边长取。 */
private val ContentTypeOptionShape = RoundedCornerShape(13.dp)

/**
 * 「文章」用报纸图标：contentType=0 与社媒源共用这一个值（ADR-0014 未单设社媒），
 * 而这类源是列表里的绝对多数，用「文章」的正名比用列表/网格之类的抽象图标更好认。
 */
private fun ContentTypeFilter.mediaIcon(): ImageVector = when (this) {
    ContentTypeFilter.Article -> Lucide.Newspaper
    ContentTypeFilter.Image -> Lucide.Image
    ContentTypeFilter.Video -> Lucide.Video
    ContentTypeFilter.Audio -> Lucide.Music
}

@Preview(showBackground = true, widthDp = 360, name = "首页顶部 · 浅色窄屏")
@Composable

private fun FeedListHeaderPreview() {
    RssRadarTheme(darkTheme = false) {
        Column {
            FeedListTopBar(
                onOpenSearch = {},
                onOpenFilter = {},
                onMarkAllRead = {},
                onOpenViewMode = {},
                viewMode = ListViewMode.CARD,
                filterActive = false,
                selectedTab = FeedTab.Unread,
                unreadCount = 47,
                tabs = FeedTab.entries,
                onSelectTab = {},
            )
            ContentTypeFilterRow(selected = ContentTypeFilter.Article, onSelect = {})
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "顶栏 · 各视图态")
@Composable

private fun FeedListTopBarTabStatesPreview() {
    RssRadarTheme(darkTheme = false) {
        Column {
            listOf(FeedTab.All, FeedTab.Unread, FeedTab.Bookmarked, FeedTab.Recommended).forEach { tab ->
                FeedListTopBar(
                    onOpenSearch = {},
                    onOpenFilter = {},
                    onMarkAllRead = {},
                    onOpenViewMode = {},
                    viewMode = ListViewMode.CARD,
                    filterActive = false,
                    selectedTab = tab,
                    unreadCount = 47,
                    tabs = FeedTab.entries,
                    onSelectTab = {},
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "分区行 · 四种选中态")
@Composable

private fun ContentTypeFilterRowStatesPreview() {
    RssRadarTheme(darkTheme = false) {
        Column {
            ContentTypeFilter.entries.forEach { type ->
                ContentTypeFilterRow(selected = type, onSelect = {})
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "分区行 · 深色")
@Composable

private fun ContentTypeFilterRowDarkPreview() {
    RssRadarTheme(darkTheme = true) {
        Column {
            ContentTypeFilterRow(selected = ContentTypeFilter.Image, onSelect = {})
        }
    }
}
