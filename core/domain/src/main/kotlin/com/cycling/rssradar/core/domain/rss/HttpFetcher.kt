package com.cycling.rssradar.core.domain.rss

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * 全应用统一的 UA 常量：feed 抓取、图标抓取、实例探活共用一条缝的同一份配置。
 * 此前三处各写一份字面量，改 UA / 加代理要改三个文件——locality 事故。
 *
 * 用真实浏览器 UA 而非自报家门（"RssRadar/1.0"）：大量站点（Cloudflare 及各类反爬
 * 中间件）对陌生客户端 UA 直接回 403/503，而 4xx 属高置信失效、连续 2 次就把源判死
 * ——伪装成浏览器是抓取器的通行证，不是欺骗。对标 ReadYou（OkHttp + 浏览器 UA）。
 */
const val RSSRADAR_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

/** 告诉服务器我们接受什么：feed MIME 优先，xml/任意兜底。部分 CDN 按协商头分流。 */
const val RSSRADAR_ACCEPT = "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8"

/**
 * URL 规范化（订阅链路与图标抓取共用）：补 https 前缀、解析合法性校验。
 * 两处各写一份同样的私有函数，一处修 bug 另一处必然漏——沉到缝的这一侧。
 */
fun normalizeHttpUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
    return try {
        URL(withScheme).toString().takeIf { it.startsWith("http") }
    } catch (_: Exception) {
        null
    }
}

/**
 * HTTP 抓取缝：刷新/订阅链路取 feed XML 的唯一入口。
 * 测试塞 fake adapter（本地流或固定响应），刷新链路即可离线复现；
 * 生产装配 [HttpUrlFetcher]。
 */
fun interface HttpFetcher {

    /** 返回响应体流；非 2xx 抛 [HttpStatusException]（带状态码），网络失败抛 [IOException]。 */
    @Throws(IOException::class)
    fun fetch(url: String): InputStream
}

/**
 * 非 2xx 响应。继承 [IOException] 是为了不改动既有调用方（刷新链路一律按失败处理，
 * 本来就该 catch IOException）；需要区分状态码的地方（加订阅预览）再单独 catch。
 */
class HttpStatusException(val code: Int) : IOException("HTTP $code")

/**
 * 条件请求（HTTP 缓存协商）的结果。
 *
 * RSS 协议没有源级增量——想知道「源变没变」只能整包下载。唯一的合法捷径是
 * HTTP 缓存协商：带上次的 ETag / Last-Modified 发 If-None-Match / If-Modified-Since，
 * 服务器没更新就回 304，下载、解析、写库三段开销全省。
 */
sealed interface ConditionalFetchResult {

    /** 304 Not Modified：源自上次成功刷新后未变。 */
    data object NotModified : ConditionalFetchResult

    /** 200：响应体 + 本轮协商凭证（服务器没回 ETag/Last-Modified 就是 null）。 */
    data class Modified(
        val body: InputStream,
        val etag: String?,
        val lastModified: String?,
    ) : ConditionalFetchResult
}

/**
 * 条件请求缝：刷新链路专用，与 [HttpFetcher] 刻意分开——
 * 订阅预览等旧调用方语义不变（无条件、非 2xx 即异常），fake 也互不干扰。
 */
fun interface ConditionalHttpFetcher {

    /** 携带协商凭证请求 [url]；304 返回 [ConditionalFetchResult.NotModified]，网络失败抛 [IOException]。 */
    @Throws(IOException::class)
    fun fetchConditional(url: String, etag: String?, lastModified: String?): ConditionalFetchResult
}

/**
 * 超时，并带出卡在哪个阶段。
 *
 * HttpURLConnection 对「连接超时」和「读取超时」抛的是**同一个** `SocketTimeoutException`，
 * 只在 message 文本里区分，靠字符串判断太脆。这里把 `connect()` 与 `responseCode` 拆成
 * 两段、用标志位记录进度，把阶段用类型带出去。
 *
 * 值得这么麻烦是因为两者的处置完全相反：卡在握手是真连不上（换实例/查网络），
 * 卡在等响应是对端慢（RSSHub 冷路由要现抓上游站点，等一等就出来了）。
 * 混成一句「连不上这个地址」会让用户去换实例——而换实例解决不了慢。
 */
class HttpTimeoutException(val phase: Phase) : IOException("timeout at $phase") {

    enum class Phase { CONNECT, READ }

