package com.cycling.rssradar.ui.article

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 视频画面贴合规则的契约（纯算术，JVM 可测）。
 *
 * 锁的是用户报过的那个 bug 的**规则半边**：全屏页把画面 `fillMaxSize()` 拉满整屏，
 * 手机屏是竖的，横屏视频被纵向拉长——看起来既"竖屏"又变形。规则本身（哪条边是紧的）
 * 不直观，写反了同样是变形，所以在这里钉住两条不变式：
 *
 * 1. 贴合结果的宽高比 = 视频宽高比（不拉伸）；
 * 2. 结果不超出可用框（不裁切，多余的都是黑边）。
 */
class MediaFitTest {

    /** 竖屏手机（360×800）——最常见的可用框。 */
    private val phoneWidth = 360.dp
    private val phoneHeight = 800.dp

    private fun assertFits(availableWidth: Float, availableHeight: Float, ratio: Float) {
        val size = fitMediaSize(availableWidth.dp, availableHeight.dp, ratio)
        assertEquals(ratio, size.width.value / size.height.value, 0.01f)
        assertTrue(
            "贴合结果 ${size.width.value}×${size.height.value} 超出可用框 $availableWidth×$availableHeight",
            size.width.value <= availableWidth + 0.01f && size.height.value <= availableHeight + 0.01f,
        )
    }

    @Test
    fun `landscape video on a portrait phone is bound by width with bars top and bottom`() {
        val size = fitMediaSize(phoneWidth, phoneHeight, 16f / 9f)

        assertEquals(360f, size.width.value, 0.01f)
        assertEquals(202.5f, size.height.value, 0.5f)
    }

    @Test
    fun `portrait video on a portrait phone keeps its own aspect`() {
        val size = fitMediaSize(phoneWidth, phoneHeight, 9f / 16f)

        assertEquals(360f, size.width.value, 0.01f)
        assertEquals(640f, size.height.value, 0.5f)
    }

    @Test
    fun `landscape video on a landscape box is bound by height with bars left and right`() {
        val size = fitMediaSize(800.dp, 360.dp, 16f / 9f)

        assertEquals(640f, size.width.value, 0.5f)
        assertEquals(360f, size.height.value, 0.01f)
    }

    @Test
    fun `portrait video on a landscape box is bound by height`() {
        val size = fitMediaSize(800.dp, 360.dp, 9f / 16f)

        assertEquals(202.5f, size.width.value, 0.5f)
        assertEquals(360f, size.height.value, 0.01f)
    }

    @Test
    fun `aspect ratio never exceeds the available box`() {
        val ratios = listOf(16f / 9f, 4f / 3f, 1f, 3f / 4f, 9f / 16f, 2.39f, 0.5f)
        for (ratio in ratios) {
            assertFits(360f, 800f, ratio)
            assertFits(800f, 360f, ratio)
            assertFits(412f, 892f, ratio)
        }
    }

    @Test
    fun `unknown aspect ratio falls back to the placeholder ratio`() {
        // 尺寸未知时 Media3 报 0：此时按 16:9 摆，拿到真实尺寸后会自己纠正
        assertEquals(
            fitMediaSize(phoneWidth, phoneHeight, 16f / 9f),
            fitMediaSize(phoneWidth, phoneHeight, 0f),
        )
    }

    @Test
    fun `undefined available box is returned untouched`() {
        val unbounded = androidx.compose.ui.unit.Dp.Infinity

        assertEquals(unbounded, fitMediaSize(unbounded, phoneHeight, 16f / 9f).width)
        assertEquals(unbounded, fitMediaSize(phoneWidth, unbounded, 16f / 9f).height)
    }
}
