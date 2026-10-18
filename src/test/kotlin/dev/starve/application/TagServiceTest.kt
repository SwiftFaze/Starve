package dev.starve.application

import dev.starve.domain.Group
import dev.starve.domain.Tag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TagServiceTest {
    private val tags = MemoryTags()
    private val groups = MemoryGroups(mutableListOf(Group(1, "Tech")), owners = mutableMapOf(1L to OWNER))
    private val service = TagService(tags, groups)

    @Test
    fun `a tag is saved with its trimmed name, checked terms, body setting and group`() {
        val result = service.create(OWNER, TagForm("  AI ", "AI,  LLM ,ai", matchBody = true, groupId = 1))

        val saved = Tag(1, "AI", listOf("AI", "LLM"), matchBody = true, groupId = 1)
        assertEquals(TagResult.Saved(saved), result)
        assertEquals(listOf(saved), service.list(OWNER))
        assertEquals(emptyList<Tag>(), service.list(OTHER_OWNER))
    }

    @Test
    fun `names are checked like group names and must be unique among the owner's tags`() {
        service.create(OWNER, TagForm("AI", "AI"))

        assertEquals(TagResult.Blank, service.create(OWNER, TagForm("  ", "AI")))
        assertEquals(TagResult.TooLong, service.create(OWNER, TagForm("x".repeat(51), "AI")))
        assertEquals(TagResult.Duplicate, service.create(OWNER, TagForm("ai", "LLM")))
        assertTrue(service.create(OTHER_OWNER, TagForm("ai", "LLM")) is TagResult.Saved)
        assertEquals(1, service.list(OWNER).size)
    }

    @Test
    fun `terms are rejected with their reason`() {
        assertEquals(TagResult.NoTerms, service.create(OWNER, TagForm("A", " , ")))
        assertEquals(TagResult.TooManyTerms, service.create(OWNER, TagForm("A", (1..21).joinToString { "t$it" })))
        assertEquals(TagResult.BadTerm("C++"), service.create(OWNER, TagForm("A", "Rust, C++")))
        assertEquals(emptyList<Tag>(), service.list(OWNER))
    }

    @Test
    fun `a group that is not the owner's is missing`() {
        assertEquals(TagResult.Missing, service.create(OWNER, TagForm("AI", "AI", groupId = 9)))
        assertEquals(TagResult.Missing, service.create(OTHER_OWNER, TagForm("AI", "AI", groupId = 1)))
    }

    @Test
    fun `updating replaces every field and may change the case of its own name`() {
        val id = (service.create(OWNER, TagForm("AI", "AI")) as TagResult.Saved).tag.id

        val result = service.update(OWNER, id, TagForm("ai", "LLM", matchBody = true, groupId = 1))

        assertEquals(TagResult.Saved(Tag(id, "ai", listOf("LLM"), true, 1)), result)
        assertEquals(listOf(Tag(id, "ai", listOf("LLM"), true, 1)), service.list(OWNER))
    }

    @Test
    fun `updating to another tag's name or with bad terms changes nothing`() {
        service.create(OWNER, TagForm("AI", "AI"))
        val id = (service.create(OWNER, TagForm("Rust", "rust")) as TagResult.Saved).tag.id

        assertEquals(TagResult.Duplicate, service.update(OWNER, id, TagForm("ai", "rust")))
        assertEquals(TagResult.BadTerm("C++"), service.update(OWNER, id, TagForm("Rust", "C++")))
        assertEquals(Tag(id, "Rust", listOf("rust")), tags.findById(OWNER, id))
    }

    @Test
    fun `another owner's or an unknown tag cannot be updated or deleted`() {
        val id = (service.create(OWNER, TagForm("AI", "AI")) as TagResult.Saved).tag.id

        assertEquals(TagResult.Missing, service.update(OTHER_OWNER, id, TagForm("X", "x")))
        assertEquals(TagResult.Missing, service.update(OWNER, 99, TagForm("X", "x")))
        assertFalse(service.delete(OTHER_OWNER, id))
        assertTrue(service.delete(OWNER, id))
        assertNull(tags.findById(OWNER, id))
    }

    @Test
    fun `a tag narrows to its group and its match`() {
        val tag = Tag(1, "AI", listOf("AI"), groupId = 3)

        assertEquals(Narrowing(3, tag.match), tag.narrowing)
    }
}
