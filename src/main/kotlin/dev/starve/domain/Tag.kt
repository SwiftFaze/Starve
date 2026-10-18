package dev.starve.domain

/**
 * A saved keyword view across sources: items whose title (and body, when [matchBody]) has a word or phrase of
 * [terms] are listed under it, from [groupId]'s sources only when given. It adds a view; items keep their group.
 */
data class Tag(
    val id: Long,
    val name: String,
    val terms: List<String>,
    val matchBody: Boolean = false,
    val groupId: Long? = null,
) {
    /** The FTS5 match expression that selects this tag's items. */
    val match: String get() = TagRules.matchExpression(terms, matchBody)

    /** The terms as the user enters them, comma-separated. */
    val termsText: String get() = terms.joinToString(", ")
}

/** A tag with the number of unread items it lists. */
data class TagSummary(
    val tag: Tag,
    val unread: Int,
)

/** The outcome of checking the terms entered for a tag. */
sealed interface TermsCheck {
    data class Valid(
        val terms: List<String>,
    ) : TermsCheck

    data object Empty : TermsCheck

    data object TooMany : TermsCheck

    /** [term] is not words of letters and digits with an optional trailing `*`. */
    data class Invalid(
        val term: String,
    ) : TermsCheck
}

/**
 * How tag terms are read and matched. A term is one or more words of letters, marks or digits separated by spaces
 * (several words are a phrase), with an optional trailing `*` making its last word a prefix. Each term is quoted
 * in the match expression, so nothing the user types is ever an FTS5 operator or column filter.
 */
object TagRules {
    const val MAX_TERMS = 20

    private val TERM = Regex("[\\p{L}\\p{M}\\p{N}]+(?: [\\p{L}\\p{M}\\p{N}]+)*\\*?")
    private val SPACES = Regex("\\s+")

    /** The terms of [raw], split on commas, trimmed, spaces collapsed, empties and repeats (ignoring case) dropped. */
    fun checkTerms(raw: String): TermsCheck {
        val terms =
            raw
                .split(',')
                .map { it.trim().replace(SPACES, " ") }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
        val invalid = terms.firstOrNull { !TERM.matches(it) }
        return when {
            invalid != null -> TermsCheck.Invalid(invalid)
            terms.isEmpty() -> TermsCheck.Empty
            terms.size > MAX_TERMS -> TermsCheck.TooMany
            else -> TermsCheck.Valid(terms)
        }
    }

    /** The expression matching any of [terms] in item titles, and in bodies too when [matchBody]. */
    fun matchExpression(
        terms: List<String>,
        matchBody: Boolean,
    ): String {
        val columns = if (matchBody) "{title body}" else "{title}"
        return "$columns : (" + terms.joinToString(" OR ") { quoted(it) } + ")"
    }

    private fun quoted(term: String): String = if (term.endsWith('*')) "\"${term.dropLast(1)}\"*" else "\"$term\""
}
