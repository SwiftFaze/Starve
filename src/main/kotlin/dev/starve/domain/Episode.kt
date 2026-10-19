package dev.starve.domain

/** A podcast episode's audio file: where it is, and how long it runs in seconds when the feed says. */
data class AudioEnclosure(
    val url: String,
    val durationSeconds: Long? = null,
)

/** How the length of an episode is read from its feed. */
object EpisodeRules {
    private const val SECONDS_PER_MINUTE = 60L
    private const val MAX_PARTS = 3
    private val NUMBER = Regex("\\d+")

    /**
     * The seconds an `itunes:duration` names: plain seconds ("3723"), or "MM:SS" or "HH:MM:SS"; a fraction of a
     * second is dropped. Null when it is blank, malformed or zero.
     */
    fun durationSeconds(raw: String?): Long? {
        val parts =
            raw
                ?.trim()
                ?.substringBefore('.')
                ?.split(':')
                ?.takeIf { parts -> parts.size <= MAX_PARTS && parts.all { NUMBER.matches(it) } }
        val seconds = parts?.fold(0L) { total, part -> total * SECONDS_PER_MINUTE + part.toLong() }
        return seconds?.takeIf { it > 0 }
    }
}
