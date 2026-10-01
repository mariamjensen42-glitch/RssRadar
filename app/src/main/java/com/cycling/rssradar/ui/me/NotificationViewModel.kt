package com.cycling.rssradar.ui.me

import androidx.lifecycle.ViewModel
import com.cycling.rssradar.core.data.store.prefs.NotificationStore
import com.cycling.rssradar.core.domain.notify.NotifyPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationStore: NotificationStore,
) : ViewModel() {

    val state: StateFlow<NotifyPrefs> = notificationStore.state

    fun setDnd(startMinute: Int?, endMinute: Int?) = notificationStore.setDnd(startMinute, endMinute)

    fun setIncludeKeywords(keywords: List<String>) =
        notificationStore.setKeywords(keywords, notificationStore.state.value.excludeKeywords)

    fun setExcludeKeywords(keywords: List<String>) =
        notificationStore.setKeywords(notificationStore.state.value.includeKeywords, keywords)
}
