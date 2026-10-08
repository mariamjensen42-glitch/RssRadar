package com.cycling.rssradar.ui.article

/**
 * 阅读页「到顶 / 到底 / 跳到某一节」的跳转请求。
 *
 * 带 [seq] 而不只带目标：同一个目标连点两次也要各跳一次 —— 裸目标值不变时下游
 * `LaunchedEffect(key)` 不会重触发，第二次按下去就成了哑键。
 * 视口模式（有图文章，WebView 自己滚）靠它把命令送进 WebView；整页模式直接用 [ratio]。
 *
 * [JumpTarget.RATIO] 是给「大纲跳段」用的。注意比例只能放在 [ratioOverride] 里、
 * **不能**拿它当判重依据：连续点同一节时比例相同，按比例判重会把第二次点吞掉。
 */
internal data class JumpRequest(
    val seq: Long,
    val target: JumpTarget,
    /** 目标占全文的比例，仅 [JumpTarget.RATIO] 用得上。 */
    val ratioOverride: Float? = null,
) {
    /** 目标位置占全文的比例，两种滚动宿主都吃这一个值。 */
    val ratio: Float
        get() = when (target) {
            JumpTarget.TOP -> 0f
            JumpTarget.BOTTOM -> 1f
            JumpTarget.RATIO -> (ratioOverride ?: 0f).coerceIn(0f, 1f)
        }
}

internal enum class JumpTarget { TOP, BOTTOM, RATIO }
