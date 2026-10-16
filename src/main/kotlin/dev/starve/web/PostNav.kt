package dev.starve.web

import dev.starve.application.ListPosition
import dev.starve.application.TimelineService
import dev.starve.domain.TimelineItem

/**
 * A Previous or Next link in a post's head: the neighbour's reader, or its detail view when it plays (so that
 * opening it never starts playback). [href] keeps the list the post was opened from.
 */
data class PostLink(
    val href: String,
)

/** The Previous (newer) and Next (older) links of a post; each is null at the end of its list. */
data class PostNav(
    val previous: PostLink?,
    val next: PostLink?,
) {
    companion object {
        val NONE = PostNav(null, null)
    }
}

/** [path] with [query] appended as its query string, unless [query] is empty. */
internal fun withQuery(
    path: String,
    query: String,
): String = if (query.isEmpty()) path else "$path?$query"

/** The Previous and Next links of the post at [position] in the list [scope] shows. */
internal fun postNav(
    timeline: TimelineService,
    owner: Long,
    position: ListPosition,
    scope: Scope,
): PostNav {
    val filter = scope.neighbourFilter ?: return PostNav.NONE
    val neighbours = timeline.neighbours(owner, position, filter)
    return PostNav(
        neighbours.previous?.let { postLink(it, scope.listQuery) },
        neighbours.next?.let { postLink(it, scope.listQuery) },
    )
}

private fun postLink(
    item: TimelineItem,
    listQuery: String,
): PostLink {
    val kind = if (item.playable) "detail" else "reader"
    return PostLink(withQuery("/items/${item.id}/$kind", listQuery))
}
