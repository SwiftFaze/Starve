package dev.starve.web

import dev.starve.application.OpenResult
import dev.starve.application.TimelinePage
import dev.starve.application.TimelineService
import dev.starve.domain.Cursor
import dev.starve.domain.TimelineItem
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
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

@Controller
class HomeController(
    private val timeline: TimelineService,
) {
    @GetMapping("/")
    fun home(
        @RequestParam(required = false) show: String?,
        @RequestParam(required = false) after: String?,
        @RequestHeader("HX-Request", required = false) htmx: String?,
        model: Model,
    ): String {
        val showAll = show == "all"
        val page = timeline.page(Cursor.parse(after), showAll)
        model.addAttribute("appName", "Starve")
        model.addAttribute("showAll", showAll)
        model.addAttribute("items", page.items.map { it.toView() })
        model.addAttribute("hasSources", page.hasSources)
        model.addAttribute("renderedAt", page.renderedAt.toEpochMilli())
        model.addAttribute("moreHref", moreHref(page, showAll))
        return if (htmx != null && after != null) "home :: more" else "home"
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
    ): String {
        timeline.markAllRead(Instant.ofEpochMilli(before))
        return "redirect:/"
    }

    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND)

    private fun moreHref(
        page: TimelinePage,
        showAll: Boolean,
    ): String? = page.next?.let { "/?after=${it.encode()}" + if (showAll) "&show=all" else "" }

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
