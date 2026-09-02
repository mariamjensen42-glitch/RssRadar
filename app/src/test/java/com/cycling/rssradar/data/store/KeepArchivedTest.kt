package com.cycling.rssradar.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** KeepArchived.cutoffMillis 纯函数（issue #57）。 */
class KeepArchivedTest {

    @Test
    fun `ALWAYS returns null - never cleans up`() {
        assertNull(KeepArchived.ALWAYS.cutoffMillis(1_000_000L))
    }

    @Test
    fun `cutoff is now minus retention days`() {
        val now = 1_700_000_000_000L
        assertEquals(now - 86_400_000L, KeepArchived.ONE_DAY.cutoffMillis(now))
        assertEquals(now - 7 * 86_400_000L, KeepArchived.ONE_WEEK.cutoffMillis(now))
        assertEquals(now - 30 * 86_400_000L, KeepArchived.ONE_MONTH.cutoffMillis(now))
    }
}