    /** true = 卡在 TCP/TLS 握手（真连不上）；false = 卡在等响应（对端慢）。 */
    val isConnectPhase: Boolean get() = phase == Phase.CONNECT
}

/** 默认 adapter：HttpURLConnection + 超时 + UA，行为与原 FeedRepository.fetch 一致。 */
class HttpUrlFetcher(
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    private val userAgent: String = RSSRADAR_USER_AGENT,
) : HttpFetcher, ConditionalHttpFetcher {

    override fun fetch(url: String): InputStream {
        val (connection, code) = request(url, etag = null, lastModified = null)
        if (code !in 200..299) {
            release(connection)
            throw HttpStatusException(code)
        }
        return connection.inputStream
    }

    override fun fetchConditional(url: String, etag: String?, lastModified: String?): ConditionalFetchResult {
        val (connection, code) = request(url, etag, lastModified)
        if (code == 304) {
            release(connection)
            return ConditionalFetchResult.NotModified
        }
        if (code !in 200..299) {
            release(connection)
            throw HttpStatusException(code)
        }
        return ConditionalFetchResult.Modified(
            body = connection.inputStream,
            etag = connection.getHeaderField("ETag"),
            lastModified = connection.getHeaderField("Last-Modified"),
        )
    }

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Accept", RSSRADAR_ACCEPT)
        return connection
    }

    /**
     * 发请求并返回「连接 + 状态码」，重定向在本层消化。
     *
     * HttpURLConnection 的 instanceFollowRedirects **不跨协议**（http↔https 互跳直接把
     * 3xx 交回来，底层抛 IOException）——而大量站点把 http 首页/feed 301 到 https，
     * 原样放行就是「浏览器能打开、App 失效」。所以这里手动跟随：每次重定向按
     * RFC 3986 相对当前 URL 解析 Location（协议切换是本逻辑存在的理由），
     * 同协议重定向仍由 instanceFollowRedirects 先吃掉，本循环只是兜底。
     * 协商凭证在重定向后原样重发：对最终 URL 做条件协商语义不变。
     */
    private fun request(url: String, etag: String?, lastModified: String?): Pair<HttpURLConnection, Int> {
        var current = url
        var redirects = 0
        while (true) {
            val connection = open(current)
            etag?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("If-None-Match", it) }
            lastModified?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("If-Modified-Since", it) }
            val code = connectPhaseAware(connection)
            val isRedirect = code == 301 || code == 302 || code == 303 || code == 307 || code == 308
            if (isRedirect && redirects < MAX_REDIRECTS) {
                val location = connection.getHeaderField("Location")
                release(connection)
                if (location.isNullOrBlank()) throw HttpStatusException(code)
                current = URL(URL(current), location).toString()
                redirects++
            } else {
                return connection to code
            }
        }
    }

    /**
     * 分两段：先握手（connectTimeout 生效），再等响应头（readTimeout 生效）。
     * 合在一起就只能拿到一个分不清阶段的 SocketTimeoutException。
     */
    private fun connectPhaseAware(connection: HttpURLConnection): Int {
        var connected = false
        return try {
            connection.connect()
            connected = true
            connection.responseCode
        } catch (e: SocketTimeoutException) {
            release(connection)
            throw HttpTimeoutException(
                if (connected) HttpTimeoutException.Phase.READ else HttpTimeoutException.Phase.CONNECT,
            )
        }
    }

    /** 读完再关 errorStream：不碰它的话 gzip Inflater 要等 GC 才 end（真机实测 ×N）。 */
    private fun release(connection: HttpURLConnection) {
        runCatching { connection.errorStream?.close() }
        connection.disconnect()
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000

        /**
         * 20s 而不是 15s：RSSHub 公共实例抓一条**缓存未命中**的路由要现抓上游站点，
         * 15s 实测经常被掐断（`docs/rsshub-instances.md`：同一条路由「读超时 → 1.0s 正常」）。
         * 掐断的后果不是慢，是订阅直接失败——所以宁可多等 5s。
         */
        const val DEFAULT_READ_TIMEOUT_MS = 20_000

        /** 兼容旧引用；真身是顶层 [RSSRADAR_USER_AGENT]。 */
        const val USER_AGENT = RSSRADAR_USER_AGENT

        /** 手动重定向上限：与浏览器一致，防循环 301。 */
        const val MAX_REDIRECTS = 5
    }
}
