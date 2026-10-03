package com.cycling.rssradar.i18n

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 日志类页面的时间戳格式（抓取诊断、崩溃日志共用）。 */
internal fun formatLogTimestamp(millis: Long, withSeconds: Boolean = false): String =
    SimpleDateFormat(if (withSeconds) "MM-dd HH:mm:ss" else "MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
