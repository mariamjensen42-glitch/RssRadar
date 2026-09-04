package com.cycling.rssradar.data.rsshub

import com.cycling.rssradar.core.domain.rsshub.RssHubRoutes
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * RSSHub 实例管理与可达性探测。
 *
 * 背景：实测 rsshub.app 官方主站、docs、官方镜像在部分网络环境下完全不可达
 * （GitHub/常规源正常）——实例写死等于首次使用即坏，见 issue #14。
 * 策略：内置镜像列表 + 并发探测选首个可达 + 用户自定义实例优先。
 */
class RssHubInstanceStore(private val prefs: SharedPreferences, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {

    /** 用户手动设置的实例。空表示未设置（用探测或默认）。 */
    var customHost: String?
        get() = prefs.getString(KEY_CUSTOM_HOST, null)
        set(value) {
            prefs.edit().putString(KEY_CUSTOM_HOST, value?.trim()?.trimEnd('/')).apply()
        }

    /** 当前应使用的实例：自定义 > 上次探测可用 > 默认。 */
    fun currentOrDefault(): String =
        customHost ?: prefs.getString(KEY_LAST_AVAILABLE, null)
            ?: RssHubRoutes.DEFAULT_HOST

    /** 记住探测结果，避免每次启动都全量探测。 */
    suspend fun refreshAvailableHost(): String? {
        val available = detectFirstAvailable() ?: return null
        prefs.edit().putString(KEY_LAST_AVAILABLE, available).apply()
        return available
    }

    /** 主机是否活着。判定同 [probeLatency]：拿到任何 HTTP 响应都算，包括 404。 */
    suspend fun isReachable(host: String): Boolean = probeLatency(host) != null

    /**
     * 并发探测内置镜像 + 自定义实例，返回**响应最快的**可达者。
     *
     * 选最快而不是按列表顺序选第一个：实测同一批实例耗时能从 0.2s 差到 2.4s，
     * 固定顺序等于让所有人都挤在第一台、慢的照样被选中；而实例状态随时在变
     * （rsshub.rssforever.com 上午 0.8s 出 feed，下午同一路由直接超时），
     * 一次探测的快慢比写在代码里的名次更可信。
     */
    suspend fun detectFirstAvailable(): String? = coroutineScope {
        val candidates = (customHost?.let { listOf(it) }.orEmpty() + BUILTIN_INSTANCES).distinct()
        candidates.map { host ->
            async(ioDispatcher) { host to probeLatency(host) }
        }.awaitAll()
            .filter { it.second != null }
            .minByOrNull { it.second!! }
            ?.first
    }

    /** 可达即探活耗时（ms），不可达返回 null。 */
    private suspend fun probeLatency(host: String): Long? = withContext(ioDispatcher) {
        val start = System.currentTimeMillis()
        try {
            val connection = URL(host.trimEnd('/') + "/healthz").openConnection() as HttpURLConnection
            connection.connectTimeout = PROBE_TIMEOUT_MS
            connection.readTimeout = PROBE_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            connection.disconnect()
            // 拿到任何 HTTP 响应都算活着，包括 404。判定放宽的原因：实测
            // rss.injahow.cn 的 /healthz 是 404，但它的 /zhihu/daily 正常返回
            // 200 的 feed——要求 2xx 等于把一个能用的实例判死。真正该判死的
            // 是连不上：超时 / DNS 失败 / 拒连。
            if (code > 0) System.currentTimeMillis() - start else null
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val KEY_CUSTOM_HOST = "rsshub_custom_host"
        private const val KEY_LAST_AVAILABLE = "rsshub_last_available_host"
        private const val PROBE_TIMEOUT_MS = 5_000
        private const val USER_AGENT = "Mozilla/5.0 (Android) RssRadar/1.0"

        /**
         * 内置公共实例。
         *
         * 来源：RSSHub 官方文档「公共实例」页（docs.rsshub.app 现行列表 +
         * rsshub.netlify.app 旧列表），2026-09-02 逐个实测 `/zhihu/daily`
         * 能否真返回 feed 后筛选——29 个候选里只有下面这批出得来 feed，
         * 其余要么 DNS 直接失败、要么 502/523、要么返回 HTML 而不是 RSS。
         * 实测记录见 `docs/rsshub-instances.md`。
         *
         * 官方两个放最前（语义优先级，且在部分网络下它们才是通的——本机
         * rsshub.app 完全不可达，不代表所有用户都不可达）；其余按实测
         * 出 feed 的耗时从快到慢排。**顺序只是并列候选的展示次序，实际选中
         * 哪个由 [detectFirstAvailable] 按探测耗时决定。**
         */
        val BUILTIN_INSTANCES = listOf(
            // 官方
            "https://rsshub.app",
            "https://rsshub.rssforever.com",
            // 实测能出 feed，按耗时排序
            "https://hub.slarker.me",
            "https://rss.injahow.cn",
            "https://rsshub.liumingye.cn",
            "https://rsshub.ktachibana.party",
            "https://rsshub.isrss.com",
            "https://rsshub.woodland.cafe",
            "https://rsshub.umzzz.com",
            "https://rsshub-balancer.virworks.moe",
        )
    }
}
