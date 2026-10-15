package dev.starve.application

import dev.starve.domain.Cursor
import dev.starve.domain.PageCursor
import dev.starve.domain.TimelineItem
import dev.starve.domain.TimelineRules
import java.time.Clock
import java.time.Instant

/** One rendered page of a timeline view. [next] is null on the last page. */
data class TimelinePage(
    val items: List<TimelineItem>,
    val next: PageCursor?,
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
    /** One page of [owner]'s timeline: unread items, read ones too when [showRead], only [groupId]'s if given. */
    fun page(
        owner: Long,
        after: Cursor?,
        showRead: Boolean,
        groupId: Long? = null,
    ): TimelinePage {
        val renderedAt = clock.instant()
        val rows = items.timeline(owner, after, TimelineFilter(showRead, groupId), TimelineRules.PAGE_SIZE + 1)
        return pageOf(owner, rows, renderedAt)
    }

    /** The Favourites view: [owner]'s favourites of every source, read or not, newest first. */
    fun favourites(
        owner: Long,
        after: Cursor?,
    ): TimelinePage {
        val renderedAt = clock.instant()
        return pageOf(owner, favourites.favourites(owner, after, TimelineRules.PAGE_SIZE + 1), renderedAt)
    }

    private fun pageOf(
        owner: Long,
        rows: List<TimelineItem>,
        renderedAt: Instant,
    ): TimelinePage {
        val (shown, next) = TimelineRules.paginate(rows)
        return TimelinePage(shown, next, renderedAt, sources.findAllOf(owner).isNotEmpty())
    }

    /** Returns false when [owner] has no such item. */
    fun markRead(
        owner: Long,
        id: Long,
    ): Boolean = items.markRead(owner, id, clock.instant())

    fun open(
        owner: Long,
        id: Long,
    ): OpenResult {
        val target = items.find(owner, id) ?: return OpenResult.Missing
        items.markRead(owner, id, clock.instant())
        return OpenResult.Opened(target.link)
    }

    /**
     * Marks read everything of [owner] (in [groupId] if given) first seen at or before [renderedAt];
     * later arrivals stay unread.
     */
    fun markAllRead(
        owner: Long,
        renderedAt: Instant,
        groupId: Long? = null,
    ): Int = items.markAllRead(owner, renderedAt, clock.instant(), groupId)
}
