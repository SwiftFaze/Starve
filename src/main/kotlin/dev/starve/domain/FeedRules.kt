package dev.starve.domain

import java.net.URI
import java.net.URISyntaxException
import java.time.Duration
import java.time.Instant

/** Rules for feed identity, subscription URLs and retention. */
object FeedRules {
    /** Identity of an entry within its source: the guid, else the link; null when it has neither. */
    fun itemKey(
        guid: String?,
        link: String?,
    ): String? = listOf(guid, link).firstOrNull { !it.isNullOrBlank() }?.trim()

    fun isHttpUrl(raw: String): Boolean =
        try {
            val uri = URI(raw.trim())
            uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
        } catch (_: URISyntaxException) {
            false
        }

    /**
     * [raw] as an absolute http(s) URL: resolved against [base] when it is relative and a base is given;
     * null when blank, unparseable, or not http(s) once resolved.
     */
    fun absoluteHttpUrl(
        raw: String?,
        base: String?,
    ): String? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching {
            val target = URI(text)
            val resolved = if (target.isAbsolute || base == null) target else URI(base.trim()).resolve(target)
            resolved.toString().takeIf { isHttpUrl(it) }
        }.getOrNull()
    }

    /**
     * The thumbnail to store for an image URL. SoundCloud cover art arrives at up to 3000x3000; the
     * timeline shows it a few dozen pixels wide, so it is stored as SoundCloud's 500x500 variant.
     */
    fun thumbnailUrl(raw: String): String =
        if (SOUNDCLOUD_ART_HOST.containsMatchIn(raw)) raw.replace(SOUNDCLOUD_ART_SIZE, "-t500x500.") else raw

    /** Items first seen strictly before this instant are expired. */
    fun retentionCutoff(
        now: Instant,
        maxAge: Duration,
    ): Instant = now.minus(maxAge)

    private val SOUNDCLOUD_ART_HOST = Regex("""^https?://i\d*\.sndcdn\.com/""")
    private val SOUNDCLOUD_ART_SIZE = Regex("""-(?:t\d+x\d+|original|large)\.(?=[a-z]+$)""")
}
