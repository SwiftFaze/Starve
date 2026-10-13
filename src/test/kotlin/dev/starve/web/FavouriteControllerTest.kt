package dev.starve.web

import dev.starve.application.FavouriteService
import dev.starve.application.TimelineItems
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class FavouriteControllerTest {
    private val now = Instant.parse("2026-10-11T12:00:00Z")

    private fun controller(items: TimelineItems): FavouriteController {
        val service = FavouriteService(items, Clock.fixed(now, ZoneOffset.UTC))
        return FavouriteController(service)
    }

    @Test
    fun `favouriting answers htmx with the filled star`() {
        val view = controller(TimelineItems(links = mapOf(5L to null))).favourite("5", null, "true")

        assertEquals("star :: star", view.viewName)
        assertEquals(StarView("star-5", 5, true), view.model["star"])
    }

    @Test
    fun `unfavouriting answers htmx with the empty star`() {
        val view = controller(TimelineItems(links = mapOf(5L to null))).unfavourite("5", null, "true")

        assertEquals("star :: star", view.viewName)
        assertEquals(StarView("star-5", 5, false), view.model["star"])
    }

    @Test
    fun `a toggle from the reader also answers with the timeline row star out of band`() {
        val view = controller(TimelineItems(links = mapOf(5L to null))).favourite("5", "reader", "true")

        assertEquals("star :: pair", view.viewName)
        assertEquals(StarView("reader-star", 5, true, fromReader = true), view.model["star"])
        assertEquals(StarView("star-5", 5, true, oob = true), view.model["rowStar"])
    }

    @Test
    fun `a plain request redirects to the timeline`() {
        val c = controller(TimelineItems(links = mapOf(5L to null)))

        assertEquals("redirect:/", c.favourite("5", null, null).viewName)
        assertEquals("redirect:/", c.unfavourite("5", null, null).viewName)
    }

    @Test
    fun `an unknown or non-numeric item is not found for either toggle`() {
        val c = controller(TimelineItems(links = mapOf(5L to null)))

        listOf("9999", "abc").forEach { id ->
            val favourite = assertThrows(ResponseStatusException::class.java) { c.favourite(id, null, "true") }
            val unfavourite = assertThrows(ResponseStatusException::class.java) { c.unfavourite(id, null, "true") }
            assertEquals(HttpStatus.NOT_FOUND, favourite.statusCode)
            assertEquals(HttpStatus.NOT_FOUND, unfavourite.statusCode)
        }
    }
}
