package com.cycling.rssradar.ui.article

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 划词工具条落位逻辑（ReaderSelectionBar.selectionBarOffset）。
 *
 * 行为约定：下方放得下 → 顶边贴长按点；放不下 → 翻到上方，底边贴长按点。
 * 水平以长按点为中心并夹在视口内，贴边时靠边对齐而不是溢出。
 */
class SelectionBarOffsetTest {

    private val size = IntSize(width = 200, height = 48)

    @Test
    fun 下方空间足够时顶边贴长按点() {
        val r = selectionBarOffset(
            anchorX = 500f,
            anchorY = 800f,
            toolbarSize = size,
            belowSpace = 300f,
            viewportWidth = 1080f,
        )
        assertEquals(800, r.y)
    }

    @Test
    fun 下方空间不足时翻到上方() {
        val r = selectionBarOffset(
            anchorX = 500f,
            anchorY = 1000f,
            toolbarSize = size,
            belowSpace = 30f,
            viewportWidth = 1080f,
        )
        assertEquals(1000 - size.height, r.y)
    }

    @Test
    fun 水平以长按点为中心() {
        val r = selectionBarOffset(
            anchorX = 540f,
            anchorY = 400f,
            toolbarSize = size,
            belowSpace = 300f,
            viewportWidth = 1080f,
        )
        assertEquals(540 - size.width / 2, r.x)
    }

    @Test
    fun 靠左边缘时夹住不溢出() {
        val r = selectionBarOffset(
            anchorX = 10f,
            anchorY = 400f,
            toolbarSize = size,
            belowSpace = 300f,
            viewportWidth = 1080f,
        )
        assertEquals(0, r.x)
    }

    @Test
    fun 靠右边缘时夹住不溢出() {
        val r = selectionBarOffset(
            anchorX = 1070f,
            anchorY = 400f,
            toolbarSize = size,
            belowSpace = 300f,
            viewportWidth = 1080f,
        )
        assertEquals(1080 - size.width, r.x)
    }

    @Test
    fun 工具条比视口宽时不会算出负偏移() {
        val r = selectionBarOffset(
            anchorX = 100f,
            anchorY = 400f,
            toolbarSize = IntSize(width = 400, height = 48),
            belowSpace = 300f,
            viewportWidth = 1080f,
        )
        assertEquals(0, r.x)
    }
}
