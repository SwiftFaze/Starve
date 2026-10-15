package dev.starve.application

import dev.starve.domain.LaterCursor
import dev.starve.domain.LaterRules
import dev.starve.domain.TimelineRules
import java.time.Clock

/** Saves items for later, clears them, and pages the Later view. Saving leaves read state and favourites alone. */
class LaterService(
    private val later: LaterRepository,
    private val sources: SourceRepository,
    private val clock: Clock,
) {
    /** Saves [owner]'s item, keeping its original saved time if it already is saved; false when it does not exist. */
    fun save(
        owner: Long,
        id: Long,
    ): Boolean = later.save(owner, id, clock.instant())

    /** Clears the saved mark; returns false when [owner] has no such item. */
    fun clear(
        owner: Long,
        id: Long,
    ): Boolean = later.clear(owner, id)

    /** The Later view: [owner]'s saved items of every source, read or not, earliest saved first. */
    fun page(
        owner: Long,
        after: LaterCursor?,
    ): TimelinePage {
        val renderedAt = clock.instant()
        val (shown, next) = LaterRules.paginate(later.later(owner, after, TimelineRules.PAGE_SIZE + 1))
        return TimelinePage(shown.map { it.item }, next, renderedAt, sources.findAllOf(owner).isNotEmpty())
    }
}
