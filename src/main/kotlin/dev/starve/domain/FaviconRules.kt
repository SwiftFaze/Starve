package dev.starve.domain

import java.net.URI
import java.time.Duration
import java.time.Instant

/** Where a source's site icon is looked up, and when it is due again. */
object FaviconRules {
    private const val RECHECK_DAYS = 7L

    /** A source's icon is looked up again once its last check is older than this. */
    val RECHECK_AFTER: Duration = Duration.ofDays(RECHECK_DAYS)

    /** Due on the first poll (never checked), then once the last check is older than [RECHECK_AFTER]. */
    fun isDue(
        checkedAt: Instant?,
        now: Instant,
    ): Boolean = checkedAt == null || checkedAt.isBefore(now.minus(RECHECK_AFTER))

    /** The page to read the icon from: the feed's site link when it is http(s), else the origin of the feed URL. */
    fun siteUrl(
        siteLink: String?,
        feedUrl: String,
    ): String = siteLink?.trim()?.takeIf { FeedRules.isHttpUrl(it) } ?: origin(feedUrl)

    /** The site's /favicon.ico, on the origin of [siteUrl]. */
    fun rootIconUrl(siteUrl: String): String = origin(siteUrl) + "favicon.ico"

    /** The scheme, host and port of [url] with a trailing slash, e.g. `https://blog.example/`. */
    fun origin(url: String): String {
        val uri = URI(url.trim())
        return "${uri.scheme}://${uri.rawAuthority}/"
    }
}
