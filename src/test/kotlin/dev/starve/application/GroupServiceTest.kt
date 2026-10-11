package dev.starve.application

import dev.starve.domain.Group
import dev.starve.domain.GroupSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GroupServiceTest {
    private val music = Group(1, "Music")
    private val groups = MemoryGroups(mutableListOf(music), mapOf(1L to 4), 9)
    private val sources = MemorySources()
    private val service = GroupService(groups, sources)

    @Test
    fun `creating stores the trimmed name`() {
        val result = service.create("  News ")

        assertEquals(GroupResult.Saved(Group(2, "News")), result)
    }

    @Test
    fun `creating rejects blank, long and duplicate names`() {
        assertEquals(GroupResult.Blank, service.create(" "))
        assertEquals(GroupResult.TooLong, service.create("x".repeat(51)))
        assertEquals(GroupResult.Duplicate, service.create("MUSIC"))
        assertEquals(listOf(music), service.list())
    }

    @Test
    fun `renaming allows a case variant of its own name`() {
        assertEquals(GroupResult.Saved(Group(1, "MUSIC")), service.rename(1, "MUSIC"))
        assertEquals("MUSIC", service.find(1)?.name)
    }

    @Test
    fun `renaming rejects a clash with another group and bad names`() {
        service.create("News")

        assertEquals(GroupResult.Duplicate, service.rename(2, "music"))
        assertEquals(GroupResult.Blank, service.rename(2, ""))
        assertEquals(GroupResult.TooLong, service.rename(2, "x".repeat(51)))
        assertEquals(GroupResult.Missing, service.rename(9, "Other"))
        assertEquals("News", service.find(2)?.name)
    }

    @Test
    fun `navigation combines the total with the unread count of each group`() {
        val nav = service.navigation()

        assertEquals(9, nav.totalUnread)
        assertEquals(listOf(GroupSummary(music, 4)), nav.groups)
    }

    @Test
    fun `assigning needs an existing group, and null ungroups`() {
        assertTrue(service.assign(5, 1))
        assertTrue(service.assign(5, null))
        assertFalse(service.assign(5, 9))
        assertEquals(listOf<Pair<Long, Long?>>(5L to 1L, 5L to null), sources.assignments)
    }

    @Test
    fun `deleting removes the group`() {
        service.delete(1)

        assertNull(service.find(1))
    }
}
