package com.cycling.rssradar.core.model

/** 外链打开方式（#26）。Custom Tabs 需要引入 androidx.browser 依赖，暂未提供。 */
enum class LinkOpenMode {
    /** 直接交给系统默认浏览器（当前默认行为，升级无感知）。 */
    BROWSER,

    /** 每次弹系统选择器，用户自己挑浏览器/应用。 */
    ASK,
}

/** 分享文章时的内容格式（#26）。 */
enum class ShareContentFormat {
    TITLE_LINK,
    LINK,
    TITLE_SUMMARY_LINK,
}

data class LinkShareState(
    val linkOpenMode: LinkOpenMode = LinkOpenMode.BROWSER,
    val shareFormat: ShareContentFormat = ShareContentFormat.TITLE_LINK,
)
