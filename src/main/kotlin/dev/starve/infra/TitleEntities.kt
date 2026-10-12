package dev.starve.infra

import org.springframework.web.util.HtmlUtils

/**
 * Decodes HTML character references (decimal, hex and HTML 4 named) in item titles.
 *
 * One left-to-right pass: `&amp;amp;` becomes `&amp;`, a bare `&` and an unknown
 * reference such as `&notareal;` are left as they are.
 */
object TitleEntities {
    fun decode(title: String): String = HtmlUtils.htmlUnescape(title)
}
