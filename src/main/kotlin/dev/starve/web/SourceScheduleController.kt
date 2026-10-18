package dev.starve.web

import dev.starve.application.SourceScheduleService
import dev.starve.domain.PollInterval
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException

/** A source's poll interval and its Fetch now button; another user's source is answered like an unknown one: 404. */
@Controller
class SourceScheduleController(
    private val schedule: SourceScheduleService,
    private val roster: SourceRoster,
) {
    /** An empty or absent [interval] puts the source back on the default; an unknown one is a 400. */
    @PostMapping("/sources/{id}/interval")
    fun setInterval(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
        @RequestParam(required = false) interval: String?,
    ): String {
        val named = interval?.takeIf { it.isNotBlank() }
        val chosen = named?.let { PollInterval.named(it) ?: throw status(HttpStatus.BAD_REQUEST) }
        if (!schedule.setInterval(owner, id, chosen)) throw status(HttpStatus.NOT_FOUND)
        return "redirect:/sources"
    }

    /** Starts the fetch and answers at once: htmx gets the row in its fetching state, a plain form the roster. */
    @PostMapping("/sources/{id}/fetch")
    fun fetchNow(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
        @RequestHeader("HX-Request", required = false) htmx: String?,
        model: Model,
    ): String {
        if (!schedule.fetchNow(owner, id)) throw status(HttpStatus.NOT_FOUND)
        return if (htmx != null) row(owner, id, model) else "redirect:/sources"
    }

    /** One row of the roster, which a row that is fetching asks for again until the fetch is over. */
    @GetMapping("/sources/{id}/row")
    fun row(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
        model: Model,
    ): String {
        if (!roster.addTo(model, owner, id)) throw status(HttpStatus.NOT_FOUND)
        return "sources :: source-row"
    }

    private fun status(code: HttpStatus) = ResponseStatusException(code)
}
