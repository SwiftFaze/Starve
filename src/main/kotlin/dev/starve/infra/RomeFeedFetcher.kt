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
import org.jdom2.Element
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.http.HttpResponse

/**
 * Fetches feeds over HTTP with a conditional GET and parses RSS/Atom with ROME. [http] must not follow redirects:
 * [FeedRedirects] follows them, so that a permanent move can be reported.
 */
class RomeFeedFetcher(
    http: BoundedHttp,
) : FeedFetcher {
    private val redirects = FeedRedirects(http)

    override fun fetch(
        url: String,
        validators: Validators,
    ): FetchResult {
        val landing = redirects.get(url, ACCEPT, conditionalHeaders(validators))
        val response = landing.response
        val status = response.statusCode()
        return when {
            status == NOT_MODIFIED -> {
                FetchResult.NotModified(landing.movedTo)
            }

            status in HTTP_OK_RANGE -> {
                FetchResult.Fetched(parse(response.body()), responseValidators(response), landing.movedTo)
            }

            else -> {
                throw FeedFetchException("HTTP $status")
            }
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
        val siteLink = syndFeed.link?.trim()?.takeIf { it.isNotEmpty() }
        return Feed(
            title = syndFeed.title?.trim()?.takeIf { it.isNotEmpty() },
            items = syndFeed.entries.mapNotNull { toItem(it, siteLink) },
            siteLink = siteLink,
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

    private fun toItem(
        entry: SyndEntry,
        siteLink: String?,
    ): FeedItem? {
        val key = FeedRules.itemKey(entry.uri, entry.link) ?: return null
        val link = entry.link?.takeIf { it.isNotBlank() }
        val published = entry.publishedDate ?: entry.updatedDate
        val body = rawBody(entry)
        return FeedItem(
            key = key,
            title = decodedTitle(entry),
            link = link,
            publishedAt = published?.toInstant(),
            author = entry.author?.takeIf { it.isNotBlank() },
            thumbnailUrl = thumbnail(entry, link ?: siteLink, body),
            body = BodySanitizer.sanitize(body, link),
        )
    }

    /** The entry's full content (content:encoded, Atom content) when it has any, else its description. */
    private fun rawBody(entry: SyndEntry): String? =
        entry.contents.firstNotNullOfOrNull { it.value?.takeIf(String::isNotBlank) }
            ?: entry.description?.value

    private fun decodedTitle(entry: SyndEntry): String {
        val decoded = TitleEntities.decode(entry.title.orEmpty()).trim()
        return decoded.ifEmpty { "(untitled)" }
    }

    /**
     * The first usable image of the entry, in order: media:thumbnail, a media:content that is an image, an image
     * enclosure, itunes:image, then the first body image that is not a tracking pixel. Relative URLs resolve
     * against [base], the item's link or else the site link.
     */
    private fun thumbnail(
        entry: SyndEntry,
        base: String?,
        rawBody: String?,
    ): String? {
        val media = entry.foreignMarkup.flatMap { if (it.name == "group") it.children else listOf(it) }
        val candidates =
            sequenceOf(
                media.firstOrNull { it.name == "thumbnail" }?.getAttributeValue("url"),
                media.firstOrNull { it.name == "content" && isImage(it) }?.getAttributeValue("url"),
                entry.enclosures.firstOrNull { it.type?.startsWith("image/") == true }?.url,
                media.firstOrNull { it.name == "image" && it.namespaceURI == ITUNES }?.getAttributeValue("href"),
            )
        val found =
            candidates.firstNotNullOfOrNull { FeedRules.absoluteHttpUrl(it, base) }
                ?: rawBody?.let { BodyImages.firstUrl(it, base) }
        return found?.let(FeedRules::thumbnailUrl)
    }

    /** A media:content is an image when its type says so, or its medium does. */
    private fun isImage(element: Element): Boolean =
        element.getAttributeValue("type")?.startsWith("image/") == true ||
            element.getAttributeValue("medium") == "image"

    private companion object {
        const val ACCEPT = "application/rss+xml, application/atom+xml, application/xml, text/xml, */*"
        const val NOT_MODIFIED = 304
        const val ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd"
        val HTTP_OK_RANGE = 200..299
    }
}
