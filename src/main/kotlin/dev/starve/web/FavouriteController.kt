package dev.starve.web

import dev.starve.application.FavouriteService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.ModelAndView

/**
 * The favourite star toggles. Both endpoints are idempotent. An htmx request gets the re-rendered star; a plain
 * one is redirected to the timeline. An id that is not a number is treated like an unknown one.
 */
@Controller
class FavouriteController(
    private val favourites: FavouriteService,
) {
    @PostMapping("/items/{id}/favourite")
    fun favourite(
        @PathVariable id: String,
        @RequestParam(required = false) from: String?,
        @RequestHeader("HX-Request", required = false) htmx: String?,
    ): ModelAndView = toggle(id, true, from, htmx)

    @PostMapping("/items/{id}/unfavourite")
    fun unfavourite(
        @PathVariable id: String,
        @RequestParam(required = false) from: String?,
        @RequestHeader("HX-Request", required = false) htmx: String?,
    ): ModelAndView = toggle(id, false, from, htmx)

    private fun toggle(
        raw: String,
        favourite: Boolean,
        from: String?,
        htmx: String?,
    ): ModelAndView {
        val id = raw.toLongOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val found = if (favourite) favourites.favourite(id) else favourites.unfavourite(id)
        if (!found) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return when {
            htmx == null -> ModelAndView("redirect:/feed")
            from == FROM_READER -> readerAnswer(id, favourite)
            else -> ModelAndView("star :: star", mapOf("star" to StarView("star-$id", id, favourite)))
        }
    }

    /** The reader's star, plus the timeline row's star refreshed out of band. */
    private fun readerAnswer(
        id: Long,
        favourite: Boolean,
    ): ModelAndView =
        ModelAndView(
            "star :: pair",
            mapOf(
                "star" to StarView("reader-star", id, favourite, fromReader = true),
                "rowStar" to StarView("star-$id", id, favourite, oob = true),
            ),
        )

    private companion object {
        const val FROM_READER = "reader"
    }
}
