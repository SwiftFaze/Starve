package dev.starve.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LaterViewTest {
    @Test
    fun `a toggle exposes the fields its template renders`() {
        val view = LaterView("detail-later", 7, true, from = "detail", oob = true)

        assertEquals("detail-later", view.domId)
        assertEquals(7L, view.itemId)
        assertTrue(view.saved)
        assertEquals("detail", view.from)
        assertTrue(view.oob)
        assertFalse(LaterView("later-7", 7, false).oob)
        assertFalse(LaterView("later-7", 7, false).saved)
    }

    @Test
    fun `an unsaved toggle posts to later`() {
        assertEquals("/items/7/later", LaterView("later-7", 7, false).action)
    }

    @Test
    fun `a saved toggle posts to unlater`() {
        assertEquals("/items/7/unlater", LaterView("later-7", 7, true).action)
    }

    @Test
    fun `a pane toggle tells the endpoint which pane it came from`() {
        assertEquals("/items/7/unlater?from=detail", LaterView("detail-later", 7, true, from = "detail").action)
    }
}
