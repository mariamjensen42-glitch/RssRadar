package com.cycling.rssradar.di

/**
 * 新文章通知用例（#31）的类型别名式接口：把「同步起点时间戳 → 发通知」注入 [com.cycling.rssradar.sync.AutoSync]，
 * 让同步这条链路上不出现 Android 通知代码（Worker 与启动同步共用同一实现）。
 */
fun interface NotifyNewArticles {
    suspend operator fun invoke(sinceMillis: Long)
}
