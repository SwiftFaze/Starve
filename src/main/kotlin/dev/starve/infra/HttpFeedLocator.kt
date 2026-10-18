package dev.starve.infra

import dev.starve.application.FeedFetchException
import dev.starve.application.FeedLocator
import dev.starve.application.LocateResult
import dev.starve.domain.FeedDiscovery
import dev.starve.domain.Platform
import dev.starve.domain.PlatformPage
import dev.starve.domain.PlatformPages
import dev.starve.domain.PlatformSite
import java.net.http.HttpResponse

/**
 * Resolves a URL. A page of one of [platforms] maps straight to its feed without being fetched (an Apple Podcasts
 * show through the lookup API); otherwise the URL is fetched: a feed stands for itself, an HTML page for its alternate
 * link or SoundCloud feed.
 */
class HttpFeedLocator(
    private val http: BoundedHttp,
    private val soundCloudHosts: Set<String>,
    private val soundCloudFeedTemplate: String,
    private val platforms: List<PlatformSite>,
) : FeedLocator {
    override fun locate(url: String): LocateResult {
        val page = PlatformPages.match(url, platforms)
        return if (page != null) fromPlatform(page) else fromPage(url)
    }

    private fun fromPage(url: String): LocateResult {
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

    private fun fromPlatform(page: PlatformPage): LocateResult =
        when (page.site.platform) {
            Platform.APPLE_PODCASTS -> fromLookup(page.address)
            else -> LocateResult.Found(page.address)
        }

    private fun fromLookup(lookupUrl: String): LocateResult {
        val response = fetch(lookupUrl) ?: return LocateResult.Unreachable
        val feedUrl = ApplePodcastLookup.feedUrl(String(response.body(), Charsets.UTF_8))
        return feedUrl?.let { LocateResult.Found(it) } ?: LocateResult.NoFeed
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
