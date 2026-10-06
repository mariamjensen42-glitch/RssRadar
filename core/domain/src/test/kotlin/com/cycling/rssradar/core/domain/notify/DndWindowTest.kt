package com.cycling.rssradar.core.domain.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DndWindowTest {

    @Test
    fun `同日区间内为静音`() {
        assertTrue(DndWindow.isQuiet(nowMinute = 10 * 60, startMinute = 9 * 60, endMinute = 17 * 60))
    }

    @Test
    fun `同日区间外不静音`() {
        assertFalse(DndWindow.isQuiet(nowMinute = 8 * 60, startMinute = 9 * 60, endMinute = 17 * 60))
    }

    @Test
    fun `起点包含终点不包含`() {
        assertTrue(DndWindow.isQuiet(nowMinute = 9 * 60, startMinute = 9 * 60, endMinute = 17 * 60))
        assertFalse(DndWindow.isQuiet(nowMinute = 17 * 60, startMinute = 9 * 60, endMinute = 17 * 60))
    }

    @Test
    fun `跨天区间在夜间静音`() {
        assertTrue(DndWindow.isQuiet(nowMinute = 23 * 60, startMinute = 22 * 60, endMinute = 7 * 60))
        assertTrue(DndWindow.isQuiet(nowMinute = 3 * 60, startMinute = 22 * 60, endMinute = 7 * 60))
    }

    @Test
    fun `跨天区间在白天不静音`() {
        assertFalse(DndWindow.isQuiet(nowMinute = 12 * 60, startMinute = 22 * 60, endMinute = 7 * 60))
    }

    @Test
    fun `跨天区间终点不包含`() {
        assertFalse(DndWindow.isQuiet(nowMinute = 7 * 60, startMinute = 22 * 60, endMinute = 7 * 60))
    }

    @Test
    fun `起止相同视为不生效`() {
        assertFalse(DndWindow.isQuiet(nowMinute = 9 * 60, startMinute = 9 * 60, endMinute = 9 * 60))
    }

    @Test
    fun `零点整在跨天区间内算静音`() {
        assertTrue(DndWindow.isQuiet(nowMinute = 0, startMinute = 22 * 60, endMinute = 7 * 60))
    }

    @Test
    fun `分钟数换算到当天内的时刻`() {
        val zone = 8 * 60 * 60 * 1000
        val epoch = 1_700_000_000_000L
        val minute = DndWindow.minuteOfDay(epoch, zone)
        assertTrue(minute in 0 until DndWindow.MINUTES_PER_DAY)
    }

    @Test
    fun `同一时刻换算结果稳定`() {
        val zone = 0
        val epoch = 1_700_000_000_000L
        assertEquals(DndWindow.minuteOfDay(epoch, zone), DndWindow.minuteOfDay(epoch, zone))
    }
}
