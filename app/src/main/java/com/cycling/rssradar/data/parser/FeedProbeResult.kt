package com.cycling.rssradar.data.parser

/** 抓取 + 解析的纯数据结果，与 UI 状态解耦。 */
sealed interface FeedProbeResult {
    data class Valid(val articleCount: Int) : FeedProbeResult
    data object InvalidUrl : FeedProbeResult
    data object InvalidFeed : FeedProbeResult
    data object NetworkError : FeedProbeResult
}
