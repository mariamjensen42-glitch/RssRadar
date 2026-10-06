package com.cycling.rssradar.core.domain.annotation

object AnnotationAnchor {

    data class Location(val start: Int, val end: Int) {
        val length: Int get() = end - start
    }

    fun locate(
        text: String,
        quote: String,
        prefix: String = "",
        suffix: String = "",
        hintStart: Int = 0,
    ): Location? {
        if (text.isEmpty() || quote.isEmpty()) return null

        if (prefix.isNotEmpty() || suffix.isNotEmpty()) {
            val anchor = prefix + quote + suffix
            val hit = nearest(text, anchor, hintStart)
            if (hit != null) {
                val start = hit + prefix.length
                if (start + quote.length <= text.length) {
                    return Location(start, start + quote.length)
                }
            }
        }

        val direct = nearest(text, quote, hintStart) ?: return null
        return Location(direct, direct + quote.length)
    }

    private fun nearest(text: String, needle: String, hint: Int): Int? {
        if (needle.isEmpty()) return null
        var best: Int? = null
        var bestDistance = Int.MAX_VALUE
        var from = 0
        while (true) {
            val index = text.indexOf(needle, from)
            if (index < 0) break
            val distance = if (hint <= 0) index else distanceBetween(index, hint)
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
            if (hint > 0 && index >= hint) break
            from = index + 1
        }
        return best
    }

    private fun distanceBetween(a: Int, b: Int): Int = if (a > b) a - b else b - a
}
