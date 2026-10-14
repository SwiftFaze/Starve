package dev.starve.application

import dev.starve.domain.FailureCount
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/** Counts failed attempts per key in memory; a restart clears every count. */
class FailureThrottle(
    private val clock: Clock,
) {
    private val counts = ConcurrentHashMap<String, FailureCount>()

    fun isBlocked(key: String): Boolean = counts[key]?.isBlocked(clock.instant()) == true

    fun recordFailure(key: String) {
        counts.compute(key) { _, count -> (count ?: FailureCount()).failed(clock.instant()) }
    }

    fun reset(key: String) {
        counts.remove(key)
    }
}
