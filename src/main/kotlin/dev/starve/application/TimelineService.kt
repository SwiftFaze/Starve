package dev.starve.application

import dev.starve.domain.Cursor
import dev.starve.domain.TimelineItem
import dev.starve.domain.TimelineRules
import java.time.Clock
import java.time.Instant

/** One rendered page of the timeline. [next] is null on the last page. */
data class TimelinePage(
    val items: List<TimelineItem>,
    val next: Cursor?,
    val renderedAt: Instant,
    val hasSources: Boolean,
)

sealed interface OpenResult {
    data object Missing : OpenResult

    /** The item was marked read; [link] is where it points, if anywhere. */
    data class Opened(
        val link: String?,
    ) : OpenResult
}

class TimelineService(
    private val items: ItemRepository,
    private val favourites: FavouriteRepository,
    private val sources: SourceRepository,
    private val clock: Clock,
) {
    fun page(
        after: Cursor?,
        showRead: Boolean,
        groupId: Long? = null,
    ): TimelinePage {
        val renderedAt = clock.instant()
        return pageOf(items.timeline(after, showRead, TimelineRules.PAGE_SIZE + 1, groupId), renderedAt)
    }

    /** The Favourites view: favourites of every source, read or not, newest first. */
    fun favourites(after: Cursor?): TimelinePage {
        val renderedAt = clock.instant()
        return pageOf(favourites.favourites(after, TimelineRules.PAGE_SIZE + 1), renderedAt)
    }

    private fun pageOf(
        rows: List<TimelineItem>,
        renderedAt: Instant,
    ): TimelinePage {
        val (shown, next) = TimelineRules.paginate(rows)
        return TimelinePage(shown, next, renderedAt, sources.findAll().isNotEmpty())
    }

    /** Returns false when the item does not exist. */
    fun markRead(id: Long): Boolean = items.markRead(id, clock.instant())

    fun open(id: Long): OpenResult {
        val target = items.find(id) ?: return OpenResult.Missing
        items.markRead(id, clock.instant())
        return OpenResult.Opened(target.link)
    }

    /**
     * Marks read everything (in [groupId] if given) first seen at or before [renderedAt];
     * later arrivals stay unread.
     */
    fun markAllRead(
        renderedAt: Instant,
        groupId: Long? = null,
    ): Int = items.markAllRead(renderedAt, clock.instant(), groupId)
}
