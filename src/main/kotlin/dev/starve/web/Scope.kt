package dev.starve.web

import dev.starve.application.GroupService
import dev.starve.application.ListFilter
import dev.starve.domain.Group
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Which timeline is on screen: search results for [text], the Favourites view, the Later view, or the timeline with
 * read items and an optional group. Searching ignores the other settings for its results but keeps them, so
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
) {
    val searching get() = text.isNotBlank()

    /** The `q=` URL parameter that keeps the search, or null when not searching. */
    val searchParam get() = if (searching) "q=" + URLEncoder.encode(text, StandardCharsets.UTF_8) else null

    /** The `seen=` URL parameter that keeps the render moment, or null when there is none. */
    val seenParam get() = seen?.let { "seen=$it" }

    /** The query parameter that selects this view, or null for the plain timeline. */
    val filter: String?
        get() =
            when {
                favourites -> FAVOURITES_FILTER
                later -> LATER_FILTER
                else -> null
            }

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
                else -> ListFilter(showRead = showAll, groupId = groupId, seen = seen)
            }
}

internal const val FAVOURITES_FILTER = "favourites=1"
internal const val LATER_FILTER = "later=1"

/**
 * The list [query] selects. A malformed or unknown group is answered with 404; while searching an unknown one is
 * dropped instead. Favourites wins over Later, and neither takes a group or read state. A [TimelineQuery.seen]
 * that is not a number is ignored.
 */
internal fun scopeOf(
    groups: GroupService,
    owner: Long,
    query: TimelineQuery,
): Scope {
    val favourites = query.favourites == "1"
    val later = !favourites && query.later == "1"
    val plain = !favourites && !later
    return Scope(
        favourites = favourites,
        later = later,
        showAll = plain && query.show == "all",
        groupId = if (plain) query.group?.let { groupIdFor(groups, owner, it, !query.q.isNullOrBlank()) } else null,
        text = query.q.orEmpty(),
        seen = query.seen?.toLongOrNull(),
    )
}

private fun groupIdFor(
    groups: GroupService,
    owner: Long,
    raw: String,
    searching: Boolean,
): Long? =
    if (searching) {
        raw.toLongOrNull()?.let { groups.find(owner, it) }?.id
    } else {
        resolveGroup(groups, owner, raw).id
    }

private fun resolveGroup(
    groups: GroupService,
    owner: Long,
    raw: String,
): Group = raw.toLongOrNull()?.let { groups.find(owner, it) } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
