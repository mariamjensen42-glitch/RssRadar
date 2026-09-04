package com.cycling.rssradar.core.domain.rss

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 锁定「等响应超时自动重试一次」——2026-09-03 修复里真正让订阅成功的那一步。
 *
 * RSSHub 抓一条缓存未命中的路由要现抓上游站点，第一次常常超过读超时，
 * 抓完就进实例缓存，第二次秒回。不重试，用户就只能看到失败。
 */
class RetryOnSlowResponseTest {

    @Test
    fun `读超时会重试一次然后成功`() {
        var calls = 0
        val result = runBlocking {
            retryOnSlowResponse {
                calls++
                // 第一次模拟实例正在现抓上游，第二次命中缓存
                if (calls == 1) throw SocketTimeoutException("Read timed out")
                "feed xml"
            }
        }
        assertEquals("feed xml", result)
        assertEquals(2, calls)
    }

    @Test
    fun `重试次数用尽后抛出原异常`() {
        var calls = 0
        runBlocking {
            try {
                retryOnSlowResponse {
                    calls++
                    throw SocketTimeoutException("Read timed out")
                }
                fail("一直超时，应当抛出")
            } catch (e: SocketTimeoutException) {
                assertEquals("Read timed out", e.message)
            }
        }
        assertEquals(2, calls)
    }

    @Test
    fun `握手超时不重试`() {
        var calls = 0
        runBlocking {
            try {
                retryOnSlowResponse {
                    calls++
                    throw HttpTimeoutException(HttpTimeoutException.Phase.CONNECT)
                }
                fail("应当抛出")
            } catch (e: HttpTimeoutException) {
                assertTrue(e.isConnectPhase)
            }
        }
        assertEquals("连都连不上，重试没有意义", 1, calls)
    }

    @Test
    fun `DNS 失败与证书错误都不重试`() {
        var dnsCalls = 0
        runBlocking {
            try {
                retryOnSlowResponse { dnsCalls++; throw UnknownHostException("nope.invalid") }
                fail("应当抛出")
            } catch (_: UnknownHostException) {
            }
        }
        assertEquals(1, dnsCalls)
    }

    @Test
    fun `第一次就成功时不发第二次请求`() {
        var calls = 0
        runBlocking { retryOnSlowResponse { calls++; "ok" } }
        assertEquals(1, calls)
    }

    @Test
    fun `非超时异常立即抛出不重试`() {
        var calls = 0
        runBlocking {
            try {
                retryOnSlowResponse { calls++; throw HttpStatusException(404) }
                fail("应当抛出")
            } catch (e: HttpStatusException) {
                assertEquals(404, e.code)
            }
        }
        assertEquals(1, calls)
    }
}
