package dev.starve.infra

import dev.starve.application.EmbedChecker
import dev.starve.application.FeedFetchException
import dev.starve.domain.Playable
import java.net.URLEncoder

/** Asks YouTube oEmbed whether a video may be embedded: 401/403 means no, anything else fails open. */
class YouTubeEmbedChecker(
    private val http: BoundedHttp,
    private val oembedUrl: String,
) : EmbedChecker {
    override fun allowsEmbedding(video: Playable.YouTube): Boolean =
        try {
            val watch = URLEncoder.encode(video.watchUrl, Charsets.UTF_8)
            http.get("$oembedUrl?url=$watch&format=json", "application/json").statusCode() !in DISABLED
        } catch (_: FeedFetchException) {
            true
        }

    private companion object {
        val DISABLED = setOf(401, 403)
    }
}
