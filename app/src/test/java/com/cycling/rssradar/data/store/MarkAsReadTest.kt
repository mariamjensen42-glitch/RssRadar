package com.cycling.rssradar.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [MarkAsReadCondition] 的时间基准单测：天数换算与「全部」的无 cutoff 语义。 */
class MarkAsReadTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `cutoff - one day is exactly 24 hours ago`() {
        assertEquals(now - 24L * 60 * 60 * 1000, MarkAsReadCondition.ONE_DAY.cutoffMillis(now))
    }

    @Test
    fun `cutoff - three and seven days scale linearly`() {
        val day = 24L * 60 * 60 * 1000
        assertEquals(now - 3 * day, MarkAsReadCondition.THREE_DAYS.cutoffMillis(now))
        assertEquals(now - 7 * day, MarkAsReadCondition.SEVEN_DAYS.cutoffMillis(now))
    }

    @Test
    fun `cutoff - all has no cutoff`() {
        assertNull(MarkAsReadCondition.ALL.cutoffMillis(now))
    }

    @Test
    fun `conditions are ordered from newest to widest`() {
        val days = MarkAsReadCondition.entries.map { it.days }
        assertEquals(listOf(1, 3, 7, null), days)
    }
}
