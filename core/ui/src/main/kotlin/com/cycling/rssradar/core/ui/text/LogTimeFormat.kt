package com.cycling.rssradar.core.ui.text

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志类页面的时间戳格式（抓取诊断、崩溃日志共用）。
 *
 * 原住 `app/i18n` 且是 `internal`：两个消费方（CrashLog / FetchDiagnostics）都在 feature:me，
 * 但 feature 够不着 app 包的 internal ⇒ 沉 core.ui.text 并放宽为 public。
 */
fun formatLogTimestamp(millis: Long, withSeconds: Boolean = false): String =
    SimpleDateFormat(if (withSeconds) "MM-dd HH:mm:ss" else "MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
