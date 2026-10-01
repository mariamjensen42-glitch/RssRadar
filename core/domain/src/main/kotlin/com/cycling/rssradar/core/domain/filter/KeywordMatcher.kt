package com.cycling.rssradar.core.domain.filter

object KeywordMatcher {

    fun matches(
        text: String,
        keyword: String,
        caseSensitive: Boolean = false,
        wholeWord: Boolean = false,
    ): Boolean {
        val needle = keyword.trim()
        if (needle.isEmpty() || text.isEmpty()) return false
        if (!wholeWord) {
            return text.contains(needle, ignoreCase = !caseSensitive)
        }
        var from = 0
        while (from <= text.length - needle.length) {
            val idx = text.indexOf(needle, from, ignoreCase = !caseSensitive)
            if (idx < 0) return false
            val boundaryBefore = idx == 0 || !text[idx - 1].isLetterOrDigit()
            val afterIndex = idx + needle.length
            val boundaryAfter = afterIndex >= text.length || !text[afterIndex].isLetterOrDigit()
            if (boundaryBefore && boundaryAfter) return true
            from = idx + 1
        }
        return false
    }

    fun matchesAny(
        text: String,
        keywords: List<String>,
        caseSensitive: Boolean = false,
    ): Boolean = keywords.any { matches(text, it, caseSensitive) }

    fun parseKeywords(raw: String): List<String> =
        raw.split(',', '，', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
}
