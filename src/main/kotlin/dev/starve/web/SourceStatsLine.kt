package dev.starve.web

import dev.starve.domain.SourceHealth
import dev.starve.domain.SourceHealthRules
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * A source's health in words: [text] reads "Last post 3 days ago · 2/week · 10 unread · skipped 95%", and [marks]
 * name what makes it a candidate to unsubscribe ("Quiet", "Mostly skipped"). Before enough reads the skip rate says
 * how many it has so far; with no tracked read at all it is left out.
 */
data class SourceStatsLine(
    val text: String,
    val marks: List<String>,
) {
    companion object {
        fun of(
            health: SourceHealth,
            now: Instant,
        ): SourceStatsLine {
            val parts =
                listOfNotNull(
                    lastPost(health.lastPostAt, now),
                    "${perWeek(health.postsPerWeek)}/week",
                    "${health.unread} unread",
                    skips(health),
                )
            val text = parts.joinToString(" · ")
            val marks = listOfNotNull("Quiet".takeIf { health.quiet }, "Mostly skipped".takeIf { health.mostlySkipped })
            return SourceStatsLine(text, marks)
        }

        /** "Last post today", "yesterday", "N days ago" (under two weeks), weeks, 30-day months, then years. */
        fun lastPost(
            at: Instant?,
            now: Instant,
        ): String {
            if (at == null) return "No recent posts"
            val days = Duration.between(at, now).toDays().coerceAtLeast(0)
            val age =
                when {
                    days == 0L -> "today"
                    days == 1L -> "yesterday"
                    days < TWO_WEEKS -> "$days days ago"
                    days < TWO_MONTHS -> plural(days / WEEK, "week")
                    days < YEAR -> plural(days / MONTH, "month")
                    else -> plural(days / YEAR, "year")
                }
            return "Last post $age"
        }

        /** One decimal under ten ("0.3", "2" for 2.0), whole numbers from ten. */
        fun perWeek(rate: Double): String {
            val text = if (rate < WHOLE_FROM) String.format(Locale.ROOT, "%.1f", rate) else rate.toLong().toString()
            return text.removeSuffix(".0")
        }

        private fun skips(health: SourceHealth): String? =
            when {
                health.skipPercent != null -> "skipped ${health.skipPercent}%"
                health.trackedReads == 0 -> null
                else -> "skip rate after ${SourceHealthRules.MIN_SAMPLE} reads (${health.trackedReads} so far)"
            }

        private fun plural(
            n: Long,
            unit: String,
        ) = if (n == 1L) "1 $unit ago" else "$n ${unit}s ago"

        private const val WEEK = 7L
        private const val TWO_WEEKS = 14L
        private const val MONTH = 30L
        private const val TWO_MONTHS = 60L
        private const val YEAR = 365L
        private const val WHOLE_FROM = 10.0
    }
}
