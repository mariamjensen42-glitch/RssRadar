package com.cycling.rssradar.core.domain.search

object SearchQueryBuilder {

    sealed interface Query {
        data class Fts(val match: String) : Query

        data class Like(val raw: String) : Query
    }

    fun build(raw: String): Query? {
        val input = raw.trim()
        if (input.isEmpty()) return null

        val words = input.split(' ', '\t', '\n').filter { it.isNotBlank() }
        if (words.isEmpty()) return null

        val tokens = mutableListOf<String>()
        var needsLike = false

        for (word in words) {
            val parts = SearchTextBuilder.segment(word).split(' ').filter { it.isNotBlank() }
            if (parts.isEmpty()) {
                needsLike = true
                continue
            }
            if (parts.any { it.length == 1 && SearchTextBuilder.isCjk(it[0]) }) {
                needsLike = true
            } else {
                tokens.addAll(parts)
            }
        }

        if (needsLike || tokens.isEmpty()) {
            return Query.Like(escapeLike(input))
        }
        return Query.Fts(tokens.joinToString(" ") { quote(it) })
    }

    private fun quote(token: String): String = "\"" + token.replace("\"", "\"\"") + "\""

    private fun escapeLike(raw: String): String =
        raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
