package dev.starve.web

import dev.starve.application.LaterService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.ModelAndView

/**
 * The ⏲ toggles. Both endpoints are idempotent. An htmx request gets the re-rendered toggle; a plain one is
 * redirected to the timeline. An id that is not a number is treated like an unknown one.
 */
@Controller
class LaterController(
    private val later: LaterService,
) {
    @PostMapping("/items/{id}/later")
    fun save(
        @CurrentUserId owner: Long,
        @PathVariable id: String,
        @RequestParam(required = false) from: String?,
        @RequestHeader("HX-Request", required = false) htmx: String?,
    ): ModelAndView = toggle(owner, id, true, Origin(from, htmx))

    @PostMapping("/items/{id}/unlater")
    fun clear(
        @CurrentUserId owner: Long,
        @PathVariable id: String,
        @RequestParam(required = false) from: String?,
        @RequestHeader("HX-Request", required = false) htmx: String?,
    ): ModelAndView = toggle(owner, id, false, Origin(from, htmx))

    /** Where a toggle came from: the pane it was clicked in, and whether htmx sent it. */
    private data class Origin(
        val from: String?,
        val htmx: String?,
    )

    private fun toggle(
        owner: Long,
        raw: String,
        saved: Boolean,
        origin: Origin,
    ): ModelAndView {
        val id = raw.toLongOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val found = if (saved) later.save(owner, id) else later.clear(owner, id)
        if (!found) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val pane = origin.from?.takeIf { it in PANES }
        return when {
            origin.htmx == null -> ModelAndView("redirect:/feed")
            pane != null -> paneAnswer(id, saved, pane)
            else -> ModelAndView("later :: toggle", mapOf("laterToggle" to LaterView("later-$id", id, saved)))
        }
    }

    /** The pane's toggle, plus the timeline row's toggle refreshed out of band. */
    private fun paneAnswer(
        id: Long,
        saved: Boolean,
        pane: String,
    ): ModelAndView =
        ModelAndView(
            "later :: pair",
            mapOf(
                "laterToggle" to LaterView("$pane-later", id, saved, from = pane),
                "rowLater" to LaterView("later-$id", id, saved, oob = true),
            ),
        )

    private companion object {
        val PANES = setOf("reader", "detail")
    }
}
