package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class FailureCountTest {
    private val t0 = Instant.parse("2026-10-14T10:00:00Z")

    @Test
    fun `the fifth consecutive failure blocks for fifteen minutes`() {
        var count = FailureCount()
        repeat(4) { count = count.failed(t0) }
        assertFalse(count.isBlocked(t0))

        count = count.failed(t0)
        assertTrue(count.isBlocked(t0))
        assertTrue(count.isBlocked(t0.plusSeconds(899)))
        assertFalse(count.isBlocked(t0.plus(Duration.ofMinutes(15))))
    }

    @Test
    fun `failures made while blocked change nothing`() {
        var count = FailureCount()
        repeat(5) { count = count.failed(t0) }
        val blocked = count
        assertTrue(count.failed(t0.plusSeconds(60)) == blocked)
    }

    @Test
    fun `once a block has run out the count starts again from one`() {
        var count = FailureCount()
        repeat(5) { count = count.failed(t0) }
        val later = t0.plus(Duration.ofMinutes(16))

        count = count.failed(later)

        assertFalse(count.isBlocked(later))
        repeat(3) { count = count.failed(later) }
        assertFalse(count.isBlocked(later))
    }

    @Test
    fun `a failure is counted and the fifth one records when the block ends`() {
        val first = FailureCount().failed(t0)
        assertEquals(1, first.failures)
        assertEquals(null, first.blockedUntil)

        var count = first
        repeat(4) { count = count.failed(t0) }
        assertEquals(5, count.failures)
        assertEquals(t0.plus(Duration.ofMinutes(15)), count.blockedUntil)
    }
}
