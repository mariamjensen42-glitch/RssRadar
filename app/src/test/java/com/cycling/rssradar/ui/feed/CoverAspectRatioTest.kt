package com.cycling.rssradar.ui.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 瀑布流封面比例换算（`coverAspectRatio`）的边界守门。 */
class CoverAspectRatioTest {

    @Test
    fun `常规横图原样返回宽高比`() {
        assertEquals(1.5f, coverAspectRatio(1200, 800), 0.0001f)
    }

    @Test
    fun `正方形返回 1`() {
        assertEquals(1f, coverAspectRatio(500, 500), 0.0001f)
    }

    @Test
    fun `超宽图夹到上限——否则卡片被压成一条`() {
        assertEquals(1.6f, coverAspectRatio(3000, 500), 0.0001f)
    }

    @Test
    fun `超长图夹到下限——否则一列被拉成一条`() {
        assertEquals(0.6f, coverAspectRatio(500, 3000), 0.0001f)
    }

    @Test
    fun `零尺寸回落到默认比例而不是 NaN`() {
        // coerceIn 对 NaN 两侧比较都为 false、会原样放行，NaN 一路传到 aspectRatio 就把卡片画没了，
        // 这种坏法不抛错、只在个别源上看得见，所以非正尺寸必须在这里挡住。
        assertEquals(4f / 3f, coverAspectRatio(0, 0), 0.0001f)
        assertEquals(4f / 3f, coverAspectRatio(0, 800), 0.0001f)
        assertEquals(4f / 3f, coverAspectRatio(1200, 0), 0.0001f)
        assertFalse(coverAspectRatio(0, 0).isNaN())
    }

    @Test
    fun `负尺寸同样回落`() {
        assertEquals(4f / 3f, coverAspectRatio(-1, 800), 0.0001f)
        assertEquals(4f / 3f, coverAspectRatio(1200, -1), 0.0001f)
    }

    @Test
    fun `恰好在上下限上的值不被夹`() {
        assertEquals(1.6f, coverAspectRatio(1600, 1000), 0.0001f)
        assertEquals(0.6f, coverAspectRatio(600, 1000), 0.0001f)
    }
}
