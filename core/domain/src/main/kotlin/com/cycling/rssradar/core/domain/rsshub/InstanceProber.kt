package com.cycling.rssradar.core.domain.rsshub

import com.cycling.rssradar.core.domain.rss.RSSRADAR_USER_AGENT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * RSSHub 实例探活缝：判定「这个 host 活着吗」，返回响应耗时（ms）或 null（不可达）。
 *
 * 从 RssHubInstanceStore 里抽出来：store 是 prefs 的家，网络探测住在里面导致
 * 「选最快可达实例」的纯策略逻辑永远无法脱离真网络做 JVM 测试。抽缝后
 * store 只剩 prefs + 选择策略，测试塞 fake prober 即可；生产装 [HttpHealthzProber]。
 *
 * 判定口径（历史定案，勿收窄）：**拿到任何 HTTP 响应都算活着，包括 404**——
 * 实测 rss.injahow.cn 的 /healthz 是 404 但路由正常出 feed，要求 2xx 等于
 * 把能用的实例判死。真正该判死的是连不上：超时 / DNS 失败 / 拒连。
 */
fun interface InstanceProber {
    suspend fun probe(host: String): Long?
}

/** 默认 adapter：GET {host}/healthz，任何 HTTP 响应即活。 */
class HttpHealthzProber(
    private val timeoutMs: Int = DEFAULT_PROBE_TIMEOUT_MS,
) : InstanceProber {

    override suspend fun probe(host: String): Long? = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val connection = URL(host.trimEnd('/') + "/healthz").openConnection() as HttpURLConnection
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", RSSRADAR_USER_AGENT)
            val code = connection.responseCode
            connection.disconnect()
            if (code > 0) System.currentTimeMillis() - start else null
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val DEFAULT_PROBE_TIMEOUT_MS = 5_000
    }
}
