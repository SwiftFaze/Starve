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
 * The favourite star toggles. Both endpoints are idempotent. An htmx request gets every star of the item back: the
 * one clicked, then the others out of band. A plain one is redirected to the timeline. An id that is not a number is
 * treated like an unknown one.
 */
@Controller
class FavouriteController(
    private val favourites: FavouriteService,
) {
    @PostMapping("/items/{id}/favourite")
    fun favourite(
        @CurrentUserId owner: Long,
        @PathVariable id: String,
        @RequestParam(required = false) from: String?,
        @RequestHeader("HX-Request", required = false) htmx: String?,
    ): ModelAndView = toggle(owner, id, true, Origin(from, htmx))

    @PostMapping("/items/{id}/unfavourite")
    fun unfavourite(
        @CurrentUserId owner: Long,
        @PathVariable id: String,
        @RequestParam(required = false) from: String?,
        @RequestHeader("HX-Request", required = false) htmx: String?,
    ): ModelAndView = toggle(owner, id, false, Origin(from, htmx))

    /** Where a toggle came from: the place it was clicked in, and whether htmx sent it. */
    private data class Origin(
        val from: String?,
        val htmx: String?,
    )

    private fun toggle(
        owner: Long,
        raw: String,
        favourite: Boolean,
        origin: Origin,
    ): ModelAndView {
        val id = raw.toLongOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val found = if (favourite) favourites.favourite(owner, id) else favourites.unfavourite(owner, id)
        if (!found) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (origin.htmx == null) return ModelAndView("redirect:/feed")
        return answer(id, favourite, StarPlace.from(origin.from))
    }

    /** The clicked place's star first, then every other place's star out of band. */
    private fun answer(
        id: Long,
        favourite: Boolean,
        clicked: StarPlace,
    ): ModelAndView {
        val others = StarPlace.entries.filter { it != clicked }.map { it.view(id, favourite, oob = true) }
        return ModelAndView("star :: set", mapOf("stars" to listOf(clicked.view(id, favourite)) + others))
    }
}
