package com.cycling.rssradar.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil3.compose.AsyncImagePainter

/**
 * 图片加载态：驱动 shimmer 占位与失败兜底的唯一判定点。
 *
 * 三处图片渲染（列表/网格的 [RadarImage]、阅读页正文图、大图查看器）此前各写了一遍
 * 「Loading 就点亮占位」，也都缺了同一条不变量：**落定后不回退**。
 * 占位层是不透明底（不是半透明扫光），而 coil 在容器尺寸变化、或节点被重建时都会重新请求 ——
 * 只要 loading 回退一次，已经显示出来的图就会在下一帧被整块盖住，
 * 症状正是「图片先显示、一闪又没了」。瀑布流（卡片高度由图片自身比例决定）与
 * 阅读页（正文按需抓取完成后整篇重建）各踩过一次。
 *
 * 成功与失败都算落定：失败还一直盖着占位，用户看到的是一片灰，比看到 fallback 更糟。
 */
class ImageLoadState internal constructor() {

    var loading by mutableStateOf(true)
        private set

    var failed by mutableStateOf(false)
        private set

    private var settled = false

    fun onState(state: AsyncImagePainter.State) {
        if (state is AsyncImagePainter.State.Success || state is AsyncImagePainter.State.Error) {
            settled = true
        }
        loading = !settled
        failed = state is AsyncImagePainter.State.Error
    }
}

/** 按 [key]（通常就是图片地址）绑定的加载态；换图即重置，与各调用点原有的 `remember(url)` 语义一致。 */
@Composable
fun rememberImageLoadState(key: Any?): ImageLoadState = remember(key) { ImageLoadState() }
