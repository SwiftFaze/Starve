package dev.starve.application

import dev.starve.domain.Source
import dev.starve.domain.SourceActivity
import dev.starve.domain.SourceHealth
import dev.starve.domain.SourceHealthRules
import java.time.Clock

/** One source of the roster with its statistics. */
data class SourceStanding(
    val source: Source,
    val health: SourceHealth,
)

/** The statistics shown beside each source on Sources, computed on demand. */
class SourceHealthService(
    private val sources: SourceRepository,
    private val stats: SourceStatsRepository,
    private val clock: Clock,
) {
    /** Each of [owner]'s sources, in roster order, with its health. */
    fun roster(owner: Long): List<SourceStanding> {
        val now = clock.instant()
        val activity = stats.activity(owner, SourceHealthRules.windowStart(now), now)
        return sources.findAllOf(owner).map { source ->
            val counted = activity[source.id] ?: SourceActivity.NONE
            SourceStanding(source, SourceHealthRules.health(counted, source.lastFetchedAt != null, now))
        }
    }
}
