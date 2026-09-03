package com.cycling.rssradar.data.parser

import com.cycling.rssradar.data.rss.HttpStatusException
import com.cycling.rssradar.data.rss.HttpTimeoutException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** 抓取 + 解析的纯数据结果，与 UI 状态解耦。 */
sealed interface FeedProbeResult {
    data class Valid(val articleCount: Int) : FeedProbeResult
    data object InvalidUrl : FeedProbeResult
    data object InvalidFeed : FeedProbeResult
    data object NetworkError : FeedProbeResult

    /**
     * 服务端回了响应，但不是 2xx（[code] 是真实状态码）。
     *
     * 单独拎出来是因为 RSSHub 公共实例最常见的失败**不是网络不通**：404 是参数不对或
     * 路由没了、429 是限流、5xx 是实例自己挂了。全塞进 NetworkError 只会得到一句
     * 「请检查网络」——用户网络好好的，于是一头雾水。
     */
    data class HttpError(val code: Int) : FeedProbeResult

    /**
     * 超时。[connecting] = 卡在 TCP/TLS 握手（真连不上），false = 卡在等响应（对端慢）。
     *
     * 拆开同样是 2026-09-03 那个「浏览器能打开、App 报连不上」的教训：慢和连不上是
     * 两回事，处置相反（一个该等，一个该换），混成一句文案等于骗用户。
     */
    data class Timeout(val connecting: Boolean) : FeedProbeResult

    /** DNS 解析失败：域名不存在，或当前网络的 DNS 服务解析不了它。 */
    data object DnsError : FeedProbeResult

    /** TLS 校验失败：自建实例常用自签/过期/域名不匹配的证书，Android 一律不信任。 */
    data object CertificateError : FeedProbeResult

    /**
     * 只有「等响应超时」值得重试（订阅预览链路据此自动重试一次）：
     * 握手超时 / DNS 失败 / 证书错误重试多少次都是同一个结果，只会让用户白等。
     */
    val isRetryableTimeout: Boolean
        get() = this is Timeout && !connecting

    companion object {
        /**
         * 抓取异常 → 探测结果。分类只有这一份，调用方不各写 catch 链。
         *
         * 曾经所有 IOException 一锅端成 NetworkError，于是「实例抓上游太慢（读超时）」
         * 被报成「连不上这个地址」——用户照着去换实例，而换实例解决不了慢。
         *
         * 顺序即优先级：[HttpStatusException] 和 [HttpTimeoutException] 都是 IOException
         * 的子类，必须先于 IOException 命中；[SSLException] / [UnknownHostException] 同理。
         */
        fun from(e: Throwable): FeedProbeResult = when (e) {
            is IllegalArgumentException -> InvalidFeed
            is HttpStatusException -> HttpError(e.code)
            is HttpTimeoutException -> Timeout(connecting = e.isConnectPhase)
            // 响应体读取超时：发生在解析读流时，不在 fetch 里，没有阶段信息可带
            is SocketTimeoutException -> Timeout(connecting = false)
            is UnknownHostException -> DnsError
            is SSLException -> CertificateError
            is IOException -> NetworkError
            else -> NetworkError
        }
    }
}
