package com.cycling.rssradar.core.data.store

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

/** 外链打开方式与分享格式偏好（#26）。 */
class LinkStore(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(readPersisted())
    val state: StateFlow<LinkShareState> = _state.asStateFlow()

    fun update(transform: (LinkShareState) -> LinkShareState) {
        val next = transform(_state.value)
        prefs.edit()
            .putString(KEY_LINK_MODE, next.linkOpenMode.name)
            .putString(KEY_SHARE_FORMAT, next.shareFormat.name)
            .apply()
        _state.value = next
    }

    private fun readPersisted(): LinkShareState = LinkShareState(
        linkOpenMode = prefs.getString(KEY_LINK_MODE, null)
            ?.let { runCatching { LinkOpenMode.valueOf(it) }.getOrNull() }
            ?: LinkOpenMode.BROWSER,
        shareFormat = prefs.getString(KEY_SHARE_FORMAT, null)
            ?.let { runCatching { ShareContentFormat.valueOf(it) }.getOrNull() }
            ?: ShareContentFormat.TITLE_LINK,
    )

    companion object {
        private const val KEY_LINK_MODE = "link_open_mode"
        private const val KEY_SHARE_FORMAT = "share_content_format"
    }
}
