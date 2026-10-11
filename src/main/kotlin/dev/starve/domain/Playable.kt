package dev.starve.domain

import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder

/** What an item's link can play inline: a YouTube video or a SoundCloud track. */
sealed interface Playable {
    /** The iframe source that plays it. */
    val embedUrl: String

    data class YouTube(
        val videoId: String,
    ) : Playable {
        val watchUrl: String get() = "https://www.youtube.com/watch?v=$videoId"
        override val embedUrl: String get() = "https://www.youtube-nocookie.com/embed/$videoId?autoplay=1"
    }

    data class SoundCloud(
        val trackUrl: String,
    ) : Playable {
        override val embedUrl: String
            get() =
                "https://w.soundcloud.com/player/?url=${URLEncoder.encode(trackUrl, Charsets.UTF_8)}&auto_play=true"
    }

    companion object {
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
        private val YOUTUBE_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com")
        private val SOUNDCLOUD_HOSTS = setOf("soundcloud.com", "www.soundcloud.com")

        /** The player for [link], or null when it is not a YouTube video or SoundCloud track link. */
        fun of(link: String?): Playable? {
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
