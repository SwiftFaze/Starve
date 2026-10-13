package dev.starve.infra

import dev.starve.application.FaviconFinder
import dev.starve.domain.FaviconRules
import dev.starve.domain.FeedDiscovery

/** Reads a site's icon link from its page, else takes its /favicon.ico when that answers with an image. */
class HttpFaviconFinder(
    private val http: BoundedHttp,
) : FaviconFinder {
    override fun find(siteUrl: String): String? = linkedIcon(siteUrl) ?: rootIcon(FaviconRules.rootIconUrl(siteUrl))

    private fun linkedIcon(siteUrl: String): String? {
        val page = http.get(siteUrl, ACCEPT_PAGE)
        if (page.statusCode() !in HTTP_OK_RANGE) return null
        return FeedDiscovery.iconLink(String(page.body(), Charsets.UTF_8), siteUrl)
    }

    private fun rootIcon(url: String): String? {
        val response = http.get(url, ACCEPT_IMAGE)
        val type =
            response
                .headers()
                .firstValue("Content-Type")
                .orElse("")
                .lowercase()
        return url.takeIf { response.statusCode() in HTTP_OK_RANGE && type.startsWith("image/") }
    }

    private companion object {
        const val ACCEPT_PAGE = "text/html, application/xhtml+xml, */*;q=0.5"
        const val ACCEPT_IMAGE = "image/*, */*;q=0.5"
        val HTTP_OK_RANGE = 200..299
    }
}
