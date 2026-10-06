package com.cycling.rssradar.core.model

import com.cycling.rssradar.core.model.library.LibraryRange
import com.cycling.rssradar.core.model.library.LibrarySort

data class LibraryState(
    val showBookmarked: Boolean = false,
    val sort: LibrarySort = LibrarySort.STARRED_AT,
    val range: LibraryRange = LibraryRange.ALL,
)
