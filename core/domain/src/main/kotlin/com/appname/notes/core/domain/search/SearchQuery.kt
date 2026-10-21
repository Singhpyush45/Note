package com.appname.notes.core.domain.search

import com.appname.notes.core.domain.model.SearchHit

object SearchQuery {

    private const val MAX_TERMS = 12

    /**
     * Turns what the user typed into a safe SQLite FTS4 MATCH expression.
     * Every word becomes a prefix term (`lec*` finds "lecture"); all words must match.
     * Operators and quotes typed by the user are removed so input can never break the query.
     * Returns null when there is nothing to search for.
     */
    fun toMatchExpression(userInput: String): String? {
        val terms = userInput
            .split(Regex("""[^\p{L}\p{M}\p{N}]+"""))
            .filter { it.isNotBlank() }
            .take(MAX_TERMS)
        if (terms.isEmpty()) return null
        // Lower-case so words like "OR"/"NOT"/"NEAR" are searched for, never treated as FTS operators.
        return terms.joinToString(" ") { "${it.lowercase()}*" }
    }
}

/** A snippet split into plain text plus the ranges to highlight. */
data class Highlighted(val text: String, val ranges: List<IntRange>) {
    companion object {
        fun parse(snippet: String): Highlighted {
            val out = StringBuilder()
            val ranges = ArrayList<IntRange>()
            var openAt = -1
            for (c in snippet) {
                when (c) {
                    SearchHit.HIGHLIGHT_START -> openAt = out.length
                    SearchHit.HIGHLIGHT_END -> {
                        if (openAt in 0 until out.length) ranges += openAt until out.length
                        openAt = -1
                    }
                    else -> out.append(c)
                }
            }
            return Highlighted(out.toString(), ranges)
        }
    }
}
