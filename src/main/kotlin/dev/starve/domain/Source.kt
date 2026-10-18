package dev.starve.domain

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
    val groupId: Long? = null,
    /** The site's icon, when one was found; shown at render time, never copied onto items. */
    val faviconUrl: String? = null,
    /** When the icon was last looked up, found or not; null when never. */
    val faviconCheckedAt: Instant? = null,
    /** How often it is fetched; null for the global default. */
    val pollInterval: PollInterval? = null,
    /** When its current run of failed fetches began; null while its last fetch succeeded. */
    val failingSince: Instant? = null,
    /** Where a permanent redirect moved it, when that URL is already another of the owner's sources. */
    val movedTo: String? = null,
)

/** When a failing source counts as broken rather than having hiccupped. */
object BrokenRules {
    /** How long a source has to keep failing to be broken, whatever its poll interval. */
    val AFTER: Duration = Duration.ofDays(1)

    /** True when [source] has been failing without a break for at least [AFTER] at [now]. */
    fun isBroken(
        source: Source,
        now: Instant,
    ): Boolean = brokenSince(source, now) != null

    /** When [source] started failing, if it is broken at [now]; null when it is not. */
    fun brokenSince(
        source: Source,
        now: Instant,
    ): Instant? = source.failingSince?.takeUnless { now.isBefore(it.plus(AFTER)) }
}

/** HTTP cache validators remembered from the last successful fetch, for conditional GET. */
data class Validators(
    val etag: String? = null,
    val lastModified: String? = null,
)
