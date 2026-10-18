package dev.starve.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class ListEndTest {
    @Test
    fun `a view with nothing unread is caught up and one with a count refreshes itself`() {
        assertTrue(ListEnd(0, "every hour", "/feed/end").caughtUp)
        assertFalse(ListEnd(2, "every hour", "/feed/end").caughtUp)
        assertEquals("/feed/end", ListEnd(2, "every hour", "/feed/end").refreshHref)
    }

    @Test
    fun `a view without a count is never caught up and never refreshes`() {
        val end = ListEnd(null, "every hour", "/feed/end?later=1")

        assertFalse(end.caughtUp)
        assertNull(end.refreshHref)
    }

    @Test
    fun `the poll interval reads in minutes or whole hours`() {
        assertEquals("every 30 minutes", everyPhrase(Duration.ofMinutes(30)))
        assertEquals("every minute", everyPhrase(Duration.ofSeconds(20)))
        assertEquals("every 90 minutes", everyPhrase(Duration.ofMinutes(90)))
        assertEquals("every hour", everyPhrase(Duration.ofHours(1)))
        assertEquals("every 2 hours", everyPhrase(Duration.ofHours(2)))
    }
}
