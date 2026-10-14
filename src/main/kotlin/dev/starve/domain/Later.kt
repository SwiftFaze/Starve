package dev.starve.domain

import java.time.Instant

/** A saved item and the instant it was saved. The Later view orders by [savedAt], then by item id. */
data class SavedItem(
    val item: TimelineItem,
    val savedAt: Instant,
)

/**
 * Keyset position in the Later view: the next page holds items saved strictly after this one, in saved-time
 * then id order. The opposite direction to the timeline's [Cursor], which pages newest first.
 */
data class LaterCursor(
    val savedAt: Instant,
    val id: Long,
) : PageCursor {
    override fun encode(): String = "${savedAt.toEpochMilli()}_$id"

    companion object {
        /** Parses [raw] as produced by [encode]; null when absent or malformed. */
        fun parse(raw: String?): LaterCursor? = Cursor.parse(raw)?.let { LaterCursor(it.publishedAt, it.id) }
    }
}

/** Rules for paging the Later view, with the timeline's page size. */
object LaterRules {
    /** Splits the rows fetched with one extra look-ahead row into the page and the cursor of the next page, if any. */
    fun paginate(rows: List<SavedItem>): Pair<List<SavedItem>, LaterCursor?> {
        val shown = rows.take(TimelineRules.PAGE_SIZE)
        val next =
            if (rows.size > TimelineRules.PAGE_SIZE) {
                shown.last().let { LaterCursor(it.savedAt, it.item.id) }
            } else {
                null
            }
        return shown to next
    }
}
