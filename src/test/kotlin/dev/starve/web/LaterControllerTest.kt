package dev.starve.web

import dev.starve.application.LaterService
import dev.starve.application.MemorySources
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

class LaterControllerTest {
    private val now = Instant.parse("2026-10-11T12:00:00Z")

    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun controller(items: TimelineItems) = LaterController(LaterService(items, MemorySources(), clock))

    @Test
    fun `saving answers htmx with the saved toggle`() {
        val items = TimelineItems(links = mapOf(5L to null))

        val view = controller(items).save(OWNER, "5", null, "true")

        assertEquals("later :: toggle", view.viewName)
        assertEquals(listOf(5L to now), items.laterCalls)
        assertEquals(LaterView("later-5", 5, true), view.model["laterToggle"])
    }

    @Test
    fun `clearing answers htmx with the unsaved toggle`() {
        val items = TimelineItems(links = mapOf(5L to null))

        val view = controller(items).clear(OWNER, "5", null, "true")

        assertEquals("later :: toggle", view.viewName)
        assertEquals(listOf<Pair<Long, Instant?>>(5L to null), items.laterCalls)
        assertEquals(LaterView("later-5", 5, false), view.model["laterToggle"])
    }

    @Test
    fun `a toggle from the reader or the detail view answers with its own toggle and the row's out of band`() {
        listOf("reader", "detail").forEach { pane ->
            val view = controller(TimelineItems(links = mapOf(5L to null))).save(OWNER, "5", pane, "true")

            assertEquals("later :: pair", view.viewName)
            assertEquals(LaterView("$pane-later", 5, true, from = pane), view.model["laterToggle"])
            assertEquals(LaterView("later-5", 5, true, oob = true), view.model["rowLater"])
        }
    }

    @Test
    fun `a pane the endpoint does not know gets only the row toggle`() {
        val view = controller(TimelineItems(links = mapOf(5L to null))).save(OWNER, "5", "elsewhere", "true")

        assertEquals("later :: toggle", view.viewName)
        assertEquals(LaterView("later-5", 5, true), view.model["laterToggle"])
    }

    @Test
    fun `a plain request redirects to the timeline`() {
        val c = controller(TimelineItems(links = mapOf(5L to null)))

        assertEquals("redirect:/feed", c.save(OWNER, "5", null, null).viewName)
        assertEquals("redirect:/feed", c.clear(OWNER, "5", null, null).viewName)
    }

    @Test
    fun `an unknown or non-numeric item is not found for either toggle`() {
        val c = controller(TimelineItems(links = mapOf(5L to null)))

        listOf("9999", "abc").forEach { id ->
            val save = assertThrows(ResponseStatusException::class.java) { c.save(OWNER, id, null, "true") }
            val clear = assertThrows(ResponseStatusException::class.java) { c.clear(OWNER, id, null, "true") }
            assertEquals(HttpStatus.NOT_FOUND, save.statusCode)
            assertEquals(HttpStatus.NOT_FOUND, clear.statusCode)
        }
    }
}
