package dev.starve.domain

import java.time.Instant

/** One entry of the timeline, with the title of the source it came from. [playable]: its link can play inline. */
data class TimelineItem(
    val id: Long,
    val sourceTitle: String,
    val title: String,
    val publishedAt: Instant,
    val thumbnailUrl: String?,
    val read: Boolean,
    val playable: Boolean = false,
)

/** Keyset position in the timeline: the next page holds items strictly older than this. */
data class Cursor(
    val publishedAt: Instant,
    val id: Long,
) {
    fun encode(): String = "${publishedAt.toEpochMilli()}_$id"

    companion object {
        /** Parses [raw] as produced by [encode]; null when absent or malformed. */
        fun parse(raw: String?): Cursor? {
            val millis = raw?.substringBefore('_')?.toLongOrNull()
            val id = raw?.substringAfter('_', "")?.toLongOrNull()
            return if (millis == null || id == null) null else Cursor(Instant.ofEpochMilli(millis), id)
        }
    }
}

/** Rules for paging the timeline. */
object TimelineRules {
    const val PAGE_SIZE = 50

    /** Splits the rows fetched with one extra look-ahead row into the page and the cursor of the next page, if any. */
    fun paginate(rows: List<TimelineItem>): Pair<List<TimelineItem>, Cursor?> {
        val shown = rows.take(PAGE_SIZE)
        val next = if (rows.size > PAGE_SIZE) shown.last().let { Cursor(it.publishedAt, it.id) } else null
        return shown to next
    }
}
