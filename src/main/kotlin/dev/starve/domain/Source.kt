package dev.starve.domain

import java.time.Instant

/** A subscription to one feed URL, with the outcome of its latest fetch attempt. */
data class Source(
    val id: Long,
    val url: String,
    val title: String,
    val validators: Validators,
    val lastFetchedAt: Instant?,
    val lastError: String?,
    val groupId: Long? = null,
    /** The site's icon, when one was found; shown at render time, never copied onto items. */
    val faviconUrl: String? = null,
    /** When the icon was last looked up, found or not; null when never. */
    val faviconCheckedAt: Instant? = null,
)

/** HTTP cache validators remembered from the last successful fetch, for conditional GET. */
data class Validators(
    val etag: String? = null,
    val lastModified: String? = null,
)
