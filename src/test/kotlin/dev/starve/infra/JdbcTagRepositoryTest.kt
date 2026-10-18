package dev.starve.infra

import dev.starve.application.ListFilter
import dev.starve.application.ListPosition
import dev.starve.application.Narrowing
import dev.starve.application.TimelineFilter
import dev.starve.application.narrowing
import dev.starve.domain.FeedItem
import dev.starve.domain.ReadMark
import dev.starve.domain.Tag
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.simple.JdbcClient
import java.nio.file.Path
import java.time.Instant
import javax.sql.DataSource

class JdbcTagRepositoryTest {
    @TempDir
    lateinit var tmp: Path

    private lateinit var dataSource: DataSource
    private lateinit var jdbc: JdbcClient
    private lateinit var sources: JdbcSourceRepository
    private lateinit var items: JdbcItemRepository
    private lateinit var groups: JdbcGroupRepository
    private lateinit var tags: JdbcTagRepository
    private lateinit var favourites: JdbcFavouriteRepository

    private val seen = Instant.parse("2026-10-11T12:00:00Z")

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConfig().dataSource(tmp.resolve("t.db").toString())
        Flyway
            .configure()
            .dataSource(dataSource)
            .javaMigrations(SearchIndexMigration())
            .load()
            .migrate()
        jdbc = JdbcClient.create(dataSource)
        jdbc.seedOwners()
        sources = JdbcSourceRepository(jdbc, SearchIndex(jdbc))
        items = JdbcItemRepository(jdbc, SearchIndex(jdbc))
        groups = JdbcGroupRepository(jdbc)
        tags = JdbcTagRepository(jdbc)
        favourites = JdbcFavouriteRepository(jdbc)
    }

    @AfterEach
    fun tearDown() = (dataSource as AutoCloseable).close()

    private fun post(
        sourceId: Long,
        title: String,
        minutes: Long,
        body: String? = null,
    ) = items.insertNew(
        sourceId,
        listOf(FeedItem(title, title, null, seen.minusSeconds(minutes * 60), null, null, body)),
        seen,
    )

    private fun titles(filter: TimelineFilter) = items.timeline(OWNER, null, filter, 10).map { it.title }

    @Test
    fun `tags are stored with their terms, body setting and group, listed alphabetically per owner`() {
        val tech = groups.insert(OWNER, "Tech")
        val rust = tags.insert(OWNER, Tag(0, "rust", listOf("rust")))
        val ai = tags.insert(OWNER, Tag(0, "AI", listOf("AI", "machine learning", "GPT*"), true, tech.id))
        tags.insert(OTHER, Tag(0, "Other", listOf("x")))

        assertEquals(listOf(ai, rust), tags.findAll(OWNER))
        val stored = Tag(ai.id, "AI", listOf("AI", "machine learning", "GPT*"), true, tech.id)
        assertEquals(stored, tags.findById(OWNER, ai.id))
        assertNull(tags.findById(OTHER, ai.id))
        assertEquals(ai, tags.findByName(OWNER, "ai"))
        assertNull(tags.findByName(OTHER, "ai"))
        assertThrows(DataAccessException::class.java) { tags.insert(OWNER, Tag(0, "Rust", listOf("x"))) }
    }

    @Test
    fun `an update replaces every field of the owner's tag only`() {
        val tech = groups.insert(OWNER, "Tech")
        val ai = tags.insert(OWNER, Tag(0, "AI", listOf("AI")))

        tags.update(OTHER, ai.copy(name = "Stolen"))
        tags.update(OWNER, Tag(ai.id, "Models", listOf("LLM"), true, tech.id))

        assertEquals(Tag(ai.id, "Models", listOf("LLM"), true, tech.id), tags.findById(OWNER, ai.id))
    }

    @Test
    fun `deleting a tag is the owner's alone, and deleting its group removes the limit`() {
        val tech = groups.insert(OWNER, "Tech")
        val ai = tags.insert(OWNER, Tag(0, "AI", listOf("AI"), groupId = tech.id))

        groups.delete(OWNER, tech.id)
        assertNull(tags.findById(OWNER, ai.id)?.groupId)
        assertFalse(tags.delete(OTHER, ai.id))
        assertTrue(tags.delete(OWNER, ai.id))
        assertFalse(tags.delete(OWNER, ai.id))
    }

    @Test
    fun `a tag's match lists title words from every source, newest first`() {
        val one = sources.insert(OWNER, "http://a.test/f", "One").id
        val two = sources.insert(OWNER, "http://b.test/f", "Two").id
        post(one, "OpenAI ships GPT-6", 3)
        post(two, "The LLM race", 2)
        post(two, "Said and Thailand", 1)
        post(two, "GPT4o launches", 4)
        post(two, "AI in the body only", 5, "<p>nothing</p>")
        val tag = Tag(0, "AI", listOf("OpenAI", "LLM", "GPT*", "AI"))

        assertEquals(
            listOf("The LLM race", "OpenAI ships GPT-6", "GPT4o launches", "AI in the body only"),
            titles(TimelineFilter(false, match = tag.match)),
        )
        assertEquals(4, tags.unreadCount(OWNER, tag.narrowing))
        assertEquals(0, tags.unreadCount(OTHER, tag.narrowing))
    }

    @Test
    fun `bodies match only when the tag says so`() {
        val one = sources.insert(OWNER, "http://a.test/f", "One").id
        post(one, "Weekly notes", 1, "<p>This week: AI everywhere.</p>")

        assertEquals(emptyList<String>(), titles(TimelineFilter(false, match = Tag(0, "t", listOf("AI")).match)))
        val anywhere = Tag(0, "t", listOf("AI"), matchBody = true)
        assertEquals(listOf("Weekly notes"), titles(TimelineFilter(false, match = anywhere.match)))
    }

    @Test
    fun `the group limit, read state and match narrow the timeline, counts, neighbours and mark all read together`() {
        val tech = groups.insert(OWNER, "Tech")
        val techBlog = sources.insert(OWNER, "http://a.test/f", "Tech").id
        val music = sources.insert(OWNER, "http://b.test/f", "Music").id
        sources.assignGroup(OWNER, techBlog, tech.id)
        post(techBlog, "AI first", 1)
        post(techBlog, "Rust", 2)
        post(techBlog, "AI second", 3)
        post(music, "AI music", 4)
        val ai = Tag(0, "AI", listOf("AI"), groupId = tech.id)
        val within = ai.narrowing

        assertEquals(listOf("AI first", "AI second"), titles(TimelineFilter(false, tech.id, ai.match)))
        assertEquals(2, tags.unreadCount(OWNER, within))
        val first = items.timeline(OWNER, null, TimelineFilter(false, tech.id, ai.match), 1).single()
        val filter = ListFilter(false, tech.id, match = ai.match)
        val around = items.neighbours(OWNER, ListPosition(first.id, first.publishedAt), filter)
        assertNull(around.previous)
        assertEquals("AI second", around.next?.title)

        items.markRead(OWNER, first.id, ReadMark.opened(seen))
        assertEquals(1, tags.unreadCount(OWNER, within))
        assertEquals(listOf("AI first", "AI second"), titles(TimelineFilter(true, tech.id, ai.match)))

        assertEquals(1, items.markAllRead(OWNER, seen, seen, within))
        assertEquals(listOf("Rust", "AI music"), titles(TimelineFilter(false)))
        assertEquals(0, items.markAllRead(OTHER, seen, seen, Narrowing(match = ai.match)))
    }

    @Test
    fun `neighbours in Favourites are the owner's favourites, read or not, whatever the group and match`() {
        val tech = groups.insert(OWNER, "Tech")
        val blog = sources.insert(OWNER, "http://a.test/f", "Blog").id
        post(blog, "Newer", 1)
        post(blog, "Middle", 2)
        post(blog, "Older", 3)
        val rows = items.timeline(OWNER, null, TimelineFilter(false), 10)
        val (newer, middle, older) = rows
        favourites.favourite(OWNER, newer.id, seen)
        favourites.favourite(OWNER, older.id, seen)
        items.markRead(OWNER, older.id, ReadMark.opened(seen))
        val filter = ListFilter(showRead = false, groupId = tech.id, favourites = true, match = "nothing")

        val around = items.neighbours(OWNER, ListPosition(middle.id, middle.publishedAt), filter)

        assertEquals("Newer" to "Older", around.previous?.title to around.next?.title)
        assertNull(items.neighbours(OTHER, ListPosition(middle.id, middle.publishedAt), filter).next)
    }
}
