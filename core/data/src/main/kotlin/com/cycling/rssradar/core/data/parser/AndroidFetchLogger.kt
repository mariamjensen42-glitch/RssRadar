package com.cycling.rssradar.core.data.parser

import android.util.Log

/**
 * [FetchLogger] 的 Android 实现：正文抓取的所有告警都落在 logcat 的 `RssRadar/Fetch` 下。
 *
 * 抽接口而不是直接用 Log，是因为 ContentFetcher 要在纯 JVM 单测里跑——
 * android.jar 里的 Log 是 stub，一调用就抛 RuntimeException。
 */
class AndroidFetchLogger : FetchLogger {

    override fun log(level: FetchLogger.Level, message: String, throwable: Throwable?) {
        when (level) {
            FetchLogger.Level.INFO -> Log.i(TAG, message)
            FetchLogger.Level.WARN -> Log.w(TAG, message, throwable)
            FetchLogger.Level.ERROR -> Log.e(TAG, message, throwable)
        }
    }

    private companion object {
        const val TAG = "RssRadar/Fetch"
    }
}
