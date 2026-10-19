package dev.starve.domain

import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder

/** What an item can play inline: its link's YouTube video or SoundCloud track, or its audio enclosure. */
sealed interface Playable {
    /** The source that plays it: the iframe's, or the audio element's. */
    val embedUrl: String

    data class YouTube(
        val videoId: String,
    ) : Playable {
        val watchUrl: String get() = "https://www.youtube.com/watch?v=$videoId"

        // The deck drives playback over the iframe API alongside YouTube's own controls, which stay visible.
        override val embedUrl: String
            get() =
                "https://www.youtube-nocookie.com/embed/$videoId" +
                    "?autoplay=1&enablejsapi=1&controls=1&rel=0&iv_load_policy=3&playsinline=1"
    }

    data class SoundCloud(
        val trackUrl: String,
    ) : Playable {
        override val embedUrl: String
            get() =
                "https://w.soundcloud.com/player/?url=${URLEncoder.encode(trackUrl, Charsets.UTF_8)}&auto_play=true"
    }

    /** A podcast episode's audio file, streamed from its publisher by an `<audio>` element. */
    data class Audio(
        val url: String,
    ) : Playable {
        override val embedUrl: String get() = url
    }

    companion object {
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
        private val YOUTUBE_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com")
        private val SOUNDCLOUD_HOSTS = setOf("soundcloud.com", "www.soundcloud.com")

        /**
         * The player of an item with [link] and [audioUrl]: its link's video or track when the link is one, else its
         * http or https audio enclosure; null when it has neither.
         */
        fun of(
            link: String?,
            audioUrl: String? = null,
        ): Playable? = ofLink(link) ?: audioUrl?.takeIf { FeedRules.isHttpUrl(it) }?.let(::Audio)

        private fun ofLink(link: String?): Playable? {
            val uri = link?.let(::parse) ?: return null
            val host = uri.host?.lowercase()
            return when {
                uri.scheme?.lowercase() !in setOf("http", "https") -> null
                host in YOUTUBE_HOSTS -> youTube(uri)
                host == "youtu.be" -> videoId(uri.path.removePrefix("/"))
                host in SOUNDCLOUD_HOSTS -> SoundCloud(link)
                else -> null
            }
        }

        private fun parse(raw: String): URI? =
            try {
                URI(raw.trim())
            } catch (_: URISyntaxException) {
                null
            }

        private fun youTube(uri: URI): Playable? =
            when {
                uri.path == "/watch" -> videoId(queryParam(uri, "v"))
                uri.path.startsWith("/shorts/") -> videoId(uri.path.removePrefix("/shorts/"))
                else -> null
            }

        private fun queryParam(
            uri: URI,
            name: String,
        ): String? =
            uri.rawQuery
                ?.split('&')
                ?.firstOrNull { it.startsWith("$name=") }
                ?.substringAfter('=')

        private fun videoId(candidate: String?): Playable? {
            val id = candidate?.takeIf { VIDEO_ID.matches(it) }
            return id?.let { YouTube(it) }
        }
    }
}
