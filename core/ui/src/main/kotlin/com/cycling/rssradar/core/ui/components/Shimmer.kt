package com.cycling.rssradar.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.cycling.rssradar.core.ui.theme.radarColors

/**
 * 加载中 shimmer 占位：单色线性渐变从左到右平移循环。
 * 只用主题色（surface1 打底 + textSecondary 低透明高光），不引第三方依赖。
 * 调用方决定占位尺寸与裁剪；reduce-motion 场景请自行不启用（动画红线）。
 */
@Composable
fun ShimmerOverlay(modifier: Modifier = Modifier) {
    val base = radarColors().surface1
    val highlight = radarColors().textSecondary.copy(alpha = 0.18f)
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "shimmerShift",
    )
    Box(
        modifier = modifier.drawBehind {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(size.width * shift, 0f),
                    end = Offset(size.width * (shift + 1f), size.height),
                ),
            )
        },
    )
}
