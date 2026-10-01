package com.cycling.rssradar.core.domain.notify

import com.cycling.rssradar.core.domain.filter.KeywordMatcher

data class NotifyPrefs(
    val enabled: Boolean = false,
    val dndStartMinute: Int? = null,
    val dndEndMinute: Int? = null,
    val includeKeywords: List<String> = emptyList(),
    val excludeKeywords: List<String> = emptyList(),
) {
    val dndEnabled: Boolean
        get() = dndStartMinute != null && dndEndMinute != null && dndStartMinute != dndEndMinute
}

object NotifyDecision {

    fun shouldNotify(
        prefs: NotifyPrefs,
        feedEnabled: Boolean,
        title: String,
        summary: String,
        nowMinute: Int,
        suppressedByRule: Boolean = false,
    ): Boolean {
        if (!prefs.enabled) return false
        if (!feedEnabled) return false
        if (suppressedByRule) return false

        val start = prefs.dndStartMinute
        val end = prefs.dndEndMinute
        if (start != null && end != null && DndWindow.isQuiet(nowMinute, start, end)) return false

        val haystack = if (summary.isEmpty()) title else "$title $summary"
        if (prefs.excludeKeywords.isNotEmpty() &&
            KeywordMatcher.matchesAny(haystack, prefs.excludeKeywords)
        ) {
            return false
        }
        if (prefs.includeKeywords.isNotEmpty() &&
            !KeywordMatcher.matchesAny(haystack, prefs.includeKeywords)
        ) {
            return false
        }
        return true
    }
}
