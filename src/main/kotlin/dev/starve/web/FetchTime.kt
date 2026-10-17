package dev.starve.web

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A source's last fetch time, written three ways: [short] fits beside the row's controls ("17:13 UTC" today,
 * "yesterday 09:02 UTC", else "2026-10-12 14:30 UTC"), [full] keeps the seconds for its tooltip, and [iso] is the
 * exact instant for its `<time datetime>`. Days are UTC days, like every time Starve shows.
 */
data class FetchTime(
    val short: String,
    val full: String,
    val iso: String,
) {
    companion object {
        fun of(
            at: Instant,
            now: Instant,
        ): FetchTime {
            val day = at.atZone(ZoneOffset.UTC).toLocalDate()
            val today = now.atZone(ZoneOffset.UTC).toLocalDate()
            val time = "${MINUTES.format(at)} UTC"
            val short =
                when (day) {
                    today -> time
                    today.minusDays(1) -> "yesterday $time"
                    else -> "${DAY.format(at)} $time"
                }
            return FetchTime(short, FULL.format(at), at.toString())
        }

        private val MINUTES = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC)
        private val DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
        private val FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC)
    }
}
