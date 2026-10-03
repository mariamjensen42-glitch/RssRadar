package com.cycling.rssradar.core.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Library
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Rss
import com.composables.icons.lucide.User
import com.cycling.rssradar.core.ui.theme.radarColors

/** 底部 TabBar 的主屏条目，与 Nav 路由一一对应（key 用于选中态判定）。 */
private data class TabDef(val key: String, val title: String, val icon: ImageVector)

private val TOP_LEVEL_TABS = listOf(
    TabDef("feed", "文章", Lucide.Rss),
    TabDef("subs", "订阅", Lucide.Library),
    TabDef("me", "我的", Lucide.User),
)

/**
 * 底部导航栏让位（不含系统导航栏 inset）。
 * 对应官方 [NavigationBar] 的标准高度 80dp——它由组件自己定高，不是我们配的，
 * 但各 tab 屏要用它做 contentPadding 的预留，所以这个常量必须跟住 [NavigationBar] 的高度。
 * 升级 material3 时若默认高度变了，回来核对这个数（编译期不会报错）。
 */
val TabBarClearance = 80.dp

/** FAB 等悬浮件完整让开导航栏的底部抬升：让位高 + 呼吸空间。 */
val TabBarFabOffset = 104.dp

/**
 * 滚动内容底部应预留的让位（导航栏总占位 + 系统导航栏 inset）。
 * 本导航栏是 overlay 不占布局，各 tab 屏的 LazyColumn / 滚动 Column
 * 必须把它加进 contentPadding / padding，最后一条内容才能完整滚出导航栏。
 */
@Composable
fun tabBarBottomClearance(): Dp {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return TabBarClearance + navInset
}

/**
 * 底部导航栏：官方 [NavigationBar] 通栏容器 + [NavigationBarItem]，
 * 不再手写 Surface / 颜色动画，也不用悬浮胶囊（2026-10-03 定：不做胶囊形态）。
 * 通过 [WindowInsets.navigationBars] 适配系统手势条。
 * 选中态由 [currentRoute] 决定（来自 NavController 当前目的地，route 即单一真相源）。
 *
 * 函数名保留 [FloatingBottomBar] 是历史遗留：它已不是 floating 形态，
 * 但改名要动 MainActivity 的调用点，收益不抵风险，故不改。语义看 KDoc。
 */
@Composable
fun FloatingBottomBar(
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        containerColor = radarColors().surface2,
        modifier = modifier.fillMaxWidth(),
    ) {
        TOP_LEVEL_TABS.forEach { tab ->
            NavigationBarItem(
                selected = tab.key == currentRoute,
                onClick = { onTabSelected(tab.key) },
                icon = { Icon(tab.icon, contentDescription = tab.title) },
                label = { Text(tab.title) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = radarColors().onAccent,
                    selectedTextColor = radarColors().onAccent,
                    indicatorColor = radarColors().accent,
                    unselectedIconColor = radarColors().textTertiary,
                    unselectedTextColor = radarColors().textSecondary,
                ),
            )
        }
    }
}
