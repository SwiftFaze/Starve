package dev.starve.web

import dev.starve.application.GroupService
import dev.starve.application.OpenResult
import dev.starve.application.TimelinePage
import dev.starve.application.TimelineService
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
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class TimelineItemView(
    val id: Long,
    val sourceTitle: String,
    val title: String,
    val published: String,
    val publishedIso: String,
    val thumbnailUrl: String?,
    val read: Boolean,
)

/** One link of the group navigation, e.g. label "Music (12)". */
data class NavEntryView(
    val label: String,
    val href: String,
    val current: Boolean,
)

/** The timeline's query parameters; a malformed or unknown [group] is answered with 404. */
data class TimelineQuery(
    val show: String? = null,
    val after: String? = null,
    val group: String? = null,
)

@Controller
class HomeController(
    private val timeline: TimelineService,
    private val groups: GroupService,
) {
    @GetMapping("/")
    fun home(
        @ModelAttribute query: TimelineQuery,
        @RequestHeader("HX-Request", required = false) htmx: String?,
        model: Model,
    ): String {
        val showAll = query.show == "all"
        val groupId = query.group?.let { resolveGroup(it).id }
        val page = timeline.page(Cursor.parse(query.after), showAll, groupId)
        model.addAttribute("appName", "Starve")
        model.addAttribute("showAll", showAll)
        model.addAttribute("items", page.items.map { it.toView() })
        model.addAttribute("hasSources", page.hasSources)
        model.addAttribute("renderedAt", page.renderedAt.toEpochMilli())
        model.addAttribute("moreHref", moreHref(page, showAll, groupId))
        if (htmx != null && query.after != null) return "home :: more"
        model.addAttribute("groupId", groupId)
        model.addAttribute("nav", navigation(groupId, showAll))
        model.addAttribute("toggleHref", href(groupId, if (showAll) null else "all", null))
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

    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND)

    private fun resolveGroup(raw: String): Group = raw.toLongOrNull()?.let { groups.find(it) } ?: throw notFound()

    private fun navigation(
        current: Long?,
        showAll: Boolean,
    ): List<NavEntryView> {
        val nav = groups.navigation()
        val show = if (showAll) "all" else null
        val all = NavEntryView("All (${nav.totalUnread})", href(null, show, null), current == null)
        return listOf(all) +
            nav.groups.map {
                NavEntryView("${it.group.name} (${it.unread})", href(it.group.id, show, null), it.group.id == current)
            }
    }

    private fun moreHref(
        page: TimelinePage,
        showAll: Boolean,
        groupId: Long?,
    ): String? = page.next?.let { href(groupId, if (showAll) "all" else null, it.encode()) }

    private fun href(
        groupId: Long?,
        show: String?,
        after: String?,
    ): String {
        val query =
            listOfNotNull(
                after?.let { "after=$it" },
                show?.let { "show=$it" },
                groupId?.let { "group=$it" },
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
        )

    private companion object {
        val DISPLAY_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)
    }
}
