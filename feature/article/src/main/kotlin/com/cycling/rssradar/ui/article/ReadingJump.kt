package com.cycling.rssradar.ui.article

/**
 * 阅读页右下角「到顶 / 到底」的跳转请求。
 *
 * 带 [seq] 而不只带目标：同一个目标连点两次也要各跳一次 —— 裸目标值不变时下游
 * `LaunchedEffect(key)` 不会重触发，第二次按下去就成了哑键。
 * 视口模式（有图文章，WebView 自己滚）靠它把命令送进 WebView；整页模式直接用 [ratio]。
 */
internal data class JumpRequest(val seq: Long, val target: JumpTarget) {
    /** 目标位置占全文的比例。 */
    val ratio: Float get() = if (target == JumpTarget.TOP) 0f else 1f
}

internal enum class JumpTarget { TOP, BOTTOM }
