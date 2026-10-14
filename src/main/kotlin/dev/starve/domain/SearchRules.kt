package dev.starve.domain

/**
 * Turns what the user typed into an FTS5 match expression.
 *
 * Words are runs of letters, marks and digits. Each is double-quoted, so it is a plain string and never an
 * operator or column filter; the last one also gets `*` for prefix matching. Separate quoted words are ANDed.
 */
object SearchRules {
    private val WORD = Regex("[\\p{L}\\p{M}\\p{N}]+")

    /** The expression for [text], or null when it has no words (blank, or punctuation only). */
    fun matchExpression(text: String): String? {
        val words = WORD.findAll(text).map { it.value }.toList()
        if (words.isEmpty()) return null
        return words
            .mapIndexed { index, word -> if (index == words.lastIndex) "\"$word\"*" else "\"$word\"" }
            .joinToString(" ")
    }
}
