package dev.starve.domain

import java.net.URI
import java.net.URISyntaxException
import java.time.Duration
import java.time.Instant

/** A subscription to one feed URL, with the outcome of its latest fetch attempt. */
data class Source(
    val id: Long,
    val url: String,
    val title: String,
    val validators: Validators,
    val lastFetchedAt: Instant?,
    val lastError: String?,
)

/** HTTP cache validators remembered from the last successful fetch, for conditional GET. */
data class Validators(
    val etag: String? = null,
    val lastModified: String? = null,
)

/** One parsed feed entry. [key] is its per-source identity (guid, else link). */
data class FeedItem(
    val key: String,
    val title: String,
    val link: String?,
    val publishedAt: Instant?,
    val author: String?,
    val thumbnailUrl: String?,
)

data class Feed(
    val title: String?,
    val items: List<FeedItem>,
)

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
