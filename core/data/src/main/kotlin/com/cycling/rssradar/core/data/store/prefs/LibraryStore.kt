package com.cycling.rssradar.core.data.store.prefs

import android.content.SharedPreferences
import com.cycling.rssradar.core.model.LibraryState
import com.cycling.rssradar.core.model.library.LibraryRange
import com.cycling.rssradar.core.model.library.LibrarySort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LibraryStore(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    fun setShowBookmarked(show: Boolean) = write(_state.value.copy(showBookmarked = show))

    fun setSort(sort: LibrarySort) = write(_state.value.copy(sort = sort))

    fun setRange(range: LibraryRange) = write(_state.value.copy(range = range))

    private fun read(): LibraryState = LibraryState(
        showBookmarked = prefs.getBoolean(KEY_BOOKMARKED, false),
        sort = LibrarySort.fromNameOrNull(prefs.getString(KEY_SORT, null)) ?: LibrarySort.STARRED_AT,
        range = LibraryRange.fromNameOrNull(prefs.getString(KEY_RANGE, null)) ?: LibraryRange.ALL,
    )

    private fun write(state: LibraryState) {
        prefs.edit()
            .putBoolean(KEY_BOOKMARKED, state.showBookmarked)
            .putString(KEY_SORT, state.sort.name)
            .putString(KEY_RANGE, state.range.name)
            .apply()
        _state.value = state
    }

    companion object {
        private const val KEY_BOOKMARKED = "library_show_bookmarked"
        private const val KEY_SORT = "library_sort"
        private const val KEY_RANGE = "library_range"
    }
}
