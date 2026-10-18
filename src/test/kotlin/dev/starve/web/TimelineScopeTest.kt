package dev.starve.web

import dev.starve.application.GroupService
import dev.starve.application.ListFilter
import dev.starve.application.MemoryGroups
import dev.starve.application.MemorySources
import dev.starve.application.MemoryTags
import dev.starve.application.Narrowing
import dev.starve.application.OWNER
import dev.starve.application.narrowing
import dev.starve.domain.Group
import dev.starve.domain.Tag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class TimelineScopeTest {
    private val tags = MemoryTags()
    private val groups = GroupService(MemoryGroups(mutableListOf(Group(1, "Tech"))), MemorySources(), tags)

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

    @Test
    fun `a tag is named by its parameter, wins over a group and neighbours among its matches`() {
        val ai = tags.insert(OWNER, Tag(0, "AI", listOf("AI"), groupId = 7))

        val scope = scope(TimelineQuery(tag = ai.id.toString(), group = "1", show = "all"))

        assertEquals(ai, scope.tag)
        assertNull(scope.groupId)
        assertEquals("show=all&tag=${ai.id}", scope.listQuery)
        assertEquals(ai.narrowing, scope.narrowing)
        assertEquals(ListFilter(showRead = true, groupId = 7, match = ai.match), scope.neighbourFilter)
    }

    @Test
    fun `a group narrows to its sources only`() {
        val scope = scope(TimelineQuery(group = "1"))

        assertEquals(Narrowing(1), scope.narrowing)
        assertNull(scope.filter)
    }

    @Test
    fun `Favourites and Later ignore a tag`() {
        val ai = tags.insert(OWNER, Tag(0, "AI", listOf("AI")))

        assertNull(scope(TimelineQuery(tag = ai.id.toString(), favourites = "1")).tag)
        assertNull(scope(TimelineQuery(tag = ai.id.toString(), later = "1")).tag)
        assertEquals("later=1", scope(TimelineQuery(tag = ai.id.toString(), later = "1")).filter)
    }

    @Test
    fun `an unknown or malformed tag is not found, and dropped while searching`() {
        listOf("9", "x").forEach { raw ->
            val e = assertThrows(ResponseStatusException::class.java) { scope(TimelineQuery(tag = raw)) }
            assertEquals(HttpStatus.NOT_FOUND, e.statusCode)
            assertNull(scope(TimelineQuery(tag = raw, q = "a")).tag)
        }
        assertNull(scope(TimelineQuery(tag = "9", group = "1", q = "a")).groupId)
    }
}
