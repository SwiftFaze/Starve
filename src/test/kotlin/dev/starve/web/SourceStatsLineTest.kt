package dev.starve.web

import dev.starve.domain.SourceHealth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class SourceStatsLineTest {
    private val now = Instant.parse("2026-10-18T12:00:00Z")

    private fun ago(days: Long) = SourceStatsLine.lastPost(now.minus(Duration.ofDays(days)), now)

    private fun line(
        skip: Int?,
        reads: Int,
        quiet: Boolean = false,
        skipped: Boolean = false,
    ) = SourceStatsLine.of(SourceHealth(now, 2.0, 3, skip, reads, quiet, skipped), now)

    @Test
    fun `the last post's age reads in days, then weeks, months and years`() {
        assertEquals(
            listOf(
                "Last post today",
                "Last post yesterday",
                "Last post 2 days ago",
                "Last post 13 days ago",
                "Last post 2 weeks ago",
                "Last post 8 weeks ago",
                "Last post 2 months ago",
                "Last post 12 months ago",
                "Last post 1 year ago",
                "Last post 2 years ago",
            ),
            listOf(0L, 1, 2, 13, 14, 59, 60, 364, 365, 730).map { ago(it) },
        )
    }

    @Test
    fun `a post dated in the future is today, and none kept says so`() {
        assertEquals("Last post today", SourceStatsLine.lastPost(now.plus(Duration.ofDays(3)), now))
        assertEquals("No recent posts", SourceStatsLine.lastPost(null, now))
    }

    @Test
    fun `the weekly rate keeps one decimal under ten and drops a trailing zero`() {
        assertEquals(
            listOf("0", "0.3", "2", "9.8", "10", "35"),
            listOf(0.0, 0.25, 2.0, 9.75, 10.25, 35.0).map { SourceStatsLine.perWeek(it) },
        )
    }

    @Test
    fun `the line carries the skip rate, or how many reads it has so far`() {
        assertEquals("Last post today · 2/week · 3 unread", line(null, 0).text)
        assertEquals("Last post today · 2/week · 3 unread · skip rate after 20 reads (5 so far)", line(null, 5).text)
        assertEquals("Last post today · 2/week · 3 unread · skipped 95%", line(95, 20).text)
    }

    @Test
    fun `the marks name what makes a source a candidate to unsubscribe`() {
        assertEquals(emptyList<String>(), line(null, 0).marks)
        assertEquals(listOf("Quiet"), line(null, 0, quiet = true).marks)
        assertEquals(listOf("Quiet", "Mostly skipped"), line(95, 20, quiet = true, skipped = true).marks)
    }
}
