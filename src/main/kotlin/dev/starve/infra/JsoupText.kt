package dev.starve.infra

import dev.starve.application.HtmlText
import org.jsoup.Jsoup

/** The plain text of stored HTML, whitespace collapsed, as a reader would show it. */
object JsoupText : HtmlText {
    override fun plainText(html: String): String = Jsoup.parseBodyFragment(html).text()
}
