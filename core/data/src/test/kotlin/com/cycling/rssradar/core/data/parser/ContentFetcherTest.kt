package com.cycling.rssradar.core.data.parser

import com.cycling.rssradar.core.model.ExtractionIssue
import com.cycling.rssradar.core.model.FetchFailure

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * [ContentFetcher] 的端到端测试：起一个本机 HTTP 服务器喂样本页，走真实的
 * HttpURLConnection（不是 mock），覆盖四类样本：正常文章 / 分页文章 / 动态加载 / 限流与付费墙。
 *
 * 代理显式设为 [Proxy.NO_PROXY]：否则 JVM 会读系统代理配置，把 127.0.0.1 的请求也送出去。
 */
class ContentFetcherTest {

    private lateinit var server: HttpServer
    private lateinit var cacheDir: File
    private lateinit var logger: RecordingLogger
    private val hits = ConcurrentHashMap<String, AtomicInteger>()

    private val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    private val config = FetchConfig(
        connectTimeoutMs = 3_000,
        readTimeoutMs = 3_000,
        maxAttempts = 3,
        backoffBaseMs = 1L,
        // 直连本机 fixture 服务器，绕开系统代理
        proxy = Proxy.NO_PROXY,
    )

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.start()
        cacheDir = createTempDirectory(prefix = "rssradar-fetch-").toFile()
        logger = RecordingLogger()
    }

    @After
    fun tearDown() {
        server.stop(0)
        cacheDir.deleteRecursively()
    }

    private fun fetcher(): ContentFetcher =
        ContentFetcher(cacheDir = cacheDir, config = config, logger = logger)

    private fun fetch(url: String): FetchOutcome = runBlocking { fetcher().fetch(url) }

    private fun route(path: String, handler: (HttpExchange) -> Unit) {
        server.createContext(path) { exchange ->
            hits.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
            try {
                handler(exchange)
            } finally {
                exchange.close()
            }
        }
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String, headers: Map<String, String> = emptyMap()) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        headers.forEach { (k, v) -> exchange.responseHeaders.add(k, v) }
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun respondRaw(exchange: HttpExchange, status: Int, bytes: ByteArray, contentType: String) {
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun respondEmpty(exchange: HttpExchange, status: Int, headers: Map<String, String> = emptyMap()) {
        headers.forEach { (k, v) -> exchange.responseHeaders.add(k, v) }
        exchange.sendResponseHeaders(status, -1)
    }

    private fun hitsOf(path: String): Int = hits[path]?.get() ?: 0

    private fun paragraph(repeat: Int = 8): String =
        "这是一段用于测试的中文正文，包含足够多的字符以通过完整性阈值，真实文章会有若干这样的段落。".repeat(repeat)

    // ———————————————————————————————————————————————
    // 1. 正常文章
    // ———————————————————————————————————————————————

    @Test
    fun `normal article is fetched complete`() {
        route("/normal") {
            respond(
                it,
                200,
                """
                <html><head><meta property="og:title" content="正常文章"></head><body>
                  <nav>首页 新闻 关于</nav>
                  <article><h1>正常文章</h1><p>${paragraph()}</p><p>${paragraph()}</p></article>
                  <footer>版权所有</footer>
                </body></html>
                """.trimIndent(),
            )
        }

        val outcome = fetch("$baseUrl/normal")

        assertTrue(outcome is FetchOutcome.Success)
        val success = outcome as FetchOutcome.Success
        assertTrue(success.content.isComplete)
        assertEquals(200, success.report.statusCode)
        assertEquals(1, success.report.attempts)
        assertTrue(success.content.contentText.contains("中文正文"))
        assertFalse("页脚不该进正文", success.content.contentText.contains("版权所有"))
        assertEquals("127.0.0.1", success.report.host)
        assertEquals(1, hitsOf("/normal"))
    }

    // ———————————————————————————————————————————————
    // 2. 动态加载
    // ———————————————————————————————————————————————

    @Test
    fun `js rendered page is written but marked incomplete`() {
        val scripts = (1..12).joinToString("\n") { "<script src=\"/chunk-$it.js\"></script>" }
        route("/spa") {
            respond(
                it,
                200,
                """
                <html><body>
                  $scripts
                  <div id="app"></div>
                  <article><p>内容加载中，请稍候。</p></article>
                  <noscript>Please enable JavaScript to view this page.</noscript>
                </body></html>
                """.trimIndent(),
            )
        }

        val outcome = fetch("$baseUrl/spa")

        assertTrue("抓到了内容就要写，但不能假装完整", outcome is FetchOutcome.Success)
        val success = outcome as FetchOutcome.Success
        assertFalse(success.content.isComplete)
        assertEquals(ExtractionIssue.DYNAMIC_RENDER, success.content.issue)
        assertTrue("必须打警告日志", logger.warnings.any { it.contains("正文不完整") })
    }

    // ———————————————————————————————————————————————
    // 3. 限流 / 反爬 / 付费墙
    // ———————————————————————————————————————————————

    @Test
    fun `rate limited request retries and then succeeds`() {
        route("/flaky") {
            if (hitsOf("/flaky") == 1) {
                respondEmpty(it, 429, mapOf("Retry-After" to "0"))
            } else {
                respond(it, 200, """<html><body><article><p>${paragraph()}</p></article></body></html>""")
            }
        }

        val outcome = fetch("$baseUrl/flaky")

        assertTrue(outcome is FetchOutcome.Success)
        assertEquals(2, (outcome as FetchOutcome.Success).report.attempts)
        assertEquals(2, hitsOf("/flaky"))
    }

    @Test
    fun `persistent 429 gives up after max attempts`() {
        route("/limited") { respondEmpty(it, 429, mapOf("Retry-After" to "0")) }

        val outcome = fetch("$baseUrl/limited")

        assertTrue(outcome is FetchOutcome.Failure)
        val failure = outcome as FetchOutcome.Failure
        assertEquals(FetchFailure.HTTP_429, failure.kind)
        assertEquals(3, failure.report.attempts)
        assertEquals(429, failure.report.statusCode)
        assertTrue(logger.warnings.any { it.contains("抓取放弃") })
    }

    @Test
    fun `403 is not retried`() {
        route("/forbidden") { respondEmpty(it, 403) }

        val outcome = fetch("$baseUrl/forbidden")

        assertTrue(outcome is FetchOutcome.Failure)
        assertEquals(FetchFailure.HTTP_403, (outcome as FetchOutcome.Failure).kind)
        assertEquals(1, outcome.report.attempts)
        assertEquals(1, hitsOf("/forbidden"))
    }

    @Test
    fun `404 is not retried`() {
        route("/missing") { respondEmpty(it, 404) }

        val outcome = fetch("$baseUrl/missing")

        assertTrue(outcome is FetchOutcome.Failure)
        assertEquals(FetchFailure.HTTP_404, (outcome as FetchOutcome.Failure).kind)
        assertEquals(1, outcome.report.attempts)
    }

    @Test
    fun `paywall page is written but marked incomplete`() {
        route("/paywall") {
            respond(
                it,
                200,
                """
                <html><body><article>
                  <h1>付费文章</h1><p>本文剩余内容需要订阅后查看。</p>
                  <div class="paywall">开通会员阅读全文</div>
                </article></body></html>
                """.trimIndent(),
            )
        }

        val outcome = fetch("$baseUrl/paywall")

        assertTrue(outcome is FetchOutcome.Success)
        val success = outcome as FetchOutcome.Success
        assertFalse(success.content.isComplete)
        assertEquals(ExtractionIssue.PAYWALL, success.content.issue)
    }

    // ———————————————————————————————————————————————
    // 缓存 / 编码 / 兜底
    // ———————————————————————————————————————————————

    @Test
    fun `cached page is not refetched`() {
        route("/cached") {
            respond(it, 200, """<html><body><article><p>${paragraph()}</p></article></body></html>""")
        }
        val fetcher = fetcher()

        runBlocking { fetcher.fetch("$baseUrl/cached") }
        val second = runBlocking { fetcher.fetch("$baseUrl/cached") }

        assertEquals(1, hitsOf("/cached"))
        assertTrue("缓存命中同样要能提取出正文", second is FetchOutcome.Success)
    }

    @Test
    fun `gbk page is decoded correctly`() {
        val html = "<html><head><meta charset=\"gbk\"></head><body><article><p>${paragraph()}</p></article></body></html>"
        route("/gbk") {
            respondRaw(it, 200, html.toByteArray(Charset.forName("GBK")), "text/html")
        }

        val outcome = fetch("$baseUrl/gbk")

        assertTrue(outcome is FetchOutcome.Success)
        assertTrue("GBK 页面要能解出中文", (outcome as FetchOutcome.Success).content.contentText.contains("中文正文"))
    }

    @Test
    fun `unreachable host reports network failure`() {
        val outcome = fetch("http://127.0.0.1:1/nothing-here")

        assertTrue(outcome is FetchOutcome.Failure)
        assertEquals(FetchFailure.NETWORK, (outcome as FetchOutcome.Failure).kind)
    }

    @Test
    fun `empty page reports extract failure`() {
        route("/empty") { respond(it, 200, "<html><head></head><body></body></html>") }

        val outcome = fetch("$baseUrl/empty")

        assertTrue(outcome is FetchOutcome.Failure)
        assertEquals(FetchFailure.EXTRACT_FAILED, (outcome as FetchOutcome.Failure).kind)
    }

    private class RecordingLogger : FetchLogger {
        val warnings = mutableListOf<String>()

        override fun log(level: FetchLogger.Level, message: String, throwable: Throwable?) {
            if (level != FetchLogger.Level.INFO) warnings += message
        }
    }
}
