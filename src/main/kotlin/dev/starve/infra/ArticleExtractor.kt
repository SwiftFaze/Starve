package dev.starve.infra

import dev.starve.application.FetchedArticle
import dev.starve.domain.FeedRules
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Pulls the article out of a fetched page: its main text (the `<article>`, else `<main>`, else the block holding
 * the most paragraph text), sanitised exactly like a feed body, and the page's og:image. Relative URLs resolve
 * against [link], the item's link.
 */
object ArticleExtractor {
    private const val CHROME = "nav, header, footer, aside, form, script, style"

    fun extract(
        page: Document,
        link: String,
    ): FetchedArticle {
        page.select(CHROME).remove()
        val body = mainElement(page)?.html()?.let { BodySanitizer.sanitize(it, link) }
        return FetchedArticle(body, ogImage(page, link))
    }

    private fun mainElement(page: Document): Element? =
        page.selectFirst("article")
            ?: page.selectFirst("main")
            ?: page
                .select("p")
                .groupBy { it.parent() }
                .maxByOrNull { (_, paragraphs) -> paragraphs.sumOf { it.text().length } }
                ?.key

    private fun ogImage(
        page: Document,
        link: String,
    ): String? =
        page
            .select("meta[property=og:image]")
            .firstNotNullOfOrNull { FeedRules.absoluteHttpUrl(it.attr("content"), link) }
}
