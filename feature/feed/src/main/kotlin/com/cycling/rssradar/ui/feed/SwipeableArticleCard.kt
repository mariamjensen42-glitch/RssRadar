package com.cycling.rssradar.ui.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Star
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.core.model.ListDisplayState
import com.cycling.rssradar.core.ui.theme.radarColors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 列表手势：右滑收藏 / 左滑切换已读——RSS 阅读器的肌肉记忆。
 *
 * 两个动作都不该让卡片从列表里消失（标已读后它只是变灰，收藏后只是多颗星），
 * 所以走「落定即执行 + 弹回」的路子。
 *
 * 刻意不用 M3 SwipeToDismissBox：它的手势仲裁是「哪个轴先过 touch slop 谁赢」，
 * 垂直滚动时手指的横向漂移经常抢到第一拍，卡片被误判成横滑并触发收藏/已读。
 * 这里改为自研手势（[Modifier.pointerInput]）：横向位移不仅要过 slop，还要
 * 显著大于纵向（1.5 倍）才接管；其余手势一律不消费，完整交还给纵向滚动。
 */
@Composable
internal fun SwipeableArticleCard(
    item: ArticleWithFeed,
    display: ListDisplayState,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleStarred: () -> Unit,
    onToggleBookmarked: () -> Unit,
    onDelete: () -> Unit,
    onReduceSuch: (() -> Unit)? = null,
) {
    // 回调每次重组都是新 lambda，而手势协程是长生命周期（key=Unit）——
    // 用 UpdatedState 保证拿到最新回调。
    val currentToggleRead by rememberUpdatedState(onToggleRead)
    val currentToggleStarred by rememberUpdatedState(onToggleStarred)

    val density = LocalDensity.current
    val maxOffsetPx = with(density) { 120.dp.toPx() } // 滑动位移上限
    val triggerPx = with(density) { 72.dp.toPx() } // 松手触发动作的距离阈值
    val touchSlopPx = LocalViewConfiguration.current.touchSlop
    // 高速轻扫阈值（px/s）：位移不够但速度够快也算有意滑动（fling 手感）
    val flingVelocityPx = with(density) { 1200.dp.toPx() }

    // 卡片横向偏移：拖动中 snapTo 跟手，松手 animateTo(0) 弹回
    val offsetX = remember { Animatable(0f) }
    var swipeDir by remember { mutableStateOf<SwipeDirection?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxWidth()) {
        // 背景：滑动中按方向露出动作提示
        SwipeActionBackground(
            modifier = Modifier.matchParentSize(), // 背景不参与测量，跟随卡片尺寸
            direction = swipeDir,
            isRead = item.article.isRead,
            isStarred = item.article.isStarred,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(maxOffsetPx, triggerPx, touchSlopPx, flingVelocityPx) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        tracker.resetTracking()
                        var total = Offset.Zero
                        var engaged = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.firstOrNull { it.pressed }
                            if (pressed == null) break // 全部指针抬起/取消
                            // 手动差分而非 positionChange()：旁观阶段不消费事件，
                            // 需要的是「忽略消费状态」的位移（等价 positionChangeIgnoreConsumed）
                            val delta = pressed.position - pressed.previousPosition
                            total += delta
                            if (!engaged && total.getDistance() > touchSlopPx) {
                                // 方向仲裁（见函数注释）：横向需 1.5 倍优势
                                engaged = abs(total.x) > abs(total.y) * 1.5f
                            }
                            if (engaged) {
                                // 接管后消费全部事件：纵向滚动停止，clickable 也因
                                // move 被消费而取消，不会误触发点击
                                event.changes.forEach { it.consume() }
                                val next = (offsetX.value + delta.x)
                                    .coerceIn(-maxOffsetPx, maxOffsetPx)
                                swipeDir =
                                    if (next >= 0f) SwipeDirection.RIGHT else SwipeDirection.LEFT
                                tracker.addPosition(pressed.uptimeMillis, pressed.position)
                                scope.launch { offsetX.snapTo(next) }
                            }
                        }
                        if (engaged) {
                            val vx = tracker.calculateVelocity().x
                            val offset = offsetX.value
                            when {
                                offset > triggerPx || (offset > 0 && vx > flingVelocityPx) ->
                                    currentToggleStarred() // 右滑：收藏
                                offset < -triggerPx || (offset < 0 && vx < -flingVelocityPx) ->
                                    currentToggleRead() // 左滑：切换已读
                            }
                            swipeDir = null
                            scope.launch {
                                offsetX.animateTo(
                                    0f,
                                    spring(stiffness = Spring.StiffnessMediumLow),
                                )
                            }
                        }
                    }
                },
        ) {
            ArticleCard(
                item = item,
                display = display,
                onClick = onClick,
                onToggleRead = onToggleRead,
                onToggleStarred = onToggleStarred,
                onToggleBookmarked = onToggleBookmarked,
                onDelete = onDelete,
                onReduceSuch = onReduceSuch,
            )
        }
    }
}

/** 横向滑动方向：决定背景从哪侧露出。 */
private enum class SwipeDirection { LEFT, RIGHT }

/** 滑动时露出的背景：图标 + 文案说明会发生什么（文案随当前状态变，避免猜）。 */
@Composable
private fun SwipeActionBackground(
    modifier: Modifier = Modifier,
    direction: SwipeDirection?,
    isRead: Boolean,
    isStarred: Boolean,
) {
    val (label, icon, tint) = when (direction) {
        SwipeDirection.RIGHT ->
            Triple(
                if (isStarred) stringResource(R.string.star_off) else stringResource(R.string.star_on),
                Lucide.Star,
                radarColors().accent,
            )

        SwipeDirection.LEFT ->
            Triple(
                if (isRead) stringResource(R.string.mark_unread) else stringResource(R.string.mark_read),
                Lucide.Check,
                radarColors().textSecondary,
            )

        null -> return
    }
    // 卡片往左移 → 背景右侧露出 → 内容靠右；反之靠左
    Box(
        modifier = modifier.padding(horizontal = 24.dp),
        contentAlignment = if (direction == SwipeDirection.LEFT) {
            Alignment.CenterEnd
        } else {
            Alignment.CenterStart
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = tint, style = MaterialTheme.typography.labelLarge)
        }
    }
}
