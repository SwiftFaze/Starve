package dev.starve.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class FavouriteServiceTest {
    private val now = Instant.parse("2026-10-11T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `favouriting stamps the current time and reports whether the item exists`() {
        val items = TimelineItems(links = mapOf(5L to null))
        val service = FavouriteService(items, clock)

        assertTrue(service.favourite(5))
        assertFalse(service.favourite(6))
        assertEquals(listOf(5L to now, 6L to now), items.favouriteCalls)
    }

    @Test
    fun `unfavouriting clears the favourite and reports whether the item exists`() {
        val items = TimelineItems(links = mapOf(5L to null))
        val service = FavouriteService(items, clock)

        assertTrue(service.unfavourite(5))
        assertFalse(service.unfavourite(6))
        assertEquals(listOf(5L to null, 6L to null), items.favouriteCalls)
    }
}
