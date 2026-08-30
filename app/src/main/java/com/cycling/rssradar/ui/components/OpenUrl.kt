package com.cycling.rssradar.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** 用系统浏览器打开外链。失败要让用户看见，不能静默吞掉。阅读页 WebView 链接接管也复用。 */
internal fun Context.openUrl(url: String) {
    if (url.isBlank()) {
        Toast.makeText(this, "该文章没有可用链接", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
        .onFailure { Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show() }
}
