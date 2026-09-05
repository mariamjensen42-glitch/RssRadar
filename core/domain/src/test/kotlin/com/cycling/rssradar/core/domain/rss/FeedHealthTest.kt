package com.cycling.rssradar.core.domain.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** FeedFailureCategory / FeedHealth 单测（#82）：双阈值判定是产品决策，必须钉死。 */
class FeedHealthTest {

    // —— 分类映射：异常 → FeedProbeResult → FeedFailureCategory ——

    @Test
    fun `HttpStatusException 按状态码分档`() {
        assertEquals(FeedFailureCategory.RATE_LIMITED, FeedFailureCategory.from(FeedProbeResult.HttpError(429)))
        assertEquals(FeedFailureCategory.SERVER_ERROR, FeedFailureCategory.from(FeedProbeResult.HttpError(503)))
        assertEquals(FeedFailureCategory.HTTP_4XX, FeedFailureCategory.from(FeedProbeResult.HttpError(404)))
    }

    @Test
    fun `确定性错误归高置信`() {
        assertEquals(FeedFailureCategory.DNS, FeedFailureCategory.from(FeedProbeResult.DnsError))
        assertEquals(FeedFailureCategory.CERTIFICATE, FeedFailureCategory.from(FeedProbeResult.CertificateError))
        assertEquals(FeedFailureCategory.INVALID_FEED, FeedFailureCategory.from(FeedProbeResult.InvalidFeed))
    }

    @Test
    fun `瞬时错误归低置信`() {
        assertEquals(FeedFailureCategory.TIMEOUT, FeedFailureCategory.from(FeedProbeResult.Timeout(connecting = true)))
        assertEquals(FeedFailureCategory.NETWORK, FeedFailureCategory.from(FeedProbeResult.NetworkError))
    }

    // —— 阈值：高置信 2 次、低置信 5 次（#80 定稿） ——

    @Test
    fun `阈值分档`() {
        assertEquals(2, FeedFailureCategory.DNS.threshold)
        assertEquals(2, FeedFailureCategory.HTTP_4XX.threshold)
        assertEquals(5, FeedFailureCategory.TIMEOUT.threshold)
        assertEquals(5, FeedFailureCategory.NETWORK.threshold)
    }

    // —— FeedHealth 判定 ——

    @Test
    fun `高置信连续2次即失效`() {
        assertTrue(FeedHealth.isUnhealthy(2, FeedFailureCategory.DNS.stored))
        assertFalse(FeedHealth.isUnhealthy(1, FeedFailureCategory.DNS.stored))
    }

    @Test
    fun `低置信连续4次仍不失效`() {
        assertFalse(FeedHealth.isUnhealthy(4, FeedFailureCategory.TIMEOUT.stored))
        assertTrue(FeedHealth.isUnhealthy(5, FeedFailureCategory.TIMEOUT.stored))
    }

    @Test
    fun `未知原因或零计数视为健康`() {
        assertFalse(FeedHealth.isUnhealthy(99, null))
        assertFalse(FeedHealth.isUnhealthy(99, "NOT_A_CATEGORY"))
        assertFalse(FeedHealth.isUnhealthy(0, FeedFailureCategory.DNS.stored))
        assertNull(FeedHealth.categoryOf(1, null))
    }

    @Test
    fun `categoryOf 与 isUnhealthy 口径一致`() {
        assertEquals(FeedFailureCategory.RATE_LIMITED, FeedHealth.categoryOf(5, FeedFailureCategory.RATE_LIMITED.stored))
        assertNull(FeedHealth.categoryOf(4, FeedFailureCategory.RATE_LIMITED.stored))
    }
}
