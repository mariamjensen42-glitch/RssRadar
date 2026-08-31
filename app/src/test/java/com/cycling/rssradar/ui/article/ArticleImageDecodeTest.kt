package com.cycling.rssradar.ui.article

import coil3.size.pxOrElse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图片解码防线回归测试：`Canvas: trying to draw too large(N bytes) bitmap` 崩溃
 * （119537664 bytes ≈ 29.9M px 实案）。
 *
 * 建模假设（Coil 3.3.0 真值，升级时需同步核对）：
 * - `AsyncImage` 默认 Precision.EXACT：inSampleSize 粗解码后会精确缩放到请求尺寸，
 *   **最终 bitmap = 请求尺寸**（clampDecodeSize 的返回值）。
 * - [MAX_DECODE_PIXELS] = 20M px（ARGB ≈ 80MB），低于 RecordingCanvas 的 100MB 上限。
 * - inSampleSize 粗解码的**中间峰值**不受 clamp 控制（粒度 2^k，src 每边 < 2×dst 时
 *   原样解码）——那是堆内存峰值问题，不是 Canvas 绘制上限问题，这里不断言。
 */
class ArticleImageDecodeTest {

    /** RecordingCanvas 单 bitmap 绘制上限（崩溃线：119537664 bytes 之前的软上限 ~100MB）。 */
    private val canvasLimitBytes = 100L * 1024 * 1024

    private val bytesPerPixel = 4L

    /** coil3.size.Size 的 width/height 是 Dimension（Float 封装），转 px Int。 */
    private fun px(d: coil3.size.Dimension): Int = d.pxOrElse { 1 }

    private fun bytesOf(request: coil3.size.Size): Long =
        px(request.width).toLong() * px(request.height) * bytesPerPixel

    @Test
    fun `decode budget keeps final bitmap under canvas limit`() {
        val requests = listOf(
            clampDecodeSize(1260, 12000),   // 正文图：屏宽 × 4000dp(@3x)
            clampDecodeSize(1440, 14400),
            clampDecodeSize(2520, 2640),    // 放大查看：屏幕 2×
            clampDecodeSize(1080, 2400),
        )
        for (request in requests) {
            assertTrue(
                "request=$request decoded=${bytesOf(request)} bytes exceeds canvas limit",
                bytesOf(request) < canvasLimitBytes,
            )
        }
    }

    @Test
    fun `budget shrink leaves ordinary screen-width images unresampled`() {
        // 清晰度回归保护：普通文章图（约屏宽、几千高）inSampleSize 仍应为 1，
        // 全分辨率解码，不被预算压缩误伤。
        // Coil 3.3.0 DecodeUtils.calculateInSampleSize：`(src / dst).takeHighestOneBit()`，
        // 商为 1 时 inSampleSize=1 → 按原图解码。
        val request = clampDecodeSize(1260, 12000)
        assertEquals(1, coilInSampleSize(1260, 2000, px(request.width), px(request.height)))
        assertEquals(1, coilInSampleSize(1080, 3000, px(request.width), px(request.height)))
    }

    @Test
    fun `clamp scales down proportionally and never below one pixel`() {
        assertEquals(coil3.size.Size(1, 1), clampDecodeSize(0, 0))
        val clamped = clampDecodeSize(1260, 12000)
        assertTrue(px(clamped.width).toLong() * px(clamped.height) <= MAX_DECODE_PIXELS)
        // 等比缩放
        assertEquals(
            12000.0 / 1260.0,
            px(clamped.height).toDouble() / px(clamped.width),
            0.02,
        )
        assertTrue(px(clamped.width) >= 1 && px(clamped.height) >= 1)
    }

    /** Coil 3.3.0 DecodeUtils.calculateInSampleSize 源码复刻。 */
    private fun coilInSampleSize(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Int {
        val w = (srcW / dstW).takeHighestOneBit()
        val h = (srcH / dstH).takeHighestOneBit()
        return maxOf(w, h).coerceAtLeast(1)
    }
}
