package com.cycling.rssradar.core.data.store.prefs

import android.content.SharedPreferences
import com.cycling.rssradar.core.model.LinkOpenMode
import com.cycling.rssradar.core.model.LinkShareState
import com.cycling.rssradar.core.model.ShareContentFormat
import com.cycling.rssradar.core.model.enumValueOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        linkOpenMode = enumValueOrNull<LinkOpenMode>(prefs.getString(KEY_LINK_MODE, null))
            ?: LinkOpenMode.BROWSER,
        shareFormat = enumValueOrNull<ShareContentFormat>(prefs.getString(KEY_SHARE_FORMAT, null))
            ?: ShareContentFormat.TITLE_LINK,
    )

    companion object {
        private const val KEY_LINK_MODE = "link_open_mode"
        private const val KEY_SHARE_FORMAT = "share_content_format"
    }
}
