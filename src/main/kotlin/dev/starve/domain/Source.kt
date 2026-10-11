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
)

/** HTTP cache validators remembered from the last successful fetch, for conditional GET. */
data class Validators(
    val etag: String? = null,
    val lastModified: String? = null,
)
