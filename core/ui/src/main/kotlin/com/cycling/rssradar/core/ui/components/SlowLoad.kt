package com.cycling.rssradar.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * 首屏加载门槛：加载持续超过 [thresholdMillis] 毫秒仍未结束才返回 true。
 *
 * 本地 Room 查询通常几十毫秒，直接按 loading 画整屏 spinner 会一闪而过；过了门槛才显示，
 * 快查询完全不出现 spinner，慢查询仍有反馈。
 *
 * 坑：调用方必须用返回值截断内容分支（`return@Scaffold` / `return@Column`，或空 `when` 分支），
 * 否则 loading 且未过门槛时会掉进内容分支，漏出一帧 0 值或空态——比 spinner 一闪更难解释。
 */
@Composable
fun rememberSlowLoad(loading: Boolean, thresholdMillis: Long = 200): Boolean {
    var slow by remember { mutableStateOf(false) }

    LaunchedEffect(loading) {
        if (!loading) {
            slow = false
            return@LaunchedEffect
        }
        delay(thresholdMillis)
        slow = true
    }

    return slow
}
