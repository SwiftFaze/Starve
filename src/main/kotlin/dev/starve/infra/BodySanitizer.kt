package dev.starve.infra

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

/**
 * Turns an untrusted feed body into HTML that is safe to render as is. Feed HTML is untrusted, so this runs
 * once, at ingestion, and the stored result is trusted afterwards. Cleaning twice changes nothing.
 */
object BodySanitizer {
    const val MAX_LENGTH = 200_000

    private const val ACTIVE = "script, style, iframe, object, embed, form, input, link, noscript, template"

    private val safelist: Safelist =
        Safelist
            .relaxed()
            .addTags("figure", "figcaption", "hr")
            .removeProtocols("a", "href", "ftp")
            .addEnforcedAttribute("a", "target", "_blank")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer")

    /**
     * The clean form of [html], with relative URLs resolved against [baseUrl] (dropped when there is none);
     * null when nothing is left. The raw text is cut to [MAX_LENGTH] first, then further if cleaning grows it.
     */
    fun sanitize(
        html: String?,
        baseUrl: String?,
    ): String? {
        if (html.isNullOrBlank()) return null
        return cleanWithinCap(html, baseUrl.orEmpty()).takeIf { it.isNotBlank() }
    }

    private fun cleanWithinCap(
        html: String,
        baseUrl: String,
    ): String {
        var keep = MAX_LENGTH
        while (true) {
            val clean = clean(html.take(keep.coerceAtLeast(0)), baseUrl)
            if (clean.length <= MAX_LENGTH) return clean
            keep -= clean.length - MAX_LENGTH
        }
    }

    private fun clean(
        html: String,
        baseUrl: String,
    ): String {
        val parsed = Jsoup.parseBodyFragment(html, baseUrl)
        parsed.select(ACTIVE).remove()
        val cleaned: Document = Cleaner(safelist).clean(parsed)
        cleaned.outputSettings().prettyPrint(false)
        dropEmptyBlocks(cleaned.body())
        collapseLineBreaks(cleaned.body())
        return cleaned.body().html().trim()
    }

    // Feeds pad posts with empty paragraphs and <br> runs for spacing; each renders as a blank line.
    private fun dropEmptyBlocks(body: Element) {
        body.select("p, div").filter { it.text().isBlankText() && it.select("img").isEmpty() }.forEach { it.remove() }
    }

    private fun collapseLineBreaks(body: Element) {
        body.select("br").filter { precededByBreak(it) }.forEach { it.remove() }
    }

    private fun precededByBreak(br: Element): Boolean {
        var node: Node? = br.previousSibling()
        while (node is TextNode && node.isBlank) node = node.previousSibling()
        return node is Element && node.tagName() == "br"
    }

    private fun String.isBlankText() = replace(' ', ' ').isBlank()
}
