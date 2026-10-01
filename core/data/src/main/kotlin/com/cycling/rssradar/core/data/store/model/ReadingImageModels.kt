package com.cycling.rssradar.core.data.store.model

/**
 * 阅读页图片显示偏好（图片圆角 / 点击放大，ReadYou 差距表第 19 项）。
 *
 * 默认值 = 引入本功能前的渲染结果（`border-radius:8px` / `RoundedCornerShape(8.dp)`），
 * 老用户升级后视觉不变（与列表显示项 issue #56 同一原则）。
 * 点击放大默认开：ReadYou 也是默认 ON。
 */
data class ReadingImageState(
    val cornerRadius: Int = DEFAULT_CORNER_RADIUS,
    val maximizeOnTap: Boolean = true,
) {
    companion object {
        const val DEFAULT_CORNER_RADIUS = 8
        const val CORNER_RADIUS_MIN = 0
        const val CORNER_RADIUS_MAX = 24
    }
}

fun coerceImageCornerRadius(value: Int): Int =
    value.coerceIn(ReadingImageState.CORNER_RADIUS_MIN, ReadingImageState.CORNER_RADIUS_MAX)
