package dev.starve.domain

/**
 * The title an item is listed under. Bluesky and Mastodon posts, among others, come without one, so it is taken from
 * the start of the post's text: placeholders in square brackets left out, cut at a word boundary to at most
 * [MAX_DERIVED_LENGTH] characters, with an ellipsis when cut.
 */
object ItemTitles {
    /** The title of an item that has neither a title nor any text. */
    const val UNTITLED = "(untitled)"

    const val MAX_DERIVED_LENGTH = 64

    private const val ELLIPSIS = "…"
    private val PLACEHOLDER = Regex("\\[[^\\]]*]")
    private val SPACES = Regex("\\s+")

    /** [title] when it is not blank, else one derived from [text], the post's plain text. */
    fun of(
        title: String,
        text: String?,
    ): String = title.ifBlank { derived(text.orEmpty()) }

    /** The start of [text] as a title, or [UNTITLED] when nothing but placeholders and spaces is left. */
    fun derived(text: String): String {
        val words = text.replace(PLACEHOLDER, " ").replace(SPACES, " ").trim()
        return when {
            words.isEmpty() -> UNTITLED
            words.length <= MAX_DERIVED_LENGTH -> words
            else -> cut(words) + ELLIPSIS
        }
    }

    /** [words] cut before the last word that does not fit, or mid-word when the first one alone is too long. */
    private fun cut(words: String): String {
        val end = words.lastIndexOf(' ', MAX_DERIVED_LENGTH).takeIf { it > 0 } ?: MAX_DERIVED_LENGTH
        return words.take(end).trimEnd { it == ' ' || it in ",;:" }
    }
}
