package com.cycling.rssradar.ui.intent

import android.content.Intent

/**
 * 外部送进来的文本（系统分享 / 选中文字）→ 可订阅的链接。
 *
 * ReadYou 差距表第 34 项：让 RssRadar 出现在系统分享单与「选中文字」菜单里。
 * RssRadar 是 RSSHub 中心化的阅读器，外部文本唯一有意义的归宿就是**订阅**——
 * 所以这里只做一件事：从一段乱七八糟的分享文本里挑出第一个链接。
 * 挑不出来就如实说挑不出来，不猜测、不拿标题当地址。
 */
object SharedText {
    // 刻意宽松：分享文本里链接常被标点、书名号、中文顿号包着，
    // 先抓下来再收尾，好过因为一个句号整个丢掉。
    private val URL = Regex("""https?://[^\s<>"'）】]+""")
    private val TRAILING = charArrayOf('.', ',', '。', '，', ')', '）', ']', '】', ';', '；')

    /** 取第一个 http(s) 链接并去掉尾随标点；没有则 null。 */
    fun extractUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return URL.find(raw)?.value?.trimEnd(*TRAILING)?.takeIf { it.length > "https://".length }
    }
}

/**
 * 本 Activity 收到的外部文本。[Intent.ACTION_SEND] 与 [Intent.ACTION_PROCESS_TEXT]
 * 各自把内容放在不同的 extra 里，其余 action 一律视为没有——
 * 别把 deep link 的 data 也当成分享文本喂进去。
 */
fun Intent.sharedText(): String? = when (action) {
    Intent.ACTION_SEND -> getStringExtra(Intent.EXTRA_TEXT)
    Intent.ACTION_PROCESS_TEXT -> getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
    else -> null
}
