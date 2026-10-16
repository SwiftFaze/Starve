package dev.starve.web

import dev.starve.application.GroupService
import dev.starve.application.ListFilter
import dev.starve.application.MemoryGroups
import dev.starve.application.MemorySources
import dev.starve.application.OWNER
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TimelineScopeTest {
    private val groups = GroupService(MemoryGroups(), MemorySources())

    private fun scope(query: TimelineQuery) = scopeOf(groups, OWNER, query)

    @Test
    fun `the plain timeline with read items is named by show=all and neighbours among all rows`() {
        val scope = scope(TimelineQuery(show = "all"))

        assertEquals("show=all", scope.listQuery)
        assertEquals(ListFilter(showRead = true), scope.neighbourFilter)
    }

    @Test
    fun `the plain timeline names no list and neighbours among unread rows`() {
        val scope = scope(TimelineQuery())

        assertEquals("", scope.listQuery)
        assertEquals(ListFilter(showRead = false), scope.neighbourFilter)
    }

    @Test
    fun `Favourites is named by its parameter and neighbours among favourites, read or not`() {
        val scope = scope(TimelineQuery(favourites = "1", show = "all"))

        assertEquals("favourites=1", scope.listQuery)
        assertEquals(ListFilter(showRead = true, favourites = true), scope.neighbourFilter)
    }

    @Test
    fun `the render moment is carried on the list and its neighbour filter, and a non-number is ignored`() {
        val scope = scope(TimelineQuery(seen = "1234"))

        assertEquals("seen=1234", scope.listQuery)
        assertEquals(ListFilter(showRead = false, seen = 1234L), scope.neighbourFilter)
        assertNull(scope(TimelineQuery(seen = "soon")).seen)
        assertEquals("", scope(TimelineQuery(seen = "soon")).listQuery)
    }

    @Test
    fun `Later and search name their list but have no neighbour query`() {
        assertEquals("later=1", scope(TimelineQuery(later = "1")).listQuery)
        assertNull(scope(TimelineQuery(later = "1")).neighbourFilter)
        assertEquals("q=a+b", scope(TimelineQuery(q = "a b")).listQuery)
        assertNull(scope(TimelineQuery(q = "a b")).neighbourFilter)
    }
}
