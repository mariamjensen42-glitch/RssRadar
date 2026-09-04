package com.cycling.rssradar.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.cycling.rssradar.core.ui.theme.LocalReducedMotion
import com.cycling.rssradar.core.ui.theme.MotionTokens

/**
 * 按压缩放反馈（docs/motion.md #2）：按下缩到 [pressedScale]，抬起回弹，
 * M3 ripple 保留——位移感交给缩放，着色反馈交给 ripple，各管一半。
 *
 * 只用在列表卡片与主操作按钮（spec 红线：全文 squeeze 会廉价化）。
 * interactionSource 必须与同节点 clickable/combinedClickable 共用同一实例，
 * 否则按压状态传不进来。
 *
 * reduce-motion：跳过缩放（恒为 1f），ripple 仍在——降级是不动，不是没反馈。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val reducedMotion = LocalReducedMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) pressedScale else 1f,
        animationSpec = tween(MotionTokens.DurationMicro, easing = MotionTokens.EasingStandard),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
