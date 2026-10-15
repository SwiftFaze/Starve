package dev.starve.web

import dev.starve.application.GroupNavigation
import dev.starve.application.GroupService
import dev.starve.application.LaterService
import dev.starve.application.OpenResult
import dev.starve.application.SearchService
import dev.starve.application.TimelinePage
import dev.starve.application.TimelineService
import dev.starve.domain.Catalogue
import dev.starve.domain.Cursor
import dev.starve.domain.Group
import dev.starve.domain.LaterCursor
import dev.starve.domain.TimelineItem
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * The htmx fragment answering [query], or null for a full page. Load more returns the next rows; a request
 * carrying the search box's `q` (even blank, when cleared) returns the list pane only.
 */
private fun fragmentFor(
    htmx: String?,
    query: TimelineQuery,
): String? =
    when {
        htmx == null -> null
        query.after != null -> "home :: more"
        query.q != null -> "home :: sheet"
        else -> null
    }

data class TimelineItemView(
    val id: Long,
    val sourceTitle: String,
    val title: String,
    val published: String,
    val publishedIso: String,
    val thumbnailUrl: String?,
    val read: Boolean,
    val playable: Boolean = false,
    val faviconUrl: String? = null,
    val favourite: Boolean = false,
    val later: Boolean = false,
) {
    val catalogueNumber: String get() = Catalogue.number(id)

    val star: StarView get() = StarView("star-$id", id, favourite)

    val laterToggle: LaterView get() = LaterView("later-$id", id, later)
}

/** One link of the group navigation, e.g. label "Music (12)". */
data class NavEntryView(
    val label: String,
    val href: String,
    val current: Boolean,
)

/**
 * The series the timeline is showing: "All", one group with its unread count, or Favourites or Later (no count).
 */
data class SeriesView(
    val name: String,
    val unread: Int?,
) {
    companion object {
        /** The group [current] from [nav], or "All" when no group is selected. */
        fun of(
            nav: GroupNavigation,
            current: Long?,
        ): SeriesView =
            nav.groups
                .firstOrNull { it.group.id == current }
                ?.let { SeriesView(it.group.name, it.unread) }
                ?: SeriesView("All", nav.totalUnread)
    }
}

/**
 * The timeline's query parameters; a malformed or unknown [group] is answered with 404. [favourites] "1" selects
 * the Favourites view and [later] "1" the Later view; both ignore [show] and [group], and Favourites wins when
 * both are given.
 */
data class TimelineQuery(
    val show: String? = null,
    val after: String? = null,
    val group: String? = null,
    val favourites: String? = null,
    val later: String? = null,
    val q: String? = null,
)

/**
 * Which timeline is on screen: search results for [text], the Favourites view, the Later view, or the timeline with
 * read items and an optional group. Searching ignores the other settings for its results but keeps them, so
 * clearing the search returns to the view it started from.
 */
private data class Scope(
    val favourites: Boolean,
    val later: Boolean,
    val showAll: Boolean,
    val groupId: Long?,
    val text: String,
) {
    val searching get() = text.isNotBlank()

    /** The `q=` URL parameter that keeps the search, or null when not searching. */
    val searchParam get() = if (searching) "q=" + URLEncoder.encode(text, StandardCharsets.UTF_8) else null

    /** The query parameter that selects this view, or null for the plain timeline. */
    val filter: String?
        get() =
            when {
                favourites -> FAVOURITES_FILTER
                later -> LATER_FILTER
                else -> null
            }
}

private const val FAVOURITES_FILTER = "favourites=1"
private const val LATER_FILTER = "later=1"

