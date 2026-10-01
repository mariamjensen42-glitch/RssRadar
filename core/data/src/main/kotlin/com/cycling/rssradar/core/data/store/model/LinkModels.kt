package com.cycling.rssradar.core.data.store.model

/** 外链打开方式（#26）。Custom Tabs 需要引入 androidx.browser 依赖，暂未提供。 */
enum class LinkOpenMode(val label: String) {
    /** 直接交给系统默认浏览器（当前默认行为，升级无感知）。 */
    BROWSER("系统浏览器"),

    /** 每次弹系统选择器，用户自己挑浏览器/应用。 */
    ASK("每次询问"),
}

/** 分享文章时的内容格式（#26）。 */
enum class ShareContentFormat(val label: String) {
    TITLE_LINK("标题 + 链接"),
    LINK("仅链接"),
    TITLE_SUMMARY_LINK("标题 + 摘要 + 链接"),
}

data class LinkShareState(
    val linkOpenMode: LinkOpenMode = LinkOpenMode.BROWSER,
    val shareFormat: ShareContentFormat = ShareContentFormat.TITLE_LINK,
)
