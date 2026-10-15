package dev.starve.application

import dev.starve.domain.LaterCursor
import dev.starve.domain.SavedItem
import dev.starve.domain.Source
import dev.starve.domain.TimelineItem
import dev.starve.domain.TimelineRules
import dev.starve.domain.Validators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LaterServiceTest {
    private val now = Instant.parse("2026-10-11T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val source = Source(1, "http://a.test/f", "A", Validators(), null, null)

    private fun saved(
        id: Long,
        savedAt: Long,
    ) = SavedItem(
        TimelineItem(id, "A", "T$id", Instant.EPOCH, null, false, later = true),
        Instant.ofEpochMilli(savedAt),
    )

    @Test
    fun `saving stamps the current time and reports whether the item exists`() {
        val items = TimelineItems(links = mapOf(5L to null))
        val service = LaterService(items, MemorySources(), clock)

        assertTrue(service.save(OWNER, 5))
        assertFalse(service.save(OWNER, 6))
        assertEquals(listOf(5L to now, 6L to now), items.laterCalls)
    }

    @Test
    fun `clearing clears the saved mark and reports whether the item exists`() {
        val items = TimelineItems(links = mapOf(5L to null))
        val service = LaterService(items, MemorySources(), clock)

        assertTrue(service.clear(OWNER, 5))
        assertFalse(service.clear(OWNER, 6))
        assertEquals(listOf<Pair<Long, Instant?>>(5L to null, 6L to null), items.laterCalls)
    }

    @Test
    fun `the later page asks for saved items after the cursor with one look-ahead row`() {
        val items = TimelineItems(savedRows = (1..TimelineRules.PAGE_SIZE + 1).map { saved(it.toLong(), it.toLong()) })
        val service = LaterService(items, MemorySources(mutableListOf(source)), clock)
        val after = LaterCursor(Instant.EPOCH, 9)

        val page = service.page(OWNER, after)

        assertEquals(after to TimelineRules.PAGE_SIZE + 1, items.laterRequested)
        assertEquals(TimelineRules.PAGE_SIZE, page.items.size)
        assertEquals(LaterCursor(Instant.ofEpochMilli(50), 50), page.next)
        assertEquals(now, page.renderedAt)
        assertTrue(page.hasSources)
    }

    @Test
    fun `a short later page has no next cursor and no sources is reported`() {
        val items = TimelineItems(savedRows = listOf(saved(1, 1)))

        val page = LaterService(items, MemorySources(), clock).page(OWNER, null)

        assertNull(page.next)
        assertFalse(page.hasSources)
    }

    @Test
    fun `saving, clearing and paging are made for the user asked for, and only their sources count`() {
        val items = TimelineItems(links = mapOf(5L to null))
        val service = LaterService(items, MemorySources(mutableListOf(source)), clock)

        service.save(OTHER_OWNER, 5)
        service.clear(OTHER_OWNER, 5)
        val page = service.page(OTHER_OWNER, null)

        assertEquals(List(3) { OTHER_OWNER }, items.calledFor)
        assertFalse(page.hasSources)
    }
}
