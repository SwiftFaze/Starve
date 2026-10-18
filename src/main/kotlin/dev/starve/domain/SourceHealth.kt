package dev.starve.domain

import java.time.Duration
import java.time.Instant

/** What a source's kept items say about it, counted in storage. */
data class SourceActivity(
    /** When its newest kept item was published; null when none is kept. */
    val lastPostAt: Instant?,
    /** Items published within [SourceHealthRules.WINDOW] before now. */
    val recentPosts: Int,
    val unread: Int,
    /** Items read since open tracking began. */
    val trackedReads: Int,
    /** Of [trackedReads], the ones never opened. */
    val skipped: Int,
) {
    companion object {
        val NONE = SourceActivity(null, 0, 0, 0, 0)
    }
}

/** How alive a source is and how much of it gets read, with the marks that make it a candidate to unsubscribe. */
data class SourceHealth(
    val lastPostAt: Instant?,
    val postsPerWeek: Double,
    val unread: Int,
    /** Percent of tracked reads that were skips; null until [SourceHealthRules.MIN_SAMPLE] reads. */
    val skipPercent: Int?,
    val trackedReads: Int,
    val quiet: Boolean,
    val mostlySkipped: Boolean,
)

/** Thresholds for the source statistics on Sources. */
object SourceHealthRules {
    private const val WINDOW_DAYS = 28L
    private const val QUIET_DAYS = 60L

    /** Posts per week are averaged over this window. */
    val WINDOW: Duration = Duration.ofDays(WINDOW_DAYS)

    /** A source with no post published for longer than this is quiet. */
    val QUIET_AFTER: Duration = Duration.ofDays(QUIET_DAYS)

    /** Reads needed before a skip rate means anything. */
    const val MIN_SAMPLE = 20

    /** A skip rate at or above this percent marks a source mostly skipped. */
    const val MOSTLY_SKIPPED_PERCENT = 80

    private const val WEEKS_IN_WINDOW = 4.0
    private const val PERCENT = 100

    /** The start of the posts-per-week window ending at [now]. */
    fun windowStart(now: Instant): Instant = now.minus(WINDOW)

    /** [activity] judged at [now]; a source never [fetched] is not called quiet for having no posts. */
    fun health(
        activity: SourceActivity,
        fetched: Boolean,
        now: Instant,
    ): SourceHealth {
        val skipPercent = skipPercent(activity)
        return SourceHealth(
            lastPostAt = activity.lastPostAt,
            postsPerWeek = activity.recentPosts / WEEKS_IN_WINDOW,
            unread = activity.unread,
            skipPercent = skipPercent,
            trackedReads = activity.trackedReads,
            quiet = isQuiet(activity.lastPostAt, fetched, now),
            mostlySkipped = skipPercent != null && skipPercent >= MOSTLY_SKIPPED_PERCENT,
        )
    }

    private fun skipPercent(activity: SourceActivity): Int? =
        if (activity.trackedReads < MIN_SAMPLE) null else activity.skipped * PERCENT / activity.trackedReads

    private fun isQuiet(
        lastPostAt: Instant?,
        fetched: Boolean,
        now: Instant,
    ): Boolean = if (lastPostAt == null) fetched else lastPostAt.isBefore(now.minus(QUIET_AFTER))
}
