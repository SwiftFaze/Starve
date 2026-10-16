package dev.starve.web

import dev.starve.application.FavouriteService
import dev.starve.application.OWNER
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
    fun `favouriting from the timeline row answers the row star filled, then the other three out of band`() {
        val items = TimelineItems(links = mapOf(5L to null))

        val view = controller(items).favourite(OWNER, "5", null, "true")

        assertEquals("star :: set", view.viewName)
        assertEquals(listOf(5L to now), items.favouriteCalls)
        assertEquals(
            listOf(
                StarView("star-5", 5, true),
                StarView("deck-star-5", 5, true, from = "deck", oob = true),
                StarView("reader-star-5", 5, true, from = "reader", oob = true),
                StarView("detail-star-5", 5, true, from = "detail", oob = true),
            ),
            view.model["stars"],
        )
    }

    @Test
    fun `unfavouriting answers the row star empty`() {
        val items = TimelineItems(links = mapOf(5L to null))

        val view = controller(items).unfavourite(OWNER, "5", null, "true")

        assertEquals("star :: set", view.viewName)
        assertEquals(listOf<Pair<Long, Instant?>>(5L to null), items.favouriteCalls)
        assertEquals(StarView("star-5", 5, false), (view.model["stars"] as List<*>).first())
    }

    @Test
    fun `a toggle from the reader answers the reader star first and the others out of band`() {
        val view = controller(TimelineItems(links = mapOf(5L to null))).favourite(OWNER, "5", "reader", "true")

        assertEquals("star :: set", view.viewName)
        assertEquals(
            listOf(
                StarView("reader-star-5", 5, true, from = "reader"),
                StarView("star-5", 5, true, oob = true),
                StarView("deck-star-5", 5, true, from = "deck", oob = true),
                StarView("detail-star-5", 5, true, from = "detail", oob = true),
            ),
            view.model["stars"],
        )
    }

    @Test
    fun `a toggle from the deck answers the deck star first and every other star out of band`() {
        val view = controller(TimelineItems(links = mapOf(5L to null))).unfavourite(OWNER, "5", "deck", "true")

        assertEquals(
            listOf(
                StarView("deck-star-5", 5, false, from = "deck"),
                StarView("star-5", 5, false, oob = true),
                StarView("reader-star-5", 5, false, from = "reader", oob = true),
                StarView("detail-star-5", 5, false, from = "detail", oob = true),
            ),
            view.model["stars"],
        )
    }

    @Test
    fun `a toggle from the detail view answers the detail star first`() {
        val view = controller(TimelineItems(links = mapOf(5L to null))).favourite(OWNER, "5", "detail", "true")

        assertEquals(StarView("detail-star-5", 5, true, from = "detail"), (view.model["stars"] as List<*>).first())
    }

    @Test
    fun `a plain request redirects to the timeline`() {
        val c = controller(TimelineItems(links = mapOf(5L to null)))

        assertEquals("redirect:/feed", c.favourite(OWNER, "5", null, null).viewName)
        assertEquals("redirect:/feed", c.unfavourite(OWNER, "5", null, null).viewName)
    }

    @Test
    fun `an unknown or non-numeric item is not found for either toggle`() {
        val c = controller(TimelineItems(links = mapOf(5L to null)))

        listOf("9999", "abc").forEach { id ->
            val favourite = assertThrows(ResponseStatusException::class.java) { c.favourite(OWNER, id, null, "true") }
            val unfavourite =
                assertThrows(ResponseStatusException::class.java) { c.unfavourite(OWNER, id, null, "true") }
            assertEquals(HttpStatus.NOT_FOUND, favourite.statusCode)
            assertEquals(HttpStatus.NOT_FOUND, unfavourite.statusCode)
        }
    }
}
