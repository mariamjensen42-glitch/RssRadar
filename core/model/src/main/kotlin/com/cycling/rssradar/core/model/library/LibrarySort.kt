package com.cycling.rssradar.core.model.library

enum class LibrarySort {
    STARRED_AT,
    PUBLISHED_AT,
    FEED,
}

enum class LibraryRange(val days: Long?) {
    ALL(null),
    WEEK(7L),
    MONTH(30L),
    YEAR(365L),
}
