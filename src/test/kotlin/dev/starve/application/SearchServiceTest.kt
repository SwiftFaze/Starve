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

class SearchServiceTest {
    private val now = Instant.parse("2026-10-11T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val source = Source(1, "http://a.test/f", "A", Validators(), null, null)

    private fun rows(count: Int) =
        (1..count).map { n ->
            TimelineItem(n.toLong(), "A", "T$n", Instant.ofEpochMilli(n.toLong()), null, false)
        }

    @Test
    fun `a search asks the index for the expression, one look-ahead row and the cursor`() {
        val items = TimelineItems(rows(TimelineRules.PAGE_SIZE + 1))
        val service = SearchService(items, MemorySources(mutableListOf(source)), clock)
        val after = Cursor(Instant.EPOCH, 9)

        val page = service.search(OWNER, "rust async", after)

        assertEquals(Triple("\"rust\" \"async\"*", after, TimelineRules.PAGE_SIZE + 1), items.searched)
        assertEquals(TimelineRules.PAGE_SIZE, page.items.size)
        assertEquals(Cursor(Instant.ofEpochMilli(50), 50), page.next)
        assertEquals(now, page.renderedAt)
        assertTrue(page.hasSources)
    }

    @Test
    fun `text with no words matches nothing without asking the index`() {
        val items = TimelineItems(rows(3))
        val service = SearchService(items, MemorySources(mutableListOf(source)), clock)

        val page = service.search(OWNER, "*", null)

        assertNull(items.searched)
        assertEquals(emptyList<TimelineItem>(), page.items)
        assertNull(page.next)
    }

    @Test
    fun `a search is made for the user asked for, and only their sources count`() {
        val items = TimelineItems(rows(1))
        val service = SearchService(items, MemorySources(mutableListOf(source)), clock)

        val page = service.search(OTHER_OWNER, "rust", null)

        assertEquals(listOf(OTHER_OWNER), items.calledFor)
        assertFalse(page.hasSources)
    }
}
