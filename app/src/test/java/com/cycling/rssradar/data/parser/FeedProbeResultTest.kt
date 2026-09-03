package com.cycling.rssradar.data.parser

import com.cycling.rssradar.data.rss.HttpStatusException
import com.cycling.rssradar.data.rss.HttpTimeoutException
import com.cycling.rssradar.data.rss.HttpUrlFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 锁定 2026-09-03 那个 bug：RSSHub 路由「手机浏览器能打开，App 却报"连不上这个地址"」。
 *
 * 根因是所有 IOException 都被一锅端成 `NetworkError`——实例抓上游站点太慢（读超时）
 * 被说成连不上，用户照着去换实例，而换实例解决不了慢。所以这里的断言只有一条主旨：
 * **每种故障必须分得开，且只有"等响应超时"允许重试。**
 * 任何一条断言失败，那句骗人的文案就回来了。
 */
class FeedProbeResultTest {

    // —— 分类：同一句「连不上这个地址」曾经盖住下面全部五种 ——

    @Test
    fun `读超时单独归类而不是当成连不上`() {
        val result = FeedProbeResult.from(SocketTimeoutException("Read timed out"))
        assertEquals(FeedProbeResult.Timeout(connecting = false), result)
    }

    @Test
    fun `握手阶段超时标记为连接不上`() {
        val result = FeedProbeResult.from(HttpTimeoutException(HttpTimeoutException.Phase.CONNECT))
        assertEquals(FeedProbeResult.Timeout(connecting = true), result)
    }

    @Test
    fun `DNS 失败单独归类`() {
        assertEquals(FeedProbeResult.DnsError, FeedProbeResult.from(UnknownHostException("nope.invalid")))
    }

    @Test
    fun `证书错误单独归类`() {
        assertEquals(FeedProbeResult.CertificateError, FeedProbeResult.from(SSLException("bad cert")))
    }

    @Test
    fun `状态码原样带出不被当成网络故障`() {
        assertEquals(FeedProbeResult.HttpError(429), FeedProbeResult.from(HttpStatusException(429)))
        assertEquals(FeedProbeResult.HttpError(503), FeedProbeResult.from(HttpStatusException(503)))
    }

    @Test
    fun `非法 feed 与连接被拒各归各位`() {
        assertEquals(FeedProbeResult.InvalidFeed, FeedProbeResult.from(IllegalArgumentException("not rss")))
        assertEquals(FeedProbeResult.NetworkError, FeedProbeResult.from(ConnectException("refused")))
        assertEquals(FeedProbeResult.NetworkError, FeedProbeResult.from(IOException("Connection reset")))
    }

    // —— 重试策略：只有「等响应超时」值得再试一次 ——

    @Test
    fun `只有等响应超时才允许重试`() {
        assertTrue(FeedProbeResult.Timeout(connecting = false).isRetryableTimeout)
        assertFalse(FeedProbeResult.Timeout(connecting = true).isRetryableTimeout)
        assertFalse(FeedProbeResult.DnsError.isRetryableTimeout)
        assertFalse(FeedProbeResult.CertificateError.isRetryableTimeout)
        assertFalse(FeedProbeResult.NetworkError.isRetryableTimeout)
        assertFalse(FeedProbeResult.HttpError(503).isRetryableTimeout)
    }

    // —— 阶段分离：HttpUrlFetcher 必须能分辨「连上了在等」和「根本连不上」——
    // 用回环 socket 复现而不碰外网，保证在 CI 上也是确定性的。

    @Test
    fun `已连上但对方不回数据时判定为读超时`() {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val serving = Thread {
            runCatching {
                // 接受连接，然后什么都不发——正是「实例活着但正在抓上游」的形态
                server.accept().use { Thread.sleep(5_000) }
            }
        }.apply { isDaemon = true; start() }
        try {
            val fetcher = HttpUrlFetcher(connectTimeoutMs = 1_000, readTimeoutMs = 400)
            try {
                fetcher.fetch("http://127.0.0.1:${server.localPort}/feed.xml")
                fail("对端不回数据，应当抛超时")
            } catch (e: HttpTimeoutException) {
                assertFalse("已经完成握手，卡的是等响应，必须判 READ 阶段", e.isConnectPhase)
            }
        } finally {
            server.close()
            serving.interrupt()
        }
    }
}
