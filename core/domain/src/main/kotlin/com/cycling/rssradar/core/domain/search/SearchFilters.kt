package com.cycling.rssradar.core.domain.search

data class SearchFilters(
    val feedId: Long? = null,
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
    val unreadOnly: Boolean = false,
    val starredOnly: Boolean = false,
    val bookmarkedOnly: Boolean = false,
) {
    val isDefault: Boolean
        get() = feedId == null &&
            fromMillis == null &&
            toMillis == null &&
            !unreadOnly &&
            !starredOnly &&
            !bookmarkedOnly

    companion object {
        val None = SearchFilters()
    }
}
