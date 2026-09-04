package com.cycling.rssradar.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.cycling.rssradar.core.data.store.LinkOpenMode
import com.cycling.rssradar.core.data.store.LinkShareState
import com.cycling.rssradar.core.data.store.LinkStore
import com.cycling.rssradar.core.data.store.SettingsPrefs
import com.cycling.rssradar.core.data.store.ShareContentFormat

/**
 * 外链出口：阅读页 WebView 链接接管、查看原文、分享前的链接都走这里。
 * 打开方式由 [LinkStore] 的偏好决定（#26）——偏好直接读 SharedPreferences，
 * 避免为了一个开关把 Store 从 DI 一路传到每个调用点。
 */
internal fun Context.openUrl(url: String) {
    openUrl(url, LinkStore(SettingsPrefs.of(this)).state.value.linkOpenMode)
}

internal fun Context.openUrl(url: String, mode: LinkOpenMode) {
    if (url.isBlank()) {
        Toast.makeText(this, "该文章没有可用链接", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val target = if (mode == LinkOpenMode.ASK) {
        Intent.createChooser(intent, "打开链接")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
        intent
    }
    runCatching { startActivity(target) }
        .onFailure { Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show() }
}

/**
 * 分享文章（#26）：按 [LinkShareState.shareFormat] 拼内容，摘要没有就不编——
 * 「标题 + 摘要 + 链接」在无摘要时退化为「标题 + 链接」，不塞空行凑格式。
 */
internal fun Context.shareArticle(
    title: String,
    link: String,
    summary: String?,
    state: LinkShareState,
) {
    if (link.isBlank()) {
        Toast.makeText(this, "该文章没有可分享的链接", Toast.LENGTH_SHORT).show()
        return
    }
    val text = when (state.shareFormat) {
        ShareContentFormat.LINK -> link
        ShareContentFormat.TITLE_LINK -> "$title\n$link"
        ShareContentFormat.TITLE_SUMMARY_LINK -> {
            val brief = summary?.takeIf { it.isNotBlank() }
            if (brief != null) "$title\n$brief\n$link" else "$title\n$link"
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_TITLE, title)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(Intent.createChooser(intent, "分享文章").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(this, "无法分享", Toast.LENGTH_SHORT).show() }
}
