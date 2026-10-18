package dev.starve.application

import dev.starve.domain.Group
import dev.starve.domain.GroupSummary
import dev.starve.domain.Source
import dev.starve.domain.Tag
import dev.starve.domain.TagSummary
import dev.starve.domain.Validators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GroupServiceTest {
    private val music = Group(1, "Music")
    private val groups = MemoryGroups(mutableListOf(music), mapOf(1L to 4), 9)
    private val sources = MemorySources(mutableListOf(Source(5, "http://a.test/f", "A", Validators(), null, null)))
    private val tagCounts = mutableMapOf<String, Int>()
    private val tags = MemoryTags(tagCounts)
    private val service = GroupService(groups, sources, tags)

    @Test
    fun `creating stores the trimmed name`() {
        val result = service.create(OWNER, "  News ")

        assertEquals(GroupResult.Saved(Group(2, "News")), result)
    }

    @Test
    fun `creating rejects blank, long and duplicate names`() {
        assertEquals(GroupResult.Blank, service.create(OWNER, " "))
        assertEquals(GroupResult.TooLong, service.create(OWNER, "x".repeat(51)))
        assertEquals(GroupResult.Duplicate, service.create(OWNER, "MUSIC"))
        assertEquals(listOf(music), service.list(OWNER))
    }

    @Test
    fun `renaming allows a case variant of its own name`() {
        assertEquals(GroupResult.Saved(Group(1, "MUSIC")), service.rename(OWNER, 1, "MUSIC"))
        assertEquals("MUSIC", service.find(OWNER, 1)?.name)
    }

    @Test
    fun `renaming rejects a clash with another group and bad names`() {
        service.create(OWNER, "News")

        assertEquals(GroupResult.Duplicate, service.rename(OWNER, 2, "music"))
        assertEquals(GroupResult.Blank, service.rename(OWNER, 2, ""))
        assertEquals(GroupResult.TooLong, service.rename(OWNER, 2, "x".repeat(51)))
        assertEquals(GroupResult.Missing, service.rename(OWNER, 9, "Other"))
        assertEquals("News", service.find(OWNER, 2)?.name)
    }

    @Test
    fun `navigation combines the total with the unread count of each group`() {
        val nav = service.navigation(OWNER)

        assertEquals(9, nav.totalUnread)
        assertEquals(listOf(GroupSummary(music, 4)), nav.groups)
        assertEquals(emptyList<TagSummary>(), nav.tags)
    }

    @Test
    fun `navigation lists the owner's tags alphabetically with the unread count of what each narrows to`() {
        val rust = tags.insert(OWNER, Tag(0, "rust", listOf("rust")))
        val ai = tags.insert(OWNER, Tag(0, "AI", listOf("AI"), groupId = 1))
        tags.insert(OTHER_OWNER, Tag(0, "Other", listOf("x")))
        tagCounts[ai.match] = 2

        val nav = service.navigation(OWNER)

        assertEquals(listOf(TagSummary(ai, 2), TagSummary(rust, 0)), nav.tags)
        assertEquals(listOf(ai.narrowing, rust.narrowing), tags.counted)
    }

    @Test
    fun `a tag is found only for its owner`() {
        val ai = tags.insert(OWNER, Tag(0, "AI", listOf("AI")))

        assertEquals(ai, service.findTag(OWNER, ai.id))
        assertNull(service.findTag(OTHER_OWNER, ai.id))
    }

    @Test
    fun `assigning needs an existing group, and null ungroups`() {
        assertTrue(service.assign(OWNER, 5, 1))
        assertTrue(service.assign(OWNER, 5, null))
        assertFalse(service.assign(OWNER, 5, 9))
        assertEquals(listOf<Pair<Long, Long?>>(5L to 1L, 5L to null), sources.assignments)
    }

    @Test
    fun `deleting removes the group`() {
        assertTrue(service.delete(OWNER, 1))
        assertFalse(service.delete(OWNER, 1))

        assertNull(service.find(OWNER, 1))
    }

    @Test
    fun `another user's group cannot be found, renamed, deleted or filled`() {
        assertNull(service.find(OTHER_OWNER, 1))
        assertEquals(emptyList<Group>(), service.list(OTHER_OWNER))
        assertEquals(GroupResult.Missing, service.rename(OTHER_OWNER, 1, "Tunes"))
        assertFalse(service.delete(OTHER_OWNER, 1))
        assertFalse(service.assign(OTHER_OWNER, 5, 1))
        assertEquals(music, service.find(OWNER, 1))
    }

    @Test
    fun `a user cannot put their source in a group of another user, nor move another user's source`() {
        val others = MemorySources(mutableListOf(Source(6, "http://b.test/f", "B", Validators(), null, null)))
        val mixed = GroupService(groups, others, MemoryTags())

        assertFalse(mixed.assign(OTHER_OWNER, 6, 1))
        assertTrue(mixed.assign(OWNER, 6, null))
        assertEquals(listOf<Pair<Long, Long?>>(6L to null), others.assignments)
    }

    @Test
    fun `two users can each have a group with the same name`() {
        assertEquals(GroupResult.Saved(Group(2, "music")), service.create(OTHER_OWNER, "music"))
        assertEquals(OTHER_OWNER, groups.created.getValue(2))
        assertEquals(GroupResult.Duplicate, service.create(OTHER_OWNER, "MUSIC"))
        assertEquals(listOf(music), service.list(OWNER))
    }
}
