package dev.starve.infra

import dev.starve.application.ItemLink
import dev.starve.domain.Cursor
import dev.starve.domain.FeedItem
import dev.starve.domain.Group
import dev.starve.domain.GroupSummary
import dev.starve.domain.Validators
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.simple.JdbcClient
import java.nio.file.Path
import java.time.Instant
import javax.sql.DataSource

class JdbcRepositoriesTest {
    @TempDir
    lateinit var tmp: Path

    private lateinit var dataSource: DataSource
    private lateinit var jdbc: JdbcClient
    private lateinit var sources: JdbcSourceRepository
    private lateinit var items: JdbcItemRepository
    private lateinit var groups: JdbcGroupRepository

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConfig().dataSource(tmp.resolve("t.db").toString())
        Flyway
            .configure()
            .dataSource(dataSource)
            .load()
            .migrate()
        jdbc = JdbcClient.create(dataSource)
        sources = JdbcSourceRepository(jdbc)
        items = JdbcItemRepository(jdbc)
        groups = JdbcGroupRepository(jdbc)
    }

    @AfterEach
    fun tearDown() = (dataSource as AutoCloseable).close()

    @Test
    fun `a recorded success is read back with its validators and time`() {
        val source = sources.insert("http://a.test/f", "T")
        val at = Instant.parse("2026-10-11T12:00:00Z")

        sources.recordSuccess(source.id, "New", Validators("etag", "modified"), at)

        val stored = sources.findById(source.id)!!
        assertEquals(Validators("etag", "modified"), stored.validators)
        assertEquals("New", stored.title)
        assertEquals(at, stored.lastFetchedAt)
    }

    @Test
    fun `a recorded failure is read back and a later success clears it`() {
        val source = sources.insert("http://a.test/f", "T")

        sources.recordFailure(source.id, "bad", Instant.EPOCH)
        assertEquals("bad", sources.findById(source.id)!!.lastError)

        sources.recordSuccess(source.id, "T", Validators(), Instant.EPOCH)
        assertNull(sources.findById(source.id)!!.lastError)
    }

    @Test
    fun `an unknown source id is not found`() {
        assertNull(sources.findById(99))
    }

    @Test
    fun `an undated item is stored as published when first seen and an existing key is kept`() {
        val source = sources.insert("http://a.test/f", "T")
        val seen = Instant.parse("2026-10-11T12:00:00Z")

        items.insertNew(source.id, listOf(item("k", "first")), seen)
        items.insertNew(source.id, listOf(item("k", "second")), seen.plusSeconds(60))

        val row =
            jdbc
                .sql("SELECT title, published_at, first_seen_at FROM items")
                .query { rs, _ -> Triple(rs.getString(1), rs.getLong(2), rs.getLong(3)) }
                .single()
        assertEquals(Triple("first", seen.toEpochMilli(), seen.toEpochMilli()), row)
    }

    @Test
    fun `deleting by cutoff removes only older items and reports the count`() {
        val source = sources.insert("http://a.test/f", "T")
        val cutoff = Instant.parse("2026-10-11T12:00:00Z")
        items.insertNew(source.id, listOf(item("old", "old")), cutoff.minusSeconds(1))
        items.insertNew(source.id, listOf(item("edge", "edge")), cutoff)

        val removed = items.deleteFirstSeenBefore(cutoff)

        assertEquals(1, removed)
        assertEquals(listOf("edge"), jdbc.sql("SELECT item_key FROM items").query(String::class.java).list())
    }

    @Test
    fun `the timeline is newest first with ties by id, hides read items and pages by cursor`() {
        val source = sources.insert("http://a.test/f", "Src")
        val seen = Instant.parse("2026-10-11T12:00:00Z")
        val same = seen.minusSeconds(60)
        items.insertNew(source.id, listOf(dated("a", same), dated("b", same), dated("c", seen)), seen)
        val ids = jdbc.sql("SELECT id FROM items ORDER BY item_key").query(Long::class.java).list()

        val all = items.timeline(null, false, 10, null)

        assertEquals(listOf("c", "b", "a"), all.map { it.title })
        assertEquals("Src", all.first().sourceTitle)
        val next = items.timeline(Cursor(all[0].publishedAt, all[0].id), false, 10, null)
        assertEquals(listOf("b", "a"), next.map { it.title })
        val afterTie = items.timeline(Cursor(same, ids[1]), false, 10, null)
        assertEquals(listOf("a"), afterTie.map { it.title })
        assertEquals(1, items.timeline(null, false, 1, null).size)

        assertEquals(true, items.markRead(ids[2], seen))
        assertEquals(listOf("b", "a"), items.timeline(null, false, 10, null).map { it.title })
        assertEquals(listOf("c", "b", "a"), items.timeline(null, true, 10, null).map { it.title })
        assertEquals(listOf(true, false, false), items.timeline(null, true, 10, null).map { it.read })
    }

    @Test
    fun `the timeline flags items whose link is playable`() {
        val source = sources.insert("http://a.test/f", "Src")
        val at = Instant.parse("2026-10-11T12:00:00Z")
        val video = dated("v", at).copy(link = "https://youtu.be/dQw4w9WgXcQ")
        val story = dated("s", at.minusSeconds(1)).copy(link = "https://example.com/s")
        val linkless = dated("n", at.minusSeconds(2)).copy(link = null)
        items.insertNew(source.id, listOf(video, story, linkless), at)

        assertEquals(listOf(true, false, false), items.timeline(null, true, 10).map { it.playable })
    }

    @Test
    fun `marking read keeps the first read time and unknown items are reported`() {
        val source = sources.insert("http://a.test/f", "T")
        items.insertNew(source.id, listOf(item("k", "t")), Instant.EPOCH)
        val id = jdbc.sql("SELECT id FROM items").query(Long::class.java).single()

        assertEquals(true, items.markRead(id, Instant.ofEpochMilli(100)))
        assertEquals(true, items.markRead(id, Instant.ofEpochMilli(200)))
        assertEquals(false, items.markRead(id + 1, Instant.ofEpochMilli(300)))

        assertEquals(100L, jdbc.sql("SELECT read_at FROM items").query(Long::class.java).single())
    }

    @Test
    fun `finding a link distinguishes a missing item from an item without a link`() {
        val source = sources.insert("http://a.test/f", "T")
        items.insertNew(
            source.id,
            listOf(item("k", "t"), FeedItem("l", "u", "http://x.test/a", null, null, null)),
            Instant.EPOCH,
        )
        val ids = jdbc.sql("SELECT id FROM items ORDER BY id").query(Long::class.java).list()

        assertEquals(ItemLink(null), items.findLink(ids[0]))
        assertEquals(ItemLink("http://x.test/a"), items.findLink(ids[1]))
        assertNull(items.findLink(ids[1] + 1))
    }

    @Test
    fun `mark all read only covers unread items first seen at or before the cutoff`() {
        val source = sources.insert("http://a.test/f", "T")
        val cutoff = Instant.parse("2026-10-11T12:00:00Z")
        items.insertNew(source.id, listOf(item("old", "old")), cutoff.minusSeconds(1))
        items.insertNew(source.id, listOf(item("edge", "edge")), cutoff)
        items.insertNew(source.id, listOf(item("late", "late")), cutoff.plusSeconds(1))

        assertEquals(2, items.markAllRead(cutoff, cutoff.plusSeconds(5), null))
        assertEquals(0, items.markAllRead(cutoff, cutoff.plusSeconds(9), null))

        assertEquals(listOf("late"), items.timeline(null, false, 10, null).map { it.title })
    }

    @Test
    fun `groups are unique ignoring case, listed alphabetically and renamed in place`() {
        val zebra = groups.insert("zebra")
        val apple = groups.insert("Apple")

        assertEquals(listOf("Apple", "zebra"), groups.findAll().map { it.name })
        assertEquals(apple, groups.findByName("APPLE"))
        assertNull(groups.findByName("pear"))
        groups.rename(zebra.id, "Mango")
        assertEquals(Group(zebra.id, "Mango"), groups.findById(zebra.id))
        assertEquals(listOf("Apple", "Mango"), groups.findAll().map { it.name })
        assertThrows(DataAccessException::class.java) { groups.insert("apple") }
    }

    @Test
    fun `a group filter scopes the timeline and mark all read, and deleting a group ungroups its sources`() {
        val music = groups.insert("Music")
        val alpha = sources.insert("http://a.test/f", "Alpha")
        val beta = sources.insert("http://b.test/f", "Beta")
        val seen = Instant.parse("2026-10-11T12:00:00Z")
        items.insertNew(alpha.id, listOf(dated("a", seen)), seen)
        items.insertNew(beta.id, listOf(dated("b", seen)), seen)
        sources.assignGroup(alpha.id, music.id)

        assertEquals(music.id, sources.findById(alpha.id)?.groupId)
        assertNull(sources.findById(beta.id)?.groupId)
        assertEquals(listOf("a"), items.timeline(null, false, 10, music.id).map { it.title })
        assertEquals(listOf(GroupSummary(music, 1)), groups.unreadCounts())
        assertEquals(2, groups.totalUnread())
        assertEquals(1, items.markAllRead(seen, seen, music.id))
        assertEquals(listOf("b"), items.timeline(null, false, 10, null).map { it.title })
        assertEquals(listOf(GroupSummary(music, 0)), groups.unreadCounts())

        groups.delete(music.id)

        assertNull(sources.findById(alpha.id)?.groupId)
        assertEquals(2, sources.findAll().size)
    }

    private fun dated(
        key: String,
        at: Instant,
    ) = FeedItem(key, key, null, at, null, null)

    private fun item(
        key: String,
        title: String,
    ) = FeedItem(key, title, null, null, null, null)
}
