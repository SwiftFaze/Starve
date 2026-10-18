package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class SourceHealthRulesTest {
    private val now = Instant.parse("2026-10-18T12:00:00Z")

    private fun health(
        activity: SourceActivity,
        fetched: Boolean = true,
    ) = SourceHealthRules.health(activity, fetched, now)

    private fun postedAt(at: Instant) = SourceActivity(at, 0, 0, 0, 0)

    @Test
    fun `the window is the four weeks before now`() {
        assertEquals(now.minus(Duration.ofDays(28)), SourceHealthRules.windowStart(now))
    }

    @Test
    fun `posts per week average the window and the counts pass through`() {
        val h = health(SourceActivity(now, 10, 7, 3, 1))

        assertEquals(2.5, h.postsPerWeek)
        assertEquals(7, h.unread)
        assertEquals(3, h.trackedReads)
        assertEquals(now, h.lastPostAt)
    }

    @Test
    fun `a source is quiet only once its last post is more than 60 days old`() {
        val limit = now.minus(Duration.ofDays(60))

        assertFalse(health(postedAt(limit)).quiet)
        assertTrue(health(postedAt(limit.minusMillis(1))).quiet)
    }

    @Test
    fun `a source with nothing kept is quiet once fetched, not before`() {
        assertTrue(health(SourceActivity.NONE, fetched = true).quiet)
        assertFalse(health(SourceActivity.NONE, fetched = false).quiet)
    }

    @Test
    fun `the skip rate waits for 20 reads`() {
        val h = health(SourceActivity(now, 0, 0, 19, 19))

        assertNull(h.skipPercent)
        assertFalse(h.mostlySkipped)
    }

    @Test
    fun `from 20 reads the skip rate is a floored percent, mostly skipped from 80`() {
        val at80 = health(SourceActivity(now, 0, 0, 20, 16))
        val below = health(SourceActivity(now, 0, 0, 21, 16))

        assertEquals(80, at80.skipPercent)
        assertTrue(at80.mostlySkipped)
        assertEquals(76, below.skipPercent)
        assertFalse(below.mostlySkipped)
    }
}
