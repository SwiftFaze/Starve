package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class BrokenRulesTest {
    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private val day = Duration.ofHours(24)

    private fun broken(since: Instant?): Boolean {
        val source = Source(1, "http://a.test/f", "A", Validators(), now, "HTTP 500", failingSince = since)
        return BrokenRules.isBroken(source, now)
    }

    @Test
    fun `a source whose last fetch succeeded is not broken`() = assertFalse(broken(null))

    @Test
    fun `a source failing for just under a day is not broken yet`() = assertFalse(broken(now.minus(day).plusMillis(1)))

    @Test
    fun `a source failing for exactly a day is broken`() = assertTrue(broken(now.minus(day)))

    @Test
    fun `a source failing for a week is broken`() = assertTrue(broken(now.minus(Duration.ofDays(7))))
}
