package dev.starve.web

import dev.starve.application.GroupNavigation
import dev.starve.application.GroupService
import dev.starve.application.LaterService
import dev.starve.application.Narrowing
import dev.starve.application.OpenResult
import dev.starve.application.SearchService
import dev.starve.application.TimelinePage
import dev.starve.application.TimelineService
import dev.starve.application.narrowing
import dev.starve.domain.Catalogue
import dev.starve.domain.Cursor
import dev.starve.domain.LaterCursor
import dev.starve.domain.TimelineItem
import dev.starve.domain.TrackingParams
import org.springframework.beans.factory.annotation.Value
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
import java.time.Clock
import java.time.Duration
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
    /** The query string of the list the row is in, which its reader link keeps (empty for the plain timeline). */
    val listQuery: String = "",
    /** Where its playback last stopped, as the deck's clock shows it ("41:12"); null to start from the beginning. */
    val resume: String? = null,
) {
    /** The Play control's words: "Resume 41:12" when it stopped part way, else "Play". */
    val playLabel: String get() = resume?.let { "Resume $it" } ?: "Play"

    val catalogueNumber: String get() = Catalogue.number(id)

    val star: StarView get() = StarView("star-$id", id, favourite)

    val laterToggle: LaterView get() = LaterView("later-$id", id, later)

    val readerHref: String get() = withQuery("/items/$id/reader", listQuery)
}

/** One link of the group navigation, e.g. label "Music [12]". */
data class NavEntryView(
    val label: String,
    val href: String,
    val current: Boolean,
)

/**
 * The series the timeline is showing: "All", one group or tag ("#AI") with its unread count, or Favourites or Later
 * (no count).
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
 * The timeline's query parameters; a malformed or unknown [group] or [tag] is answered with 404, and a tag wins over
 * a group. [favourites] "1" selects the Favourites view and [later] "1" the Later view; both ignore [show], [group]
 * and [tag], and Favourites wins when both are given.
 */
data class TimelineQuery(
    val show: String? = null,
    val after: String? = null,
    val group: String? = null,
    val favourites: String? = null,
    val later: String? = null,
    val q: String? = null,
    val seen: String? = null,
    val tag: String? = null,
)

