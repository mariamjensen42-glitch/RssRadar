package com.cycling.rssradar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.data.FeedEntity
import com.cycling.rssradar.ui.components.FeedIcon
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.BgRoot
import com.cycling.rssradar.ui.theme.Link
import com.cycling.rssradar.ui.theme.OnAccent
import com.cycling.rssradar.ui.theme.Surface1
import com.cycling.rssradar.ui.theme.Surface2
import com.cycling.rssradar.ui.theme.TextPrimary
import com.cycling.rssradar.ui.theme.TextSecondary
import com.cycling.rssradar.ui.theme.TextTertiary
import com.composables.icons.lucide.ArrowDownUp
import com.composables.icons.lucide.BookMarked
import com.composables.icons.lucide.CheckCheck
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.CornerUpRight
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus

@Composable
fun SubscriptionsScreen(
    viewModel: SubscriptionsViewModel,
    onAddSubscription: () -> Unit = {},
    onCreateGroup: () -> Unit = {},
) {
    val groups by viewModel.groups.collectAsState()
    val expandedIds by viewModel.expandedGroupIds.collectAsState()
    val totalUnread by viewModel.totalUnread.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val message = viewModel.uiMessage

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    Scaffold(
        containerColor = BgRoot,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SubscriptionsTopBar(
                onSort = { viewModel.toggleSort() },
                onAdd = onAddSubscription,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddSubscription,
                containerColor = Accent,
                contentColor = OnAccent,
                icon = { Icon(Lucide.Plus, contentDescription = null) },
                text = { Text("添加") },
                shape = RoundedCornerShape(20.dp),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(groups, key = { it.group }) { group ->
                GroupSection(
                    title = group.group,
                    feedCount = group.feeds.size,
                    feeds = group.feeds,
                    expanded = group.group in expandedIds,
                    onToggle = { viewModel.toggleGroup(group.group) },
                    onEdit = { /* TODO: 编辑分组 */ },
                    onFeedMore = { /* TODO: feed 菜单 */ },
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                CreateGroupRow(onClick = onCreateGroup)
            }

            if (totalUnread > 0) {
                item {
                    Spacer(Modifier.height(8.dp))
                    MarkAllReadRow(onClick = { viewModel.markAllRead() })
                }
            }

            item { Spacer(Modifier.height(96.dp)) } // 避让 FAB
        }
    }
}

@Composable
private fun SubscriptionsTopBar(onSort: () -> Unit, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "订阅管理",
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSort) {
            Icon(Lucide.ArrowDownUp, contentDescription = "排序", tint = TextPrimary)
        }
        IconButton(onClick = onAdd) {
            Icon(Lucide.Plus, contentDescription = "添加订阅", tint = TextPrimary)
        }
    }
}

@Composable
private fun GroupSection(
    title: String,
    feedCount: Int,
    feeds: List<FeedWithUnread>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onFeedMore: (FeedEntity) -> Unit,
) {
    Column {
        GroupHeader(
            title = title,
            feedCount = feedCount,
            expanded = expanded,
            onToggle = onToggle,
            onEdit = onEdit,
        )
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 6.dp, start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                feeds.forEach { item ->
                    FeedRow(item = item, onMore = { onFeedMore(item.feed) })
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(
    title: String,
    feedCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) Lucide.ChevronDown else Lucide.ChevronRight,
            contentDescription = if (expanded) "折叠" else "展开",
            tint = TextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$feedCount 个订阅",
            color = TextTertiary,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onEdit) {
            Icon(
                Lucide.Pencil,
                contentDescription = "编辑分组",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun FeedRow(item: FeedWithUnread, onMore: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Surface1,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeedIcon(title = item.feed.title, size = 32.dp, cornerRadius = 8.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.feed.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.feed.url.withoutScheme(),
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            UnreadBadge(count = item.unreadCount)
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onMore, modifier = Modifier.size(32.dp)) {
                Icon(
                    Lucide.Ellipsis,
                    contentDescription = "更多",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    if (count <= 0) {
        Surface(shape = RoundedCornerShape(50), color = Surface2) {
            Text(
                text = "已读",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        return
    }
    Surface(shape = RoundedCornerShape(50), color = Accent) {
        Text(
            text = count.coerceAtMost(999).toString(),
            color = OnAccent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun CreateGroupRow(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Surface1,
        modifier = Modifier
            .fillMaxWidth()
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
                tint = Link,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "新建分组",
                color = Link,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MarkAllReadRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Lucide.CheckCheck,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "全部标记为已读",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun String.withoutScheme(): String = removePrefix("https://").removePrefix("http://")
