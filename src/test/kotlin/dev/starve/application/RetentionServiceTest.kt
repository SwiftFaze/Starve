package dev.starve.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class RetentionServiceTest {
    @Test
    fun `purges items older than the max age and reports how many were removed`() {
        val items = MemoryItems(deleted = 3)
        val clock = Clock.fixed(Instant.parse("2026-10-11T00:00:00Z"), ZoneOffset.UTC)

        val removed = RetentionService(items, clock, Duration.ofDays(90)).purgeOld()

        assertEquals(3, removed)
        assertEquals(Instant.parse("2026-07-13T00:00:00Z"), items.cutoff)
    }
}