@Controller
class HomeController(
    private val timeline: TimelineService,
    private val search: SearchService,
    private val laterService: LaterService,
    private val groups: GroupService,
    private val clock: Clock,
    @Value("\${starve.poll.interval:PT30M}") private val pollInterval: Duration = Duration.parse("PT30M"),
) {
    @GetMapping("/feed")
    fun home(
        @CurrentUserId owner: Long,
        @ModelAttribute query: TimelineQuery,
        @RequestHeader("HX-Request", required = false) htmx: String?,
        model: Model,
    ): String {
        // A list rendered now is stamped now; a page of a list already on screen keeps that list's stamp.
        val scope = scopeOf(groups, owner, query).let { it.copy(seen = it.seen ?: clock.millis()) }
        val nav = groups.navigation(owner)
        addPage(model, pageFor(owner, scope, query.after), scope)
        model.addAttribute("end", listEnd(nav, scope))
        fragmentFor(htmx, query)?.let { return it }
        addChrome(model, nav, scope)
        return "home"
    }

    /** How the list of the view [query] selects ends, which its end marker fetches again after an item is read. */
    @GetMapping("/feed/end")
    fun end(
        @CurrentUserId owner: Long,
        @ModelAttribute query: TimelineQuery,
        model: Model,
    ): String {
        model.addAttribute("end", listEnd(groups.navigation(owner), scopeOf(groups, owner, query)))
        return "home :: end"
    }

    @GetMapping("/items/{id}/open")
    fun open(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
    ): String =
        when (val result = timeline.open(owner, id)) {
            OpenResult.Missing -> throw notFound()
            is OpenResult.Opened -> "redirect:" + (result.link?.let(TrackingParams::strip) ?: "/feed")
        }

    @PostMapping("/items/{id}/read")
    fun markRead(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
        @RequestHeader("HX-Request", required = false) htmx: String?,
    ): Any {
        if (!timeline.markRead(owner, id)) throw notFound()
        return if (htmx != null) ResponseEntity.ok().header(HX_TRIGGER, READ_EVENT).body("") else "redirect:/feed"
    }

    /** The unread counts of the view [query] selects, which the timeline swaps in out of band after a read. */
    @GetMapping("/feed/counts")
    fun counts(
        @CurrentUserId owner: Long,
        @ModelAttribute query: TimelineQuery,
        model: Model,
    ): String {
        addChrome(model, groups.navigation(owner), scopeOf(groups, owner, query))
        return "counts :: counts"
    }

    /** Marks read the view's items shown at [before]: a [tag]'s (which wins), a [group]'s, or every item. */
    @PostMapping("/read-all")
    fun markAllRead(
        @CurrentUserId owner: Long,
        @RequestParam before: Long,
        @RequestParam(required = false) group: Long?,
        @RequestParam(required = false) tag: Long?,
    ): String {
        val tagged = tag?.let { groups.findTag(owner, it) ?: throw notFound() }
        val within = tagged?.narrowing ?: Narrowing(group)
        timeline.markAllRead(owner, Instant.ofEpochMilli(before), within)
        return "redirect:" + if (tagged != null) href(null, null, null, "tag=${tagged.id}") else href(group, null, null)
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
            else -> timeline.page(owner, Cursor.parse(after), scope.showAll, scope.narrowing)
        }

    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND)

    private fun listEnd(
        nav: GroupNavigation,
        scope: Scope,
    ) = ListEnd(seriesOf(nav, scope).unread, everyPhrase(pollInterval), viewHref("/feed/end", scope))

    /** The navigation, series and toggles around the page; only a full page needs them. */
    private fun addChrome(
        model: Model,
        nav: GroupNavigation,
        scope: Scope,
    ) {
        model.addAttribute("groupId", scope.groupId)
        model.addAttribute("tagId", scope.tag?.id)
        model.addAttribute("nav", navigation(nav, scope))
        val favouritesEntry = NavEntryView("Favourites", href(null, null, null, FAVOURITES_FILTER), scope.favourites)
        model.addAttribute("favouritesEntry", favouritesEntry)
        val laterEntry = NavEntryView("Later", href(null, null, null, LATER_FILTER), scope.later)
        model.addAttribute("laterEntry", laterEntry)
        model.addAttribute("series", seriesOf(nav, scope))
        model.addAttribute("toggleHref", href(scope.groupId, if (scope.showAll) null else "all", null, scope.filter))
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
    model.addAttribute("items", page.items.map { it.toView(scope.listQuery) })
    model.addAttribute("emptyFavourites", scope.favourites && !scope.searching && page.items.isEmpty())
    model.addAttribute("emptyLater", scope.later && !scope.searching && page.items.isEmpty())
    model.addAttribute("hasSources", page.hasSources)
    model.addAttribute("brokenSources", page.brokenSources)
    model.addAttribute("renderedAt", page.renderedAt.toEpochMilli())
    model.addAttribute("moreHref", moreHref(page, scope))
    model.addAttribute("countsHref", viewHref("/feed/counts", scope))
}

private fun seriesOf(
    nav: GroupNavigation,
    scope: Scope,
): SeriesView =
    when {
        scope.favourites -> {
            SeriesView("Favourites", null)
        }

        scope.later -> {
            SeriesView("Later", null)
        }

        scope.tag != null -> {
            SeriesView("#${scope.tag.name}", nav.tags.firstOrNull { it.tag.id == scope.tag.id }?.unread ?: 0)
        }

        else -> {
            SeriesView.of(nav, scope.groupId)
        }
    }

private fun navigation(
    nav: GroupNavigation,
    scope: Scope,
): List<NavEntryView> {
    val show = if (scope.showAll) "all" else null
    val current = scope.groupId == null && scope.filter == null
    val all = NavEntryView("All [${nav.totalUnread}]", href(null, show, null), current)
    val groups =
        nav.groups.map {
            NavEntryView("${it.group.name} [${it.unread}]", href(it.group.id, show, null), it.group.id == scope.groupId)
        }
    val tags =
        nav.tags.map {
            val tagHref = href(null, show, null, "tag=${it.tag.id}")
            NavEntryView("#${it.tag.name} [${it.unread}]", tagHref, it.tag.id == scope.tag?.id)
        }
    return listOf(all) + groups + tags
}

private fun moreHref(
    page: TimelinePage,
    scope: Scope,
): String? =
    page.next?.let {
        val show = if (scope.showAll) "all" else null
        val rest = parts(scope.groupId, show, it.encode(), scope.filter)
        link(listOfNotNull(scope.searchParam) + rest + listOfNotNull(scope.seenParam))
    }

/** [path] for the view [scope] shows: its group, filter and read state, without its search, paging or render moment. */
private fun viewHref(
    path: String,
    scope: Scope,
): String {
    val query = parts(scope.groupId, if (scope.showAll) "all" else null, null, scope.filter)
    return path + if (query.isEmpty()) "" else "?" + query.joinToString("&")
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

private fun TimelineItem.toView(listQuery: String) =
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
        listQuery,
        position?.let(::playTime),
    )
