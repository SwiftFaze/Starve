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

    /** Items first seen strictly before this instant are expired. */
    fun retentionCutoff(
        now: Instant,
        maxAge: Duration,
    ): Instant = now.minus(maxAge)
}
