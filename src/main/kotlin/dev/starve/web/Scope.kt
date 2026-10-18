package dev.starve.web

import dev.starve.application.GroupService
import dev.starve.application.ListFilter
import dev.starve.application.Narrowing
import dev.starve.application.narrowing
import dev.starve.domain.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Which timeline is on screen: search results for [text], the Favourites view, the Later view, or the timeline with
 * read items and an optional group or [tag]. Searching ignores the other settings for its results but keeps them, so
 * clearing the search returns to the view it started from. [seen] is the moment the list was rendered (epoch ms),
 * carried on its links so that Previous and Next follow the list as it was shown; null when the query had none.
 */
internal data class Scope(
    val favourites: Boolean,
    val later: Boolean,
    val showAll: Boolean,
    val groupId: Long?,
    val text: String,
    val seen: Long? = null,
    val tag: Tag? = null,
) {
    val searching get() = text.isNotBlank()

    /** The `q=` URL parameter that keeps the search, or null when not searching. */
    val searchParam get() = if (searching) "q=" + URLEncoder.encode(text, StandardCharsets.UTF_8) else null

    /** The `seen=` URL parameter that keeps the render moment, or null when there is none. */
    val seenParam get() = seen?.let { "seen=$it" }

    /** The query parameter that selects this view, or null for the plain timeline and a group. */
    val filter: String?
        get() =
            when {
                favourites -> FAVOURITES_FILTER
                later -> LATER_FILTER
                else -> tag?.let { "tag=${it.id}" }
            }

    /** The items the timeline view covers: the tag's, else the group's, else every item. */
    val narrowing: Narrowing get() = tag?.narrowing ?: Narrowing(groupId)

    /** The query string (without "?") that names this list as `/feed` takes it, and its render moment. */
    val listQuery: String
        get() {
            val show = if (showAll) "show=all" else null
            val group = groupId?.let { "group=$it" }
            return listOfNotNull(searchParam, show, group, filter, seenParam).joinToString("&")
        }

    /**
     * The rows a post in this list has neighbours among, or null when this list has no neighbour query: Later and
     * search order their rows their own way.
     */
    val neighbourFilter: ListFilter?
        get() =
            when {
                searching || later -> null
                favourites -> ListFilter(showRead = true, favourites = true)
                else -> narrowing.let { ListFilter(showAll, it.groupId, seen = seen, match = it.match) }
            }
}

internal const val FAVOURITES_FILTER = "favourites=1"
internal const val LATER_FILTER = "later=1"

/**
 * The list [query] selects. A malformed or unknown group or tag is answered with 404; while searching an unknown one
 * is dropped instead. Favourites wins over Later, and neither takes a group, tag or read state. A tag wins over a
 * group. A [TimelineQuery.seen] that is not a number is ignored.
 */
internal fun scopeOf(
    groups: GroupService,
    owner: Long,
    query: TimelineQuery,
): Scope {
    val favourites = query.favourites == "1"
    val later = !favourites && query.later == "1"
    val plain = !favourites && !later
    val searching = !query.q.isNullOrBlank()
    val tag = query.tag?.takeIf { plain }?.let { found(it, searching) { id -> groups.findTag(owner, id) } }
    val group =
        query.group
            ?.takeIf { plain && query.tag == null }
            ?.let { found(it, searching) { id -> groups.find(owner, id) } }
    return Scope(
        favourites = favourites,
        later = later,
        showAll = plain && query.show == "all",
        groupId = group?.id,
        text = query.q.orEmpty(),
        seen = query.seen?.toLongOrNull(),
        tag = tag,
    )
}

/** The row [raw] names through [find]; an unknown one is null while [searching], and a 404 otherwise. */
private fun <T : Any> found(
    raw: String,
    searching: Boolean,
    find: (Long) -> T?,
): T? {
    val row = raw.toLongOrNull()?.let(find)
    if (row == null && !searching) throw ResponseStatusException(HttpStatus.NOT_FOUND)
    return row
}
