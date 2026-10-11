package dev.starve.application

import dev.starve.domain.FeedRules
import java.time.Clock
import java.time.Duration

class RetentionService(
    private val items: ItemRepository,
    private val clock: Clock,
    private val maxAge: Duration,
) {
    /** Deletes items first seen longer ago than the retention age; returns the number removed. */
    fun purgeOld(): Int = items.deleteFirstSeenBefore(FeedRules.retentionCutoff(clock.instant(), maxAge))
}
