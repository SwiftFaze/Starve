package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class TimelineTest {
    @Test
    fun `a cursor survives encoding`() {
        val cursor = Cursor(Instant.ofEpochMilli(1_700_000_000_123), 42)

        assertEquals(cursor, Cursor.parse(cursor.encode()))
    }

    @Test
    fun `absent or malformed cursors parse to null`() {
        assertNull(Cursor.parse(null))
        assertNull(Cursor.parse(""))
        assertNull(Cursor.parse("12"))
        assertNull(Cursor.parse("a_1"))
        assertNull(Cursor.parse("1_b"))
        assertNull(Cursor.parse("1_2_3"))
    }

    @Test
    fun `a look-ahead row yields a next cursor at the last shown item`() {
        val rows = (1..TimelineRules.PAGE_SIZE + 1).map { item(it.toLong()) }

        val (shown, next) = TimelineRules.paginate(rows)

        assertEquals(TimelineRules.PAGE_SIZE, shown.size)
        assertEquals(Cursor(shown.last().publishedAt, shown.last().id), next)
    }

    @Test
    fun `exactly one page has no next cursor`() {
        val rows = (1..TimelineRules.PAGE_SIZE).map { item(it.toLong()) }

        val (shown, next) = TimelineRules.paginate(rows)

        assertEquals(TimelineRules.PAGE_SIZE, shown.size)
        assertNull(next)
    }

    @Test
    fun `no rows give an empty page`() {
        assertEquals(emptyList<TimelineItem>() to null, TimelineRules.paginate(emptyList()))
    }

    private fun item(id: Long) = TimelineItem(id, "S", "T$id", Instant.ofEpochMilli(id), null, false)
}
