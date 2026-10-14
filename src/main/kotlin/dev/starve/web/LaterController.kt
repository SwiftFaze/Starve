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
        @PathVariable id: String,
        @RequestParam(required = false) from: String?,
        @RequestHeader("HX-Request", required = false) htmx: String?,
    ): ModelAndView = toggle(id, true, from, htmx)

    @PostMapping("/items/{id}/unlater")
    fun clear(
        @PathVariable id: String,
        @RequestParam(required = false) from: String?,
        @RequestHeader("HX-Request", required = false) htmx: String?,
    ): ModelAndView = toggle(id, false, from, htmx)

    private fun toggle(
        raw: String,
        saved: Boolean,
        from: String?,
        htmx: String?,
    ): ModelAndView {
        val id = raw.toLongOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val found = if (saved) later.save(id) else later.clear(id)
        if (!found) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val pane = from?.takeIf { it in PANES }
        return when {
            htmx == null -> ModelAndView("redirect:/")
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
