package dev.starve.infra

import dev.starve.application.ArticleFetcher
import dev.starve.application.FeedFetchException
import dev.starve.application.FetchedArticle
import org.jsoup.Jsoup
import java.net.http.HttpResponse

/**
 * Fetches an article page through [BoundedHttp] (one timeout over the whole exchange). Anything but a 2xx HTML
 * page of at most [MAX_BYTES] is a failed fetch, as is a page that cannot be reached in time.
 */
class HttpArticleFetcher(
    private val http: BoundedHttp,
) : ArticleFetcher {
    override fun fetch(url: String): FetchedArticle? {
        val response = answer(url)?.takeIf(::usable) ?: return null
        return ArticleExtractor.extract(parse(response, url), url)
    }

    private fun parse(
        response: HttpResponse<ByteArray>,
        url: String,
    ) = Jsoup.parse(response.body().inputStream(), null, url)

    private fun answer(url: String): HttpResponse<ByteArray>? =
        try {
            http.get(url, ACCEPT)
        } catch (_: FeedFetchException) {
            null
        }

    private fun usable(response: HttpResponse<ByteArray>): Boolean =
        response.statusCode() in HTTP_OK_RANGE &&
            response
                .headers()
                .firstValue("Content-Type")
                .orElse("")
                .lowercase()
                .startsWith("text/html") &&
            response.body().size <= MAX_BYTES

    private companion object {
        const val ACCEPT = "text/html, application/xhtml+xml;q=0.9, */*;q=0.1"
        const val MAX_BYTES = 1_000_000
        val HTTP_OK_RANGE = 200..299
    }
}
