package com.cycling.rssradar.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.OnAccent
import com.cycling.rssradar.ui.theme.Surface2
import com.cycling.rssradar.ui.theme.TextSecondary
import com.cycling.rssradar.ui.theme.TextTertiary
import com.composables.icons.lucide.Library
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Rss
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.User

/** 主导航 4 个 tab，与底部 TabBar 一一对应。 */
enum class MainTab(val title: String) {
    Feed("文章"),
    Subscriptions("订阅"),
    Search("搜索"),
    Me("我的"),
}

/** 单一图标源：未选中/选中都用同一线性图标，靠 tint 区分。 */
private fun iconFor(tab: MainTab): ImageVector = when (tab) {
    MainTab.Feed -> Lucide.Rss
    MainTab.Subscriptions -> Lucide.Library
    MainTab.Search -> Lucide.Search
    MainTab.Me -> Lucide.User
}

/**
 * 悬浮胶囊底部 TabBar：选中 tab 是紫色填充胶囊；未选中是透明 + 灰字。
 * 通过 [WindowInsets.navigationBars] 适配系统手势条。
 */
@Composable
fun FloatingBottomBar(
    current: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val insets = WindowInsets.navigationBars.asPaddingValues()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp + insets.calculateBottomPadding()),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Surface2,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MainTab.entries.forEach { tab ->
                    TabItem(
                        tab = tab,
                        selected = tab == current,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (selected) Accent else Color.Transparent,
        label = "tab-bg",
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) OnAccent else TextTertiary,
        label = "tab-fg",
    )
    val secondaryFg by animateColorAsState(
        targetValue = if (selected) OnAccent else TextSecondary,
        label = "tab-secondary-fg",
    )
    @Suppress("UNUSED_EXPRESSION") secondaryFg // 留作未来副标题

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = iconFor(tab),
                contentDescription = tab.title,
                tint = foreground,
                modifier = Modifier.size(20.dp),
            )
            if (selected) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = tab.title,
                    color = foreground,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
