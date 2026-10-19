package dev.starve.domain

import java.time.Instant

/**
 * One entry of the timeline, with the title of the source it came from. [playable]: its link can play inline.
 * [faviconUrl] is the source's site icon, shown in the thumbnail slot when the item has no [thumbnailUrl].
 * [favourite]: the user starred it; retention never deletes it. [later]: the user saved it for later; retention
 * never deletes it either.
 */
data class TimelineItem(
    val id: Long,
    val sourceTitle: String,
    val title: String,
    val publishedAt: Instant,
    val thumbnailUrl: String?,
    val read: Boolean,
    val playable: Boolean = false,
    val faviconUrl: String? = null,
    val favourite: Boolean = false,
    val later: Boolean = false,
    /** Where its playback last stopped, in seconds; null when it would start from the beginning. */
    val position: Long? = null,
)

/** The position a page continues from; encoded into the "after" parameter of the next page's link. */
interface PageCursor {
    fun encode(): String
}

/** Keyset position in the timeline: the next page holds items strictly older than this. */
data class Cursor(
    val publishedAt: Instant,
    val id: Long,
) : PageCursor {
    override fun encode(): String = "${publishedAt.toEpochMilli()}_$id"

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
