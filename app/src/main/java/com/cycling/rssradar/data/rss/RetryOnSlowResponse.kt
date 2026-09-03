package com.cycling.rssradar.data.rss

import com.cycling.rssradar.data.parser.FeedProbeResult

/**
 * 执行 [block]，卡在等响应时自动重试，最多 [maxAttempts] 次。
 *
 * 为什么值得重试：RSSHub 公共实例抓一条**缓存未命中**的路由要现抓上游站点，
 * 抓完就进实例缓存，第二次基本秒回——`docs/rsshub-instances.md` 实测同一条路由
 * 「第 1 次读超时 → 第 2 次 1.0s 正常」。2026-09-03 的「浏览器能打开、App 报连不上
 * 这个地址」就是这个：浏览器只不过肯等，App 15s 就掐了。
 *
 * 只重试「等响应超时」：握手超时 / DNS 失败 / 证书错误重试多少次都是同一个结果，
 * 只会让用户白等更久。
 *
 * **抽成顶层函数而不是 FeedRepository 的私有方法，是为了让它可测**——
 * FeedRepository 构造函数就要 Room 的 AppDatabase，塞不进 fake，写在里面的
 * 重试逻辑会永远没有测试覆盖。而重试恰恰是本次修复里真正让订阅成功的那一步。
 */
suspend fun <T> retryOnSlowResponse(
    maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    block: suspend () -> T,
): T {
    var attempt = 1
    while (true) {
        try {
            return block()
        } catch (e: Exception) {
            if (attempt >= maxAttempts || !FeedProbeResult.from(e).isRetryableTimeout) throw e
            attempt++
        }
    }
}

/** 默认只多试一次：第二次命中实例缓存通常 1s 内返回，再多次只是让用户干等。 */
const val DEFAULT_MAX_ATTEMPTS = 2
