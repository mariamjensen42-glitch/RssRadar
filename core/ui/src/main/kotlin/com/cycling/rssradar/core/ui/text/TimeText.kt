package com.cycling.rssradar.core.ui.text

import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 相对时间（「3 分钟前」）：标注与收藏列表共用。 */
fun relativeTime(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()

/** 导出文件名日期后缀：多次导出不互相覆盖。小数分隔符固定 US，避免地区差异侵入文件名。 */
fun todayStamp(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

/** 媒体时长：`m:ss`，满一小时转 `h:mm:ss`。播放页与正文媒体条共用。 */
fun formatMediaTime(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1000L
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
