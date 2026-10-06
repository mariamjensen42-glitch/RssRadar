package com.cycling.rssradar.core.domain.reading

/** 页内查找：在摊平后的正文上定位，并给出可环绕的游标。 */
object FindIndex {

    data class Hit(
        val blockIndex: Int,
        val globalStart: Int,
        val localStart: Int,
        val length: Int,
    )

    fun find(
        flat: ReadingTextMap.FlatText,
        query: String,
        caseSensitive: Boolean = false,
        wholeWord: Boolean = false,
    ): List<Hit> {
        val needle = query.trim()
        if (needle.isEmpty() || flat.text.isEmpty()) return emptyList()
        val haystack = flat.text
        val hits = mutableListOf<Hit>()
        var from = 0
        while (from <= haystack.length - needle.length) {
            val index = haystack.indexOf(needle, from, ignoreCase = !caseSensitive)
            if (index < 0) break
            if (!wholeWord || hasWordBoundaries(haystack, index, needle.length)) {
                val span = flat.spanOf(index)
                if (span != null) {
                    hits += Hit(
                        blockIndex = span.blockIndex,
                        globalStart = index,
                        localStart = index - span.start,
                        length = needle.length,
                    )
                }
            }
            from = index + 1
        }
        return hits
    }

    private fun hasWordBoundaries(text: String, index: Int, length: Int): Boolean {
        val before = index == 0 || !text[index - 1].isLetterOrDigit()
        val afterIndex = index + length
        val after = afterIndex >= text.length || !text[afterIndex].isLetterOrDigit()
        return before && after
    }

    class Cursor(private val hits: List<Hit>) {

        private var position = -1

        val size: Int get() = hits.size

        val positionIndex: Int get() = position

        fun current(): Hit? = hits.getOrNull(position)

        fun next(): Hit? {
            if (hits.isEmpty()) return null
            position = if (position < 0) 0 else (position + 1) % hits.size
            return hits[position]
        }

        fun previous(): Hit? {
            if (hits.isEmpty()) return null
            position = if (position <= 0) hits.size - 1 else position - 1
            return hits[position]
        }
    }
}
