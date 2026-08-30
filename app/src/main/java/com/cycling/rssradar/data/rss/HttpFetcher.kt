package com.cycling.rssradar.data.rss

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP 抓取缝：刷新/订阅链路取 feed XML 的唯一入口。
 * 测试塞 fake adapter（本地流或固定响应），刷新链路即可离线复现；
 * 生产装配 [HttpUrlFetcher]。
 */
fun interface HttpFetcher {

    /** 返回响应体流；非 2xx 与网络失败抛 [IOException]，调用方统一按「抓取失败」处理。 */
    @Throws(IOException::class)
    fun fetch(url: String): InputStream
}

/** 默认 adapter：HttpURLConnection + 超时 + UA，行为与原 FeedRepository.fetch 一致。 */
class HttpUrlFetcher(
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    private val userAgent: String = USER_AGENT,
) : HttpFetcher {

    override fun fetch(url: String): InputStream {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", userAgent)
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            throw IOException("HTTP ${connection.responseCode}")
        }
        return connection.inputStream
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000
        const val DEFAULT_READ_TIMEOUT_MS = 15_000
        const val USER_AGENT = "Mozilla/5.0 (Android) RssRadar/1.0"
    }
}
