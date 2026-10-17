package dev.starve.web

import java.time.Duration

/**
 * How the list on screen ends. [unread] is its view's unread count, or null for Favourites and Later, which have
 * none. With nothing unread the view is [caughtUp]; otherwise it ends with an "End of list" marker that fetches
 * itself again from [refreshHref] after an item is read. [checked] says how often the sources are polled.
 */
data class ListEnd(
    val unread: Int?,
    val checked: String,
    val href: String,
) {
    val caughtUp: Boolean get() = unread == 0

    val refreshHref: String? get() = href.takeIf { unread != null }
}

/** [interval] as the caught-up note says it: "every 30 minutes", "every hour", "every 2 hours". */
internal fun everyPhrase(interval: Duration): String {
    val minutes = interval.toMinutes().coerceAtLeast(1)
    return if (minutes % MINUTES_PER_HOUR == 0L) every(minutes / MINUTES_PER_HOUR, "hour") else every(minutes, "minute")
}

private fun every(
    count: Long,
    unit: String,
) = if (count == 1L) "every $unit" else "every $count ${unit}s"

private const val MINUTES_PER_HOUR = 60L
