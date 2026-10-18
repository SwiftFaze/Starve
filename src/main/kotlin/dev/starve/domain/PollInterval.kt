package dev.starve.domain

import java.time.Duration
import java.time.Instant

private const val QUARTER_HOUR = 15L
private const val HALF_HOUR = 30L
private const val HOUR = 60L
private const val QUARTER_DAY = 6 * HOUR
private const val DAY = 24 * HOUR

/** How often a source may be fetched, chosen per source from a fixed set; a source without one uses the default. */
enum class PollInterval(
    val minutes: Long,
    val label: String,
) {
    FIFTEEN_MINUTES(QUARTER_HOUR, "15 minutes"),
    THIRTY_MINUTES(HALF_HOUR, "30 minutes"),
    HOURLY(HOUR, "1 hour"),
    SIX_HOURS(QUARTER_DAY, "6 hours"),
    DAILY(DAY, "Daily"),
    ;

    val duration: Duration get() = Duration.ofMinutes(minutes)

    companion object {
        /** The interval stored as [minutes]; null for none, or for a value no longer offered. */
        fun ofMinutes(minutes: Long?): PollInterval? = entries.firstOrNull { it.minutes == minutes }

        /** The interval named [name]; null when the name is blank or unknown. */
        fun named(name: String?): PollInterval? = entries.firstOrNull { it.name == name?.trim() }
    }
}

/** When a source is next fetched by the background poll. */
object PollRules {
    /**
     * True when [source] has never been tried, or its interval ([default] when it has none) has passed since its
     * last attempt, success or failure.
     */
    fun isDue(
        source: Source,
        now: Instant,
        default: Duration,
    ): Boolean {
        val last = source.lastFetchedAt ?: return true
        val interval = source.pollInterval?.duration ?: default
        return !now.isBefore(last.plus(interval))
    }
}
