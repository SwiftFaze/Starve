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
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Fetches feeds over HTTP with a conditional GET and parses RSS/Atom with ROME. */
class RomeFeedFetcher(
    private val client: HttpClient,
    private val timeout: Duration,
    private val userAgent: String,
) : FeedFetcher {
    override fun fetch(
        url: String,
        validators: Validators,
    ): FetchResult {
        val response = send(request(url, validators))
        val status = response.statusCode()
        return when {
            status == NOT_MODIFIED -> FetchResult.NotModified
            status in HTTP_OK_RANGE -> FetchResult.Fetched(parse(response.body()), responseValidators(response))
            else -> throw FeedFetchException("HTTP $status")
        }
    }

    private fun request(
        url: String,
        validators: Validators,
    ): HttpRequest {
        val builder =
            HttpRequest
                .newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
        validators.etag?.let { builder.header("If-None-Match", it) }
        validators.lastModified?.let { builder.header("If-Modified-Since", it) }
        return builder.GET().build()
    }

    /** Bounds the whole exchange, body included, by [timeout]; cancels the request when it expires. */
    @Suppress("ThrowsCount") // one mapping per distinct failure of the future
    private fun send(request: HttpRequest): HttpResponse<ByteArray> {
        val future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
        return try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw FeedFetchException("timed out after $timeout", e)
        } catch (e: ExecutionException) {
            throw FeedFetchException(e.cause?.message ?: e.javaClass.simpleName, e)
        } catch (e: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            throw FeedFetchException("interrupted", e)
        }
    }

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
        return media.firstOrNull { it.name == "thumbnail" }?.getAttributeValue("url")
            ?: entry.enclosures.firstOrNull { it.type?.startsWith("image/") == true }?.url
    }

    private companion object {
        const val NOT_MODIFIED = 304
        val HTTP_OK_RANGE = 200..299
    }
}
