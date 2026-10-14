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
    /** Saves the item, keeping its original saved time if it already is saved; false when it does not exist. */
    fun save(id: Long): Boolean = later.save(id, clock.instant())

    /** Clears the saved mark; returns false when the item does not exist. */
    fun clear(id: Long): Boolean = later.clear(id)

    /** The Later view: saved items of every source, read or not, earliest saved first. */
    fun page(after: LaterCursor?): TimelinePage {
        val renderedAt = clock.instant()
        val (shown, next) = LaterRules.paginate(later.later(after, TimelineRules.PAGE_SIZE + 1))
        return TimelinePage(shown.map { it.item }, next, renderedAt, sources.findAll().isNotEmpty())
    }
}
