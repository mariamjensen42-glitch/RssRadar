package com.cycling.rssradar.core.ui.components

import androidx.compose.material3.Slider
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

/**
 * 与上游单一数据源保持同步的滑杆。
 *
 * Material3 1.5 用 SliderState 取代了 value/onValueChange 重载，而 rememberSliderState 只取一次
 * 初值：外部值变化（播放进度推进、样式重置、换文章）必须显式回写，否则滑块停在旧位置。
 * 这层同步收在这里一处，调用点的写法与旧重载保持一致。
 */
@Composable
fun SyncedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val state = rememberSliderState(value = value.coerceIn(valueRange), trackRange = valueRange)
    LaunchedEffect(value) {
        val target = value.coerceIn(valueRange)
        if (state.value != target) state.value = target
    }
    Slider(
        state = state,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        modifier = modifier,
    )
}
