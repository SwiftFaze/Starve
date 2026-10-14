package dev.starve.application

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

private class SteppedClock(
    var now: Instant,
) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}

class FailureThrottleTest {
    private val clock = SteppedClock(Instant.parse("2026-10-14T10:00:00Z"))
    private val throttle = FailureThrottle(clock)

    @Test
    fun `five failures block the key until fifteen minutes have passed`() {
        repeat(5) { throttle.recordFailure("login|rob|203.0.113.7") }
        assertTrue(throttle.isBlocked("login|rob|203.0.113.7"))

        clock.now = clock.now.plus(Duration.ofMinutes(15))
        assertFalse(throttle.isBlocked("login|rob|203.0.113.7"))
    }

    @Test
    fun `keys are counted separately`() {
        repeat(5) { throttle.recordFailure("login|rob|203.0.113.7") }
        assertFalse(throttle.isBlocked("login|rob|198.51.100.2"))
    }

    @Test
    fun `a reset clears the failures for the key`() {
        repeat(4) { throttle.recordFailure("login|rob|203.0.113.7") }
        throttle.reset("login|rob|203.0.113.7")
        throttle.recordFailure("login|rob|203.0.113.7")
        assertFalse(throttle.isBlocked("login|rob|203.0.113.7"))
    }
}
