package com.cycling.rssradar.ui.article

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工具栏随滚动自动隐藏（ReadYou 差距表 #22）的判据证明。
 *
 * 判据住在纯函数里而不是 Composable 里，就是因为这类「方向 + 死区 + 顶部例外」
 * 的规则靠肉眼在真机上试是试不全的——尤其是抖动不改状态那条。
 */
class AutoHideBarsTest {

    @Test
    fun `auto hide off keeps the bars visible no matter what`() {
        assertTrue(nextBarsVisible(autoHide = false, previous = 0, current = 5_000, wasVisible = true))
        assertTrue(nextBarsVisible(autoHide = false, previous = 0, current = 5_000, wasVisible = false))
    }

    @Test
    fun `scrolling down hides and scrolling up brings them back`() {
        assertFalse("下滚该收起", nextBarsVisible(true, previous = 0, current = 200, wasVisible = true))
        assertTrue("上滚该回来", nextBarsVisible(true, previous = 200, current = 100, wasVisible = false))
    }

    @Test
    fun `jitter inside the slop keeps the current state`() {
        // 惯性回弹 / WebView 亚像素滚动不该让工具栏抽搐
        assertTrue(nextBarsVisible(true, previous = 100, current = 105, wasVisible = true))
        assertFalse(nextBarsVisible(true, previous = 100, current = 105, wasVisible = false))
        assertFalse("小幅上滚不足以唤回", nextBarsVisible(true, previous = 100, current = 95, wasVisible = false))
    }

    @Test
    fun `bars are always visible near the top`() {
        // 刚进文章或滚回顶部时必须能看见返回键
        assertTrue(nextBarsVisible(true, previous = 400, current = 0, wasVisible = false))
        assertTrue(nextBarsVisible(true, previous = 400, current = BARS_TOP_EDGE, wasVisible = false))
    }

    @Test
    fun `switching articles reveals the bars again`() {
        // 换文章会把滚动量归零：从大值掉到 0 是一次大幅上滚
        assertTrue(nextBarsVisible(true, previous = 3_000, current = 0, wasVisible = false))
    }
}
