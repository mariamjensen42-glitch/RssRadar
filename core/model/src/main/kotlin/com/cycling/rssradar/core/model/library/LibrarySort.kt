package com.cycling.rssradar.core.model.library

enum class LibrarySort(val label: String) {
    STARRED_AT("按收藏时间"),
    PUBLISHED_AT("按发布时间"),
    FEED("按来源"),
    ;

    companion object {
        fun fromNameOrNull(name: String?): LibrarySort? = entries.firstOrNull { it.name == name }
    }
}

enum class LibraryRange(val label: String, val days: Long?) {
    ALL("全部", null),
    WEEK("近一周", 7L),
    MONTH("近一月", 30L),
    YEAR("近一年", 365L),
    ;

    companion object {
        fun fromNameOrNull(name: String?): LibraryRange? = entries.firstOrNull { it.name == name }
    }
}
