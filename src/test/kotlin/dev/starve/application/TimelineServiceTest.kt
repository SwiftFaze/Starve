package dev.starve.application

import dev.starve.domain.Cursor
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

class TimelineServiceTest {
    private val now = Instant.parse("2026-10-11T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val source = Source(1, "http://a.test/f", "A", Validators(), null, null)

    private fun rows(count: Int) =
        (1..count).map { n ->
            TimelineItem(n.toLong(), "A", "T$n", Instant.ofEpochMilli(n.toLong()), null, false)
        }

    @Test
    fun `a page asks for one look-ahead row and reports the next cursor and render time`() {
        val items = TimelineItems(rows(TimelineRules.PAGE_SIZE + 1))
        val service = TimelineService(items, MemorySources(mutableListOf(source)), clock)
        val after = Cursor(Instant.EPOCH, 9)

        val page = service.page(after, true)

        assertEquals(Triple<Cursor?, Boolean, Int>(after, true, TimelineRules.PAGE_SIZE + 1), items.requested)
        assertEquals(TimelineRules.PAGE_SIZE, page.items.size)
        assertEquals(Cursor(Instant.ofEpochMilli(50), 50), page.next)
        assertEquals(now, page.renderedAt)
        assertTrue(page.hasSources)
    }

    @Test
    fun `a short page has no next cursor and no sources is reported`() {
        val service = TimelineService(TimelineItems(rows(2)), MemorySources(), clock)

        val page = service.page(null, false)

        assertNull(page.next)
        assertFalse(page.hasSources)
    }

    @Test
    fun `marking read stamps the current time and reports whether the item exists`() {
        val items = TimelineItems(links = mapOf(5L to null))
        val service = TimelineService(items, MemorySources(), clock)

        assertTrue(service.markRead(5))
        assertFalse(service.markRead(6))
        assertEquals(listOf(5L to now, 6L to now), items.readCalls)
    }

    @Test
    fun `opening marks the item read and yields its link`() {
        val items = TimelineItems(links = mapOf(5L to "http://x.test/a", 6L to null))
        val service = TimelineService(items, MemorySources(), clock)

        assertEquals(OpenResult.Opened("http://x.test/a"), service.open(5))
        assertEquals(OpenResult.Opened(null), service.open(6))
        assertEquals(listOf(5L to now, 6L to now), items.readCalls)
    }

    @Test
    fun `opening an unknown item marks nothing`() {
        val items = TimelineItems()

        assertEquals(OpenResult.Missing, TimelineService(items, MemorySources(), clock).open(1))
        assertTrue(items.readCalls.isEmpty())
    }

    @Test
    fun `mark all read passes the render time as the first-seen cutoff`() {
        val items = TimelineItems()
        val cutoff = now.minusSeconds(30)

        TimelineService(items, MemorySources(), clock).markAllRead(cutoff)

        assertEquals(cutoff to now, items.markAllCall)
    }

    @Test
    fun `mark all read reports how many items the repository marked`() {
        val items = TimelineItems(markedAllCount = 4)

        val marked = TimelineService(items, MemorySources(), clock).markAllRead(now)

        assertEquals(4, marked)
    }
}
