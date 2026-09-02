package com.cycling.rssradar.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cycling.rssradar.MainActivity

/**
 * 新文章通知（#31）的 Android 实现：渠道 + 发通知 + 权限检查。
 *
 * - 渠道：Android 8+ 必须建渠道才能发，重要性 DEFAULT（有声音无悬浮）。
 * - 权限：Android 13+ 的 POST_NOTIFICATIONS 是运行时权限，没授权就不发（静默跳过，
 *   不打扰用户）——是否请求权限由设置页在用户主动开开关时发起。
 * - 点击：回应用主界面（MainActivity），不做 deep link 到具体文章——
 *   多篇新文章时"点哪篇"没有唯一答案，交给信息流列表更符合直觉。
 */
object NotificationHelper {

    private const val CHANNEL_ID = "new_articles"
    private const val CHANNEL_NAME = "新文章"
    /** 同一渠道内固定 id：连续多次同步只替换这一条，不刷屏。 */
    private const val NOTIFICATION_ID = 1001
    /** 通知用小图标资源名（res/drawable/ic_stat_rssradar.xml）。 */
    private const val STATUS_ICON_NAME = "ic_stat_rssradar"

    /**
     * 状态栏图标：按名字解析而不是引用 R——本工程禁止 gradle，静态编译没有
     * R 类（资源不参与 kotlinc 编译），引用 R 会让 check-kotlin 直接报错。
     * 找不到就用系统图标兜底，不至于发不出通知。
     */
    private fun smallIconRes(context: Context): Int {
        val id = context.resources.getIdentifier(STATUS_ICON_NAME, "drawable", context.packageName)
        return if (id != 0) id else android.R.drawable.stat_notify_more
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "订阅源有新文章时提醒"
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    /** 发一条汇总通知；无权限时静默跳过（返回 false）。 */
    fun postNewArticles(context: Context, summary: NewArticleSummary.Summary): Boolean {
        if (!hasPermission(context)) return false
        createChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIconRes(context))
            .setContentTitle(summary.title)
            .setContentText(summary.contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary.bigText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
        return true
    }
}
