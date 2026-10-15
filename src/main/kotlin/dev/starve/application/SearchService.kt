package dev.starve.application

import dev.starve.domain.Cursor
import dev.starve.domain.SearchRules
import dev.starve.domain.TimelineRules
import java.time.Clock

class SearchService(
    private val search: SearchRepository,
    private val sources: SourceRepository,
    private val clock: Clock,
) {
    /**
     * One page of [owner]'s items matching [text], newest first. Read state and groups do not apply. Text with no
     * words (only punctuation) matches nothing.
     */
    fun search(
        owner: Long,
        text: String,
        after: Cursor?,
    ): TimelinePage {
        val renderedAt = clock.instant()
        val rows =
            SearchRules
                .matchExpression(text)
                ?.let { search.search(owner, it, after, TimelineRules.PAGE_SIZE + 1) }
                .orEmpty()
        val (shown, next) = TimelineRules.paginate(rows)
        return TimelinePage(shown, next, renderedAt, sources.findAllOf(owner).isNotEmpty())
    }
}
