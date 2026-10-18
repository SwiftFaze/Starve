package dev.starve.web

import kotlin.math.min

/**
 * A stored post body's headings, re-levelled for display so the post's own title (an h2 in the reader and the detail
 * view) owns them: the body's top heading level becomes h3 and the rest keep their distance from it, capped at h6.
 * A body whose headings already sit below the title is left as stored. Stored bodies are never changed.
 */
object PostHeadings {
    private const val TITLE_LEVEL = 2
    private const val DEEPEST = 6

    // An h1-h6 opening or closing tag: the name must end there, so <header> or <hr> never match.
    private val TAG = Regex("<(/?)[hH]([1-6])(?=[^0-9a-zA-Z])")

    private fun deeper(
        level: String,
        shift: Int,
    ) = min(level.toInt() + shift, DEEPEST)

    fun underTitle(html: String): String {
        val top = TAG.findAll(html).minOfOrNull { it.groupValues[2].toInt() } ?: DEEPEST
        val shift = TITLE_LEVEL + 1 - top
        if (shift <= 0) return html
        return TAG.replace(html) { "<${it.groupValues[1]}h${deeper(it.groupValues[2], shift)}" }
    }
}
