package dev.starve.web

import dev.starve.application.GroupService
import dev.starve.application.SourceHealthService
import dev.starve.application.SourceScheduleService
import dev.starve.application.SourceStanding
import dev.starve.application.TagService
import dev.starve.domain.BrokenRules
import dev.starve.domain.PollInterval
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.ui.Model
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class SourceView(
    val id: Long,
    val title: String,
    val url: String,
    val lastFetched: FetchTime?,
    val error: String?,
    val groupId: Long?,
    val stats: SourceStatsLine,
    /** How often it is fetched; null for the default. */
    val interval: PollInterval?,
    /** True while a Fetch now of it is queued or running. */
    val fetching: Boolean,
    /** When it started failing, once it has failed long enough to be broken; null otherwise. */
    val brokenSince: FetchTime? = null,
    /** Where a permanent redirect moved it, when that URL is already another of the owner's sources. */
    val movedTo: String? = null,
)

/** What the Sources roster and each of its rows render from: the rows, the groups, the tags and the intervals. */
@Component
class SourceRoster(
    private val health: SourceHealthService,
    private val groups: GroupService,
    private val tags: TagService,
    private val schedule: SourceScheduleService,
    private val clock: Clock,
    @Value("\${starve.poll.interval:PT30M}") private val defaultInterval: Duration,
) {
    /** Adds [owner]'s rows to [model], or only source [only]'s row when given; false when that row is not theirs. */
    fun addTo(
        model: Model,
        owner: Long,
        only: Long? = null,
    ): Boolean {
        val now = clock.instant()
        val rows = health.roster(owner).filter { only == null || it.source.id == only }.map { view(it, now) }
        model.addAttribute("sources", rows)
        model.addAttribute("groups", groups.list(owner))
        model.addAttribute("tags", tags.list(owner))
        model.addAttribute("intervals", PollInterval.entries)
        model.addAttribute("defaultInterval", "Default (${everyPhrase(defaultInterval)})")
        return only == null || rows.isNotEmpty()
    }

    private fun view(
        standing: SourceStanding,
        now: Instant,
    ): SourceView {
        val source = standing.source
        return SourceView(
            id = source.id,
            title = source.title,
            url = source.url,
            lastFetched = source.lastFetchedAt?.let { FetchTime.of(it, now) },
            error = source.lastError,
            groupId = source.groupId,
            stats = SourceStatsLine.of(standing.health, now),
            interval = source.pollInterval,
            fetching = schedule.isFetching(source.id),
            brokenSince = BrokenRules.brokenSince(source, now)?.let { FetchTime.of(it, now) },
            movedTo = source.movedTo,
        )
    }
}
