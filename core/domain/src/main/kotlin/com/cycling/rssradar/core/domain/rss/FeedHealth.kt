package com.cycling.rssradar.core.domain.rss

/**
 * 刷新失败的分类（#82 失效源检测）：失效判定与 UI 文案的唯一真相源。
 *
 * 置信度分两档，阈值也分两档——这是 #80 敲定的产品决策：
 * - **高置信**（DNS 解析失败 / 4xx / 证书错误 / 无效 feed）：错误本身是「确定性」的，
 *   连续 2 次即判失效。域名都没了，连 2 次就不是网络抖动。
 * - **低置信**（超时 / 网络错误 / 429 / 5xx）：偶发太常见（1000+ 源里每次刷新
 *   总有几个超时），连续 5 次才判，避免冤枉。
 */
enum class FeedFailureCategory(
    /** 落库值（feeds.failureReason 存这个），解析回枚举靠 [fromStored]。 */
    val stored: String,
    val highConfidence: Boolean,
    /** UI 展示文案（角标），与 FeedProbeResult 的订阅预览文案口径一致。 */
    val label: String,
) {
    DNS("DNS", true, "域名解析失败"),
    HTTP_4XX("HTTP_4XX", true, "站点返回错误"),
    CERTIFICATE("CERTIFICATE", true, "证书校验失败"),
    INVALID_FEED("INVALID_FEED", true, "不是有效的订阅源"),
    TIMEOUT("TIMEOUT", false, "连接或响应超时"),
    NETWORK("NETWORK", false, "网络错误"),
    RATE_LIMITED("RATE_LIMITED", false, "站点限流 (429)"),
    SERVER_ERROR("SERVER_ERROR", false, "服务器错误 (5xx)"),
    ;

    /** 连续失败多少次判失效。 */
    val threshold: Int get() = if (highConfidence) 2 else 5

    companion object {
        /** 探测结果 → 失败分类。Valid 不该进来，防御性归 NETWORK（不参与判定）。 */
        fun from(result: FeedProbeResult): FeedFailureCategory = when (result) {
            is FeedProbeResult.Valid -> NETWORK
            FeedProbeResult.InvalidUrl, FeedProbeResult.InvalidFeed -> INVALID_FEED
            FeedProbeResult.DnsError -> DNS
            FeedProbeResult.CertificateError -> CERTIFICATE
            is FeedProbeResult.HttpError -> when {
                result.code == 429 -> RATE_LIMITED
                result.code in 500..599 -> SERVER_ERROR
                else -> HTTP_4XX
            }
            is FeedProbeResult.Timeout -> TIMEOUT
            FeedProbeResult.NetworkError -> NETWORK
        }

        /** 从落库值还原；null / 未知值返回 null（视为健康，不参与判定）。 */
        fun fromStored(stored: String?): FeedFailureCategory? =
            entries.firstOrNull { it.stored == stored }
    }
}

/**
 * 失效源判定（#80/#82）：纯函数，无依赖。
 *
 * 「失效」不单独落库——它是 consecutiveFailures（连续失败计数）与
 * failureReason（最后一次失败分类）推导出的**状态**：计数达到该分类的
 * 阈值即失效。任何一次成功会把计数清零（FeedDao.recordRefreshSuccess），
 * 失效随之自动解除，不需要第二份可变状态。
 */
object FeedHealth {

    /** 该源当前是否处于失效状态。 */
    fun isUnhealthy(consecutiveFailures: Int, failureReason: String?): Boolean {
        val category = FeedFailureCategory.fromStored(failureReason) ?: return false
        return consecutiveFailures >= category.threshold
    }

    /** 该源当前的失效分类；健康或计数为 0 时返回 null。 */
    fun categoryOf(consecutiveFailures: Int, failureReason: String?): FeedFailureCategory? {
        val category = FeedFailureCategory.fromStored(failureReason) ?: return null
        return category.takeIf { consecutiveFailures >= it.threshold }
    }
}