@Controller
class HomeController(
    private val timeline: TimelineService,
    private val search: SearchService,
    private val laterService: LaterService,
    private val groups: GroupService,
) {
    @GetMapping("/feed")
    fun home(
        @CurrentUserId owner: Long,
        @ModelAttribute query: TimelineQuery,
        @RequestHeader("HX-Request", required = false) htmx: String?,
        model: Model,
    ): String {
        val scope = scopeOf(owner, query)
        addPage(model, pageFor(owner, scope, query.after), scope)
        fragmentFor(htmx, query)?.let { return it }
        addChrome(model, groups.navigation(owner), scope)
        return "home"
    }

    @GetMapping("/items/{id}/open")
    fun open(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
    ): String =
        when (val result = timeline.open(owner, id)) {
            OpenResult.Missing -> throw notFound()
            is OpenResult.Opened -> "redirect:" + (result.link ?: "/feed")
        }

    @PostMapping("/items/{id}/read")
    fun markRead(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
        @RequestHeader("HX-Request", required = false) htmx: String?,
    ): Any {
        if (!timeline.markRead(owner, id)) throw notFound()
        return if (htmx != null) ResponseEntity.ok("") else "redirect:/feed"
    }

    @PostMapping("/read-all")
    fun markAllRead(
        @CurrentUserId owner: Long,
        @RequestParam before: Long,
        @RequestParam(required = false) group: Long?,
    ): String {
        timeline.markAllRead(owner, Instant.ofEpochMilli(before), group)
        return "redirect:" + href(group, null, null)
    }

    private fun pageFor(
        owner: Long,
        scope: Scope,
        after: String?,
    ): TimelinePage =
        when {
            scope.searching -> search.search(owner, scope.text, Cursor.parse(after))
            scope.favourites -> timeline.favourites(owner, Cursor.parse(after))
            scope.later -> laterService.page(owner, LaterCursor.parse(after))
            else -> timeline.page(owner, Cursor.parse(after), scope.showAll, scope.groupId)
        }

    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND)

    /** The group of [raw]; while [searching] an unknown one is dropped instead of answered with 404. */
    private fun groupIdFor(
        owner: Long,
        raw: String,
        searching: Boolean,
    ): Long? = if (searching) raw.toLongOrNull()?.let { groups.find(owner, it) }?.id else resolveGroup(owner, raw).id

    private fun resolveGroup(
        owner: Long,
        raw: String,
    ): Group = raw.toLongOrNull()?.let { groups.find(owner, it) } ?: throw notFound()

    /** Which view the query selects: Favourites wins over Later, and neither takes a group or read state. */
    private fun scopeOf(
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
            groupId = if (plain) query.group?.let { groupIdFor(owner, it, !query.q.isNullOrBlank()) } else null,
            text = query.q.orEmpty(),
        )
    }

    /** The navigation, series and toggles around the page; only a full page needs them. */
    private fun addChrome(
        model: Model,
        nav: GroupNavigation,
        scope: Scope,
    ) {
        model.addAttribute("groupId", scope.groupId)
        model.addAttribute("nav", navigation(nav, scope))
        val favouritesEntry = NavEntryView("Favourites", href(null, null, null, FAVOURITES_FILTER), scope.favourites)
        model.addAttribute("favouritesEntry", favouritesEntry)
        val laterEntry = NavEntryView("Later", href(null, null, null, LATER_FILTER), scope.later)
        model.addAttribute("laterEntry", laterEntry)
        model.addAttribute("series", seriesOf(nav, scope))
        model.addAttribute("toggleHref", href(scope.groupId, if (scope.showAll) null else "all", null))
    }
}

/** The rows and the paging of the page on screen. */
private fun addPage(
    model: Model,
    page: TimelinePage,
    scope: Scope,
) {
    model.addAttribute("appName", "Starve")
    model.addAttribute("showAll", scope.showAll)
    model.addAttribute("favourites", scope.favourites)
    model.addAttribute("later", scope.later)
    model.addAttribute("searching", scope.searching)
    model.addAttribute("query", scope.text)
    model.addAttribute("items", page.items.map { it.toView() })
    model.addAttribute("emptyFavourites", scope.favourites && !scope.searching && page.items.isEmpty())
    model.addAttribute("emptyLater", scope.later && !scope.searching && page.items.isEmpty())
    model.addAttribute("hasSources", page.hasSources)
    model.addAttribute("renderedAt", page.renderedAt.toEpochMilli())
    model.addAttribute("moreHref", moreHref(page, scope))
}

private fun seriesOf(
    nav: GroupNavigation,
    scope: Scope,
): SeriesView =
    when {
        scope.favourites -> SeriesView("Favourites", null)
        scope.later -> SeriesView("Later", null)
        else -> SeriesView.of(nav, scope.groupId)
    }

private fun navigation(
    nav: GroupNavigation,
    scope: Scope,
): List<NavEntryView> {
    val show = if (scope.showAll) "all" else null
    val current = scope.groupId == null && scope.filter == null
    val all = NavEntryView("All (${nav.totalUnread})", href(null, show, null), current)
    return listOf(all) +
        nav.groups.map {
            NavEntryView(
                "${it.group.name} (${it.unread})",
                href(it.group.id, show, null),
                it.group.id == scope.groupId,
            )
        }
}

private fun moreHref(
    page: TimelinePage,
    scope: Scope,
): String? =
    page.next?.let {
        val show = if (scope.showAll) "all" else null
        link(listOfNotNull(scope.searchParam) + parts(scope.groupId, show, it.encode(), scope.filter))
    }

private fun href(
    groupId: Long?,
    show: String?,
    after: String?,
    filter: String? = null,
): String = link(parts(groupId, show, after, filter))

private fun parts(
    groupId: Long?,
    show: String?,
    after: String?,
    filter: String?,
): List<String> =
    listOfNotNull(
        after?.let { "after=$it" },
        show?.let { "show=$it" },
        groupId?.let { "group=$it" },
        filter,
    )

private fun link(query: List<String>): String = if (query.isEmpty()) "/feed" else "/feed?" + query.joinToString("&")

private fun TimelineItem.toView() =
    TimelineItemView(
        id,
        sourceTitle,
        title,
        DISPLAY_FORMAT.format(publishedAt),
        publishedAt.toString(),
        thumbnailUrl,
        read,
        playable,
        faviconUrl,
        favourite,
        later,
    )
