package com.cycling.rssradar.data.parser

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
}
