package com.cycling.rssradar.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.Dp
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


/** 底部 TabBar 的 4 个主屏条目，与 Nav 路由一一对应（key 用于选中态判定）。 */
private data class TabDef(val key: String, val title: String, val icon: ImageVector)

private val TOP_LEVEL_TABS = listOf(
    TabDef("feed", "文章", Lucide.Rss),
    TabDef("subs", "订阅", Lucide.Library),
    TabDef("search", "搜索", Lucide.Search),
    TabDef("me", "我的", Lucide.User),
)

/**
 * 悬浮 TabBar 的底部让位（不含导航栏 inset）：胶囊高约 52dp + 底边距 12dp。
 * 与 [FloatingBottomBar] 的实际占位联动——改胶囊内边距/外边距时必须同步这里，
 * 否则 tab 屏最后一条内容又会被遮住。
 */
val FloatingTabBarClearance = 64.dp

/** FAB 等悬浮件完整让开 TabBar 的底部抬升：让位高 + 呼吸空间。 */
val FloatingTabBarFabOffset = 88.dp

/**
 * 滚动内容底部应预留的让位（TabBar 总占位 + 导航栏 inset）。
 * TabBar 是 overlay 不占布局，各 tab 屏的 LazyColumn / 滚动 Column
 * 必须把它加进 contentPadding / padding，最后一条内容才能完整滚出胶囊。
 */
@Composable
fun tabBarBottomClearance(): Dp {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return FloatingTabBarClearance + navInset
}

/**
 * 悬浮胶囊底部 TabBar：选中 tab 是紫色填充胶囊；未选中是透明 + 灰字。
 * 通过 [WindowInsets.navigationBars] 适配系统手势条。
 * 选中态由 [currentRoute] 决定（来自 NavController 当前目的地，route 即单一真相源）。
 */
@Composable
fun FloatingBottomBar(
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
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
                TOP_LEVEL_TABS.forEach { tab ->
                    TabItem(
                        tab = tab,
                        selected = tab.key == currentRoute,
                        onClick = { onTabSelected(tab.key) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: TabDef,
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
                imageVector = tab.icon,
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
