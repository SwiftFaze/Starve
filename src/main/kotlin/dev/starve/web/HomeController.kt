package dev.starve.web

import dev.starve.application.GroupNavigation
import dev.starve.application.GroupService
import dev.starve.application.OpenResult
import dev.starve.application.SearchService
import dev.starve.application.TimelinePage
import dev.starve.application.TimelineService
import dev.starve.domain.Catalogue
import dev.starve.domain.Cursor
import dev.starve.domain.Group
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
) {
    val catalogueNumber: String get() = Catalogue.number(id)

    val star: StarView get() = StarView("star-$id", id, favourite)
}

/** One link of the group navigation, e.g. label "Music (12)". */
data class NavEntryView(
    val label: String,
    val href: String,
    val current: Boolean,
)

/** The series the timeline is showing: "All", one group with its unread count, or Favourites (no count). */
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
 * the Favourites view, which ignores [show] and [group].
 */
data class TimelineQuery(
    val show: String? = null,
    val after: String? = null,
    val group: String? = null,
    val favourites: String? = null,
    val q: String? = null,
)

/**
 * Which timeline is on screen: search results for [text], the Favourites view, or the timeline with read items
 * and an optional group. Searching ignores the other settings.
 */
private data class Scope(
    val favourites: Boolean,
    val showAll: Boolean,
    val groupId: Long?,
    val text: String,
) {
    val searching get() = text.isNotBlank()
}

@Controller
class HomeController(
    private val timeline: TimelineService,
    private val search: SearchService,
    private val groups: GroupService,
) {
    @GetMapping("/")
    fun home(
        @ModelAttribute query: TimelineQuery,
        @RequestHeader("HX-Request", required = false) htmx: String?,
        model: Model,
    ): String {
        val searching = !query.q.isNullOrBlank()
        val favourites = !searching && query.favourites == "1"
        val scope =
            Scope(
                favourites = favourites,
                showAll = !favourites && query.show == "all",
                groupId = if (favourites || searching) null else query.group?.let { resolveGroup(it).id },
                text = query.q.orEmpty(),
            )
        val cursor = Cursor.parse(query.after)
        addPage(model, pageFor(scope, cursor), scope)
        fragmentFor(htmx, query)?.let { return it }
        addChrome(model, groups.navigation(), scope)
        return "home"
    }

    @GetMapping("/items/{id}/open")
    fun open(
        @PathVariable id: Long,
    ): String =
        when (val result = timeline.open(id)) {
            OpenResult.Missing -> throw notFound()
            is OpenResult.Opened -> "redirect:" + (result.link ?: "/")
        }

    @PostMapping("/items/{id}/read")
    fun markRead(
        @PathVariable id: Long,
        @RequestHeader("HX-Request", required = false) htmx: String?,
    ): Any {
        if (!timeline.markRead(id)) throw notFound()
        return if (htmx != null) ResponseEntity.ok("") else "redirect:/"
    }

    @PostMapping("/read-all")
    fun markAllRead(
        @RequestParam before: Long,
        @RequestParam(required = false) group: Long?,
    ): String {
        timeline.markAllRead(Instant.ofEpochMilli(before), group)
        return "redirect:" + href(group, null, null)
    }

    private fun pageFor(
        scope: Scope,
        cursor: Cursor?,
    ): TimelinePage =
        when {
            scope.searching -> search.search(scope.text, cursor)
            scope.favourites -> timeline.favourites(cursor)
            else -> timeline.page(cursor, scope.showAll, scope.groupId)
        }

    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND)

    private fun resolveGroup(raw: String): Group = raw.toLongOrNull()?.let { groups.find(it) } ?: throw notFound()

    /** The rows and the paging of the page on screen. */
    private fun addPage(
        model: Model,
        page: TimelinePage,
        scope: Scope,
    ) {
        model.addAttribute("appName", "Starve")
        model.addAttribute("showAll", scope.showAll)
        model.addAttribute("favourites", scope.favourites)
        model.addAttribute("searching", scope.searching)
        model.addAttribute("query", scope.text)
        model.addAttribute("items", page.items.map { it.toView() })
        model.addAttribute("emptyFavourites", scope.favourites && page.items.isEmpty())
        model.addAttribute("hasSources", page.hasSources)
        model.addAttribute("renderedAt", page.renderedAt.toEpochMilli())
        model.addAttribute("moreHref", moreHref(page, scope))
    }

    /** The navigation, series and toggles around the page; only a full page needs them. */
    private fun addChrome(
        model: Model,
        nav: GroupNavigation,
        scope: Scope,
    ) {
        model.addAttribute("groupId", scope.groupId)
        model.addAttribute("nav", navigation(nav, scope))
        val favouritesEntry = NavEntryView("Favourites", href(null, null, null, true), scope.favourites)
        model.addAttribute("favouritesEntry", favouritesEntry)
        model.addAttribute("series", seriesOf(nav, scope))
        model.addAttribute("toggleHref", href(scope.groupId, if (scope.showAll) null else "all", null))
    }
}

private fun seriesOf(
    nav: GroupNavigation,
    scope: Scope,
): SeriesView = if (scope.favourites) SeriesView("Favourites", null) else SeriesView.of(nav, scope.groupId)

private fun navigation(
    nav: GroupNavigation,
    scope: Scope,
): List<NavEntryView> {
    val show = if (scope.showAll) "all" else null
    val current = scope.groupId == null && !scope.favourites
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
        if (scope.searching) {
            "/?q=" + URLEncoder.encode(scope.text, StandardCharsets.UTF_8) + "&after=" + it.encode()
        } else {
            href(scope.groupId, if (scope.showAll) "all" else null, it.encode(), scope.favourites)
        }
    }

private fun href(
    groupId: Long?,
    show: String?,
    after: String?,
    favourites: Boolean = false,
): String {
    val query =
        listOfNotNull(
            after?.let { "after=$it" },
            show?.let { "show=$it" },
            groupId?.let { "group=$it" },
            "favourites=1".takeIf { favourites },
        )
    return if (query.isEmpty()) "/" else "/?" + query.joinToString("&")
}

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
    )
