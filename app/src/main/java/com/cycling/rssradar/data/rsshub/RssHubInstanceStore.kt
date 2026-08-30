package com.cycling.rssradar.data.rsshub

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
class RssHubInstanceStore(prefs: SharedPreferences, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {

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

    /**
     * 并发探测内置镜像 + 自定义实例，按列表顺序返回首个可达者。
     * 探测端点用 /healthz（RSSHub 内置健康检查），比抓首页便宜且语义明确。
     */
    suspend fun detectFirstAvailable(): String? = coroutineScope {
        val candidates = (customHost?.let { listOf(it) }.orEmpty() + BUILTIN_INSTANCES).distinct()
        candidates.map { host ->
            async(ioDispatcher) { host to isReachable(host) }
        }.awaitAll().firstOrNull { it.second }?.first
    }

    suspend fun isReachable(host: String): Boolean = withContext(ioDispatcher) {
        try {
            val connection = URL(host.trimEnd('/') + "/healthz").openConnection() as HttpURLConnection
            connection.connectTimeout = PROBE_TIMEOUT_MS
            connection.readTimeout = PROBE_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            connection.disconnect()
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val KEY_CUSTOM_HOST = "rsshub_custom_host"
        private const val KEY_LAST_AVAILABLE = "rsshub_last_available_host"
        private const val PROBE_TIMEOUT_MS = 5_000
        private const val USER_AGENT = "Mozilla/5.0 (Android) RssRadar/1.0"

        /** 内置公共实例。官方在前，镜像在后；顺序即优先级。 */
        val BUILTIN_INSTANCES = listOf(
            "https://rsshub.app",
            "https://rsshub.rssforever.com",
            "https://rss.injahow.cn",
        )
    }
}
