package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class PollIntervalTest {
    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private val default = Duration.ofMinutes(30)

    private fun source(
        lastAttempt: Instant?,
        interval: PollInterval? = null,
    ) = Source(1, "http://a.test/f", "A", Validators(), lastAttempt, null, pollInterval = interval)

    @Test
    fun `the offered intervals, their labels and lengths`() {
        assertEquals(
            listOf("15 minutes", "30 minutes", "1 hour", "6 hours", "Daily"),
            PollInterval.entries.map { it.label },
        )
        assertEquals(listOf(15L, 30, 60, 360, 1440), PollInterval.entries.map { it.duration.toMinutes() })
    }

    @Test
    fun `an interval is found by its stored minutes or its name, and nothing else`() {
        assertEquals(PollInterval.SIX_HOURS, PollInterval.ofMinutes(360))
        assertNull(PollInterval.ofMinutes(45))
        assertNull(PollInterval.ofMinutes(null))
        assertEquals(PollInterval.DAILY, PollInterval.named(" DAILY "))
        assertNull(PollInterval.named("daily"))
        assertNull(PollInterval.named(null))
    }

    @Test
    fun `a source never tried is due`() {
        assertTrue(PollRules.isDue(source(null, PollInterval.DAILY), now, default))
    }

    @Test
    fun `a source is due once its own interval has passed since its last attempt`() {
        val quarter = PollInterval.FIFTEEN_MINUTES

        assertFalse(PollRules.isDue(source(now.minus(Duration.ofMinutes(15)).plusMillis(1), quarter), now, default))
        assertTrue(PollRules.isDue(source(now.minus(Duration.ofMinutes(15)), quarter), now, default))
    }

    @Test
    fun `a source without an interval uses the default`() {
        assertFalse(PollRules.isDue(source(now.minus(Duration.ofMinutes(29))), now, default))
        assertTrue(PollRules.isDue(source(now.minus(Duration.ofMinutes(30))), now, default))
    }
}
