package dev.starve.infra

import dev.starve.application.FeedFetchException
import dev.starve.application.Oembed
import dev.starve.application.SoundCloudOembed
import java.net.URLEncoder

/**
 * SoundCloud's public oEmbed (no API key). Keeps the author, the artwork and the description, sanitised; the
 * iframe `html` is ignored because the deck does the playing. Any failure gives null, so nothing is cached.
 */
class SoundCloudOembedClient(
    private val http: BoundedHttp,
    private val oembedUrl: String,
) : SoundCloudOembed {
    override fun fetch(trackUrl: String): Oembed? =
        try {
            val query = URLEncoder.encode(trackUrl, Charsets.UTF_8)
            val response = http.get("$oembedUrl?url=$query&format=json", "application/json")
            if (response.statusCode() == OK) parse(String(response.body(), Charsets.UTF_8)) else null
        } catch (_: FeedFetchException) {
            null
        }

    private fun parse(json: String): Oembed? =
        OembedJson.decode(json)?.let {
            Oembed(
                authorName = it.authorName,
                thumbnailUrl = it.thumbnailUrl?.takeIf { url -> WEB_LINK.matches(url) },
                description = BodySanitizer.sanitize(it.description, null),
            )
        }

    private companion object {
        const val OK = 200
        val WEB_LINK = Regex("(?i)https?://.*")
    }
}
