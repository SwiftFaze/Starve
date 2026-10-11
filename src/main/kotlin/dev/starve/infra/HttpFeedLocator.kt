package dev.starve.infra

import dev.starve.application.FeedFetchException
import dev.starve.application.FeedLocator
import dev.starve.application.LocateResult
import dev.starve.domain.FeedDiscovery
import java.net.http.HttpResponse

/** Resolves a URL by fetching it: a feed stands for itself, an HTML page for its alternate link or SoundCloud feed. */
class HttpFeedLocator(
    private val http: BoundedHttp,
    private val soundCloudHosts: Set<String>,
    private val soundCloudFeedTemplate: String,
) : FeedLocator {
    override fun locate(url: String): LocateResult {
        val response = fetch(url) ?: return LocateResult.Unreachable
        val body = String(response.body(), Charsets.UTF_8)
        val contentType = response.headers().firstValue("Content-Type").orElse(null)
        return if (FeedDiscovery.isHtml(contentType, body)) fromHtml(url, body) else LocateResult.Found(url)
    }

    private fun fetch(url: String): HttpResponse<ByteArray>? =
        try {
            http.get(url, ACCEPT).takeIf { it.statusCode() in HTTP_OK_RANGE }
        } catch (_: FeedFetchException) {
            null
        }

    private fun fromHtml(
        url: String,
        html: String,
    ): LocateResult {
        val feedUrl = FeedDiscovery.alternateFeed(html, url) ?: soundCloudFeed(url, html)
        return feedUrl?.let { LocateResult.Found(it) } ?: LocateResult.NoFeed
    }

    private fun soundCloudFeed(
        url: String,
        html: String,
    ): String? {
        val userId = FeedDiscovery.soundCloudUserId(html, url, soundCloudHosts)
        return userId?.let { soundCloudFeedTemplate.replace("{id}", it) }
    }

    private companion object {
        const val ACCEPT = "text/html, application/xhtml+xml, application/rss+xml, application/atom+xml, */*"
        val HTTP_OK_RANGE = 200..299
    }
}
