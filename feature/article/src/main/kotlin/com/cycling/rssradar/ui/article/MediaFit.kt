package com.cycling.rssradar.ui.article

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize

/**
 * 视频尺寸未知前的占位宽高比（绝大多数网络视频是 16:9，猜错的代价只是一次布局跳动）。
 */
internal const val MEDIA_PLACEHOLDER_ASPECT = 16f / 9f

/**
 * 把画面按比例贴合进 [availableWidth]×[availableHeight] 的可用框：以**较紧的一边**为准，
 * 另一维按比例缩短，余下的交给外层居中的黑边。
 *
 * 这一步不能省：`PlayerSurface` 只负责"给一块画布"，**它不做宽高比适配**——`PlayerView`
 * 内部那个 `AspectRatioFrameLayout` 在 Compose 版里得调用方自己补。直接 `fillMaxSize()`
 * 的结果是全屏页把画面拉满整屏：手机屏是竖的，横屏视频于是被纵向拉长，看起来既"竖屏"又变形。
 *
 * [aspectRatio] 未就绪（≤0）时按 [MEDIA_PLACEHOLDER_ASPECT] 算；可用框尺寸不确定（放进
 * unbounded 容器）时原样返回——算不出来的比例不如不猜。
 *
 * **本文件刻意只依赖 `Dp`/`DpSize`**：它是 `app/src/test` 里的守门测试直接调用的纯算术，
 * 一旦牵扯 Compose 组件或 Media3 类型，JVM 单测就会在类加载阶段踩 NoClassDefFoundError
 * （ui-graphics 的 `Color.hsl` 就是先例）。布局胶水在 `ArticleMediaPlayer.kt`。
 *
 * public 是为了让 `app/src/test` 能锁住这段算术（哪条边是紧的并不直观，写反同样是变形）。
 */
fun fitMediaSize(availableWidth: Dp, availableHeight: Dp, aspectRatio: Float): DpSize {
    val w = availableWidth.value
    val h = availableHeight.value
    if (!w.isFinite() || !h.isFinite() || w <= 0f || h <= 0f) {
        return DpSize(availableWidth, availableHeight)
    }
    val ratio = aspectRatio.takeIf { it > 0f } ?: MEDIA_PLACEHOLDER_ASPECT
    // 可用框比画面"更宽"⇒ 高度是紧的一边（上下的黑边），否则宽度是紧的一边（左右的黑边）
    return if (w / h > ratio) {
        DpSize(availableHeight * ratio, availableHeight)
    } else {
        DpSize(availableWidth, availableWidth / ratio)
    }
}
