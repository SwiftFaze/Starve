package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class LaterTest {
    private fun saved(
        id: Long,
        savedAt: Long,
    ) = SavedItem(TimelineItem(id, "A", "T$id", Instant.EPOCH, null, false), Instant.ofEpochMilli(savedAt))

    @Test
    fun `a later cursor encodes as saved time and id and parses back`() {
        val cursor = LaterCursor(Instant.ofEpochMilli(1_000), 7)

        assertEquals("1000_7", cursor.encode())
        assertEquals(cursor, LaterCursor.parse(cursor.encode()))
    }

    @Test
    fun `a missing or malformed later cursor parses to null`() {
        listOf<String?>(null, "", "abc", "1000_", "_7", "1000_x").forEach { assertNull(LaterCursor.parse(it), "$it") }
    }

    @Test
    fun `a full page with one more row names the last shown item as the next cursor`() {
        val rows = (1..TimelineRules.PAGE_SIZE + 1).map { saved(it.toLong(), it.toLong()) }

        val (shown, next) = LaterRules.paginate(rows)

        assertEquals(TimelineRules.PAGE_SIZE, shown.size)
        val size = TimelineRules.PAGE_SIZE.toLong()
        assertEquals(LaterCursor(Instant.ofEpochMilli(size), size), next)
    }

    @Test
    fun `a page of exactly the page size has no next cursor`() {
        val rows = (1..TimelineRules.PAGE_SIZE).map { saved(it.toLong(), it.toLong()) }

        val (shown, next) = LaterRules.paginate(rows)

        assertEquals(TimelineRules.PAGE_SIZE, shown.size)
        assertNull(next)
    }

    @Test
    fun `a short page has no next cursor`() {
        val (shown, next) = LaterRules.paginate(listOf(saved(1, 1)))

        assertEquals(1, shown.size)
        assertNull(next)
    }
}
