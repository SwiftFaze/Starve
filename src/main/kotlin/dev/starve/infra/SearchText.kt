package dev.starve.infra

import org.jsoup.Jsoup

/**
 * The text of a stored post body, as the search index holds it: markup dropped, entities decoded.
 * Shared by the write path and the V10 back-fill so both index the same words.
 */
object SearchText {
    fun bodyText(html: String?): String? = html?.let { Jsoup.parseBodyFragment(it).text() }
}
