package com.cycling.rssradar.core.domain.ai

import com.cycling.rssradar.core.domain.ai.FeedHealthDigest.Status
import com.cycling.rssradar.core.domain.ai.FeedHealthDigest.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 源健康的守门测试。
 *
 * 这里锁的都是"用户看不见但会误解"的边界：把 UNKNOWN 当问题报出去，用户会以为
 * 自己的源坏了；排序换个方向，"失效"就会沉到"降频"下面。
 */
class FeedHealthDigestTest {

    private fun verdict(id: Long, status: Status, at: Long = id) =
        Verdict(feedId = id, status = status, reason = "r$id", advice = "a$id", createdAt = at)

    @Test
    fun `只有失效与降频进入待处理清单`() {
        val issues = FeedHealthDigest.actionable(
            listOf(
                verdict(1, Status.OK),
                verdict(2, Status.UNKNOWN),
                verdict(3, Status.DEGRADED),
                verdict(4, Status.BROKEN),
            ),
        )
        assertEquals(listOf(4L, 3L), issues.map { it.feedId })
    }

    @Test
    fun `失效排在降频之前`() {
        val issues = FeedHealthDigest.actionable(
            listOf(
                verdict(1, Status.DEGRADED, at = 900),
                verdict(2, Status.BROKEN, at = 100),
            ),
        )
        assertEquals(listOf(2L, 1L), issues.map { it.feedId })
    }

    @Test
    fun `同档位最近判定的在前`() {
        val issues = FeedHealthDigest.actionable(
            listOf(
                verdict(1, Status.BROKEN, at = 100),
                verdict(2, Status.BROKEN, at = 900),
            ),
        )
        assertEquals(listOf(2L, 1L), issues.map { it.feedId })
    }

    @Test
    fun `无法识别的档位归 UNKNOWN 并挡在清单外`() {
        assertEquals(Status.UNKNOWN, Status.of("MAYBE_BROKEN"))
        assertEquals(Status.UNKNOWN, Status.of(""))
        assertTrue(FeedHealthDigest.actionable(listOf(verdict(1, Status.of("MAYBE_BROKEN")))).isEmpty())
    }

    @Test
    fun `档位解析忽略大小写与空白`() {
        assertEquals(Status.BROKEN, Status.of(" broken "))
        assertEquals(Status.DEGRADED, Status.of("degraded"))
    }
}
