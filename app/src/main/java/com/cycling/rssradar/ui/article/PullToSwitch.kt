package com.cycling.rssradar.ui.article

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * 触发切篇所需的越界位移（px，ReadYou 差距表 #23）。
 *
 * 比下拉刷新的触发距离更大：刷新只是多等一下，切篇是直接换掉你正在读的东西，
 * 误触代价高一个量级，所以宁可让人多拉一点。
 */
internal const val PULL_TO_SWITCH_DISTANCE = 160f

/** 越界拖拽该切到哪一篇。 */
enum class PullTarget { NONE, PREVIOUS, NEXT }

/**
 * 累积越界位移。
 *
 * [delta] 沿用 M3 下拉刷新的符号约定（已核对 `material3/pulltorefresh/PullToRefresh.kt`
 * 源码，不靠猜）：`> 0` 手指下滑（顶部下拉），`< 0` 手指上滑（底部上拉）。
 *
 * 不贴边就清零；方向翻转也清零——来回蹭不该能凑出一次切篇。
 * 累积量夹在 ±[PULL_TO_SWITCH_DISTANCE]，免得一直拉着不放涨到天上。
 */
internal fun accumulatePull(current: Float, delta: Float, atEdge: Boolean): Float {
    if (!atEdge) return 0f
    if (current != 0f && current * delta < 0f) return 0f
    return (current + delta).coerceIn(-PULL_TO_SWITCH_DISTANCE, PULL_TO_SWITCH_DISTANCE)
}

/**
 * 越过阈值就返回该切到哪一篇。
 *
 * 没有上一篇 / 下一篇时返回 [PullTarget.NONE]——不给一个「拉到底什么也没发生」的手势，
 * 与「没有意义的按钮不该存在」同一条原则。
 */
internal fun pullTarget(
    accumulated: Float,
    atTop: Boolean,
    atBottom: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
): PullTarget = when {
    accumulated >= PULL_TO_SWITCH_DISTANCE && atTop && hasPrev -> PullTarget.PREVIOUS
    accumulated <= -PULL_TO_SWITCH_DISTANCE && atBottom && hasNext -> PullTarget.NEXT
    else -> PullTarget.NONE
}

/**
 * 顶部下拉看上一篇、底部上拉看下一篇（#23）。
 *
 * **只观察、不消费**：`onPostScroll` 拿到的 `available` 是子级没吃掉的滚动量，
 * 贴边继续拉时才有值；这里一律返回 [Offset.Zero]，系统自己的拉伸效果照旧，
 * 不会跟正常滚动抢事件。
 *
 * 挂在滚动容器的**祖先**上（与 M3 `PullToRefreshBox` 的写法一致）：祖先的
 * connection 才能收到后代滚动的 pre/post 回调。
 *
 * 视口模式（WebView 内部滚动）拿不到越界量，本手势在那里不生效——
 * 设置项文案里如实说明，不假装处处可用。
 */
@Composable
internal fun Modifier.pullToSwitchArticle(
    enabled: Boolean,
    atTop: () -> Boolean,
    atBottom: () -> Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onSwitch: (PullTarget) -> Unit,
): Modifier {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentHasPrev by rememberUpdatedState(hasPrev)
    val currentHasNext by rememberUpdatedState(hasNext)
    val currentAtTop by rememberUpdatedState(atTop)
    val currentAtBottom by rememberUpdatedState(atBottom)
    val currentOnSwitch by rememberUpdatedState(onSwitch)
    val connection = remember {
        object : NestedScrollConnection {
            var accumulated = 0f

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!currentEnabled || source != NestedScrollSource.UserInput) return Offset.Zero
                val delta = available.y
                if (delta == 0f) return Offset.Zero
                val top = currentAtTop()
                val bottom = currentAtBottom()
                accumulated = accumulatePull(
                    current = accumulated,
                    delta = delta,
                    atEdge = if (delta > 0) top else bottom,
                )
                val target = pullTarget(accumulated, top, bottom, currentHasPrev, currentHasNext)
                if (target != PullTarget.NONE) {
                    accumulated = 0f
                    currentOnSwitch(target)
                }
                return Offset.Zero
            }

            // 抬手就清零：一次拖拽只算一次，不把上次的累积带进下一次
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                accumulated = 0f
                return Velocity.Zero
            }
        }
    }
    return this.nestedScroll(connection)
}
