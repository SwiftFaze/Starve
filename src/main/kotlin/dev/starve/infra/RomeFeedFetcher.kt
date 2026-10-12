package dev.starve.infra

import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.FeedException
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import dev.starve.application.FeedFetchException
import dev.starve.application.FeedFetcher
import dev.starve.application.FetchResult
import dev.starve.application.InvalidFeedException
import dev.starve.domain.Feed
import dev.starve.domain.FeedItem
import dev.starve.domain.FeedRules
import dev.starve.domain.Validators
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.http.HttpResponse

/** Fetches feeds over HTTP with a conditional GET and parses RSS/Atom with ROME. */
class RomeFeedFetcher(
    private val http: BoundedHttp,
) : FeedFetcher {
    override fun fetch(
        url: String,
        validators: Validators,
    ): FetchResult {
        val response = http.get(url, ACCEPT, conditionalHeaders(validators))
        val status = response.statusCode()
        return when {
            status == NOT_MODIFIED -> FetchResult.NotModified
            status in HTTP_OK_RANGE -> FetchResult.Fetched(parse(response.body()), responseValidators(response))
            else -> throw FeedFetchException("HTTP $status")
        }
    }

    private fun conditionalHeaders(validators: Validators): Map<String, String> =
        listOfNotNull(
            validators.etag?.let { "If-None-Match" to it },
            validators.lastModified?.let { "If-Modified-Since" to it },
        ).toMap()

    private fun responseValidators(response: HttpResponse<*>) =
        Validators(
            etag = response.headers().firstValue("ETag").orElse(null),
            lastModified = response.headers().firstValue("Last-Modified").orElse(null),
        )

    private fun parse(body: ByteArray): Feed {
        val syndFeed = readFeed(body)
        return Feed(
            title = syndFeed.title?.trim()?.takeIf { it.isNotEmpty() },
            items = syndFeed.entries.mapNotNull { toItem(it) },
        )
    }

    private fun readFeed(body: ByteArray): SyndFeed =
        try {
            XmlReader(ByteArrayInputStream(body)).use { SyndFeedInput().build(it) }
        } catch (e: FeedException) {
            throw InvalidFeedException(e.message ?: "unparseable feed", e)
        } catch (e: IOException) {
            throw InvalidFeedException(e.message ?: "unreadable feed", e)
        }

    private fun toItem(entry: SyndEntry): FeedItem? {
        val key = FeedRules.itemKey(entry.uri, entry.link) ?: return null
        val published = entry.publishedDate ?: entry.updatedDate
        return FeedItem(
            key = key,
            title = entry.title?.trim()?.takeIf { it.isNotEmpty() } ?: "(untitled)",
            link = entry.link?.takeIf { it.isNotBlank() },
            publishedAt = published?.toInstant(),
            author = entry.author?.takeIf { it.isNotBlank() },
            thumbnailUrl = thumbnail(entry),
        )
    }

    private fun thumbnail(entry: SyndEntry): String? {
        val media = entry.foreignMarkup.flatMap { if (it.name == "group") it.children else listOf(it) }
        val found =
            media.firstOrNull { it.name == "thumbnail" }?.getAttributeValue("url")
                ?: entry.enclosures.firstOrNull { it.type?.startsWith("image/") == true }?.url
                ?: media.firstOrNull { it.name == "image" && it.namespaceURI == ITUNES }?.getAttributeValue("href")
        return found?.let(FeedRules::thumbnailUrl)
    }

    private companion object {
        const val ACCEPT = "application/rss+xml, application/atom+xml, application/xml, text/xml, */*"
        const val NOT_MODIFIED = 304
        const val ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd"
        val HTTP_OK_RANGE = 200..299
    }
}
