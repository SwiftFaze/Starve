package dev.starve.infra

import dev.starve.application.DetailRef
import dev.starve.application.ItemRef
import dev.starve.application.ListFilter
import dev.starve.application.ListPosition
import dev.starve.application.Oembed
import dev.starve.application.PostRef
import dev.starve.application.TimelineFilter
import dev.starve.domain.Cursor
import dev.starve.domain.FeedItem
import dev.starve.domain.Group
import dev.starve.domain.GroupSummary
import dev.starve.domain.LaterCursor
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
    private lateinit var favourites: JdbcFavouriteRepository
    private lateinit var later: JdbcLaterRepository
    private lateinit var groups: JdbcGroupRepository

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
        favourites = JdbcFavouriteRepository(jdbc)
        later = JdbcLaterRepository(jdbc)
        groups = JdbcGroupRepository(jdbc)
    }

    @AfterEach
    fun tearDown() = (dataSource as AutoCloseable).close()

    @Test
    fun `a recorded success is read back with its validators and time`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        val at = Instant.parse("2026-10-11T12:00:00Z")

        sources.recordSuccess(source.id, "New", Validators("etag", "modified"), at)

        val stored = sources.findById(source.id)!!
        assertEquals(Validators("etag", "modified"), stored.validators)
        assertEquals("New", stored.title)
        assertEquals(at, stored.lastFetchedAt)
    }

    @Test
    fun `a recorded favicon and its check time are read back, and a miss keeps the check time`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        val at = Instant.parse("2026-10-11T12:00:00Z")
        assertNull(sources.findById(source.id)!!.faviconCheckedAt)

        sources.recordFavicon(source.id, "http://a.test/i.png", at)
        val stored = sources.findAll().single()
        assertEquals("http://a.test/i.png", stored.faviconUrl)
        assertEquals(at, stored.faviconCheckedAt)

        sources.recordFavicon(source.id, null, at.plusSeconds(60))
        assertNull(sources.findById(source.id)!!.faviconUrl)
        assertEquals(at.plusSeconds(60), sources.findById(source.id)!!.faviconCheckedAt)
    }

    @Test
    fun `a recorded failure is read back and a later success clears it`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")

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
        val source = sources.insert(OWNER, "http://a.test/f", "T")
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
    fun `a later insert fills a missing thumbnail but never replaces an existing one`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        val first = listOf(item("bare", "bare"), pictured("kept", "http://a.test/old.jpg"))
        items.insertNew(source.id, first, Instant.EPOCH)

        items.insertNew(
            source.id,
            listOf(pictured("bare", "http://a.test/new.jpg"), pictured("kept", "http://a.test/other.jpg")),
            Instant.EPOCH.plusSeconds(60),
        )

        val thumbs =
            jdbc
                .sql("SELECT item_key, thumbnail_url FROM items ORDER BY item_key")
                .query { rs, _ -> rs.getString(1) to rs.getString(2) }
                .list()
        assertEquals(listOf("bare" to "http://a.test/new.jpg", "kept" to "http://a.test/old.jpg"), thumbs)
    }

    @Test
    fun `a later insert fills a missing body but never replaces an existing one`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        items.insertNew(source.id, listOf(item("bare", "bare"), withBody("kept", "<p>first</p>")), Instant.EPOCH)

        items.insertNew(
            source.id,
            listOf(withBody("bare", "<p>filled</p>"), withBody("kept", "<p>second</p>")),
            Instant.EPOCH.plusSeconds(60),
        )

        val bodies =
            jdbc
                .sql("SELECT item_key, body FROM items ORDER BY item_key")
                .query { rs, _ -> rs.getString(1) to rs.getString(2) }
                .list()
        assertEquals(listOf("bare" to "<p>filled</p>", "kept" to "<p>first</p>"), bodies)
    }

    @Test
    fun `finding a post returns its source, published date, link and body, or nothing when missing`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        val published = Instant.parse("2026-03-04T05:06:07Z")
        val post = FeedItem("p", "Post", "http://a.test/p", published, null, null, "<p>b</p>")
        items.insertNew(source.id, listOf(post, item("bare", "bare")), Instant.EPOCH)
        val ids = jdbc.sql("SELECT id FROM items ORDER BY id").query(Long::class.java).list()

        val expected =
            PostRef(ids[0], "http://a.test/p", "Post", "T", published, "<p>b</p>", sourceUrl = "http://a.test/f")
        assertEquals(expected, items.findPost(OWNER, ids[0]))
        val bare = PostRef(ids[1], null, "bare", "T", Instant.EPOCH, null, sourceUrl = "http://a.test/f")
        assertEquals(bare, items.findPost(OWNER, ids[1]))
        assertNull(items.findPost(OWNER, ids[1] + 1))
    }

    @Test
    fun `deleting by cutoff removes only older items and reports the count`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        val cutoff = Instant.parse("2026-10-11T12:00:00Z")
        items.insertNew(source.id, listOf(item("old", "old")), cutoff.minusSeconds(1))
        items.insertNew(source.id, listOf(item("edge", "edge")), cutoff)

        val removed = items.deleteFirstSeenBefore(cutoff)

        assertEquals(1, removed)
        assertEquals(listOf("edge"), jdbc.sql("SELECT item_key FROM items").query(String::class.java).list())
    }

    @Test
    fun `the timeline is newest first with ties by id, hides read items and pages by cursor`() {
        val source = sources.insert(OWNER, "http://a.test/f", "Src")
        val seen = Instant.parse("2026-10-11T12:00:00Z")
        val same = seen.minusSeconds(60)
        items.insertNew(source.id, listOf(dated("a", same), dated("b", same), dated("c", seen)), seen)
        val ids = jdbc.sql("SELECT id FROM items ORDER BY item_key").query(Long::class.java).list()

        val all = items.timeline(OWNER, null, TimelineFilter(false), 10)

        assertEquals(listOf("c", "b", "a"), all.map { it.title })
        assertEquals("Src", all.first().sourceTitle)
        val next = items.timeline(OWNER, Cursor(all[0].publishedAt, all[0].id), TimelineFilter(false), 10)
        assertEquals(listOf("b", "a"), next.map { it.title })
        val afterTie = items.timeline(OWNER, Cursor(same, ids[1]), TimelineFilter(false), 10)
        assertEquals(listOf("a"), afterTie.map { it.title })
        assertEquals(1, items.timeline(OWNER, null, TimelineFilter(false), 1).size)

        assertEquals(true, items.markRead(OWNER, ids[2], seen))
        assertEquals(listOf("b", "a"), items.timeline(OWNER, null, TimelineFilter(false), 10).map { it.title })
        assertEquals(listOf("c", "b", "a"), items.timeline(OWNER, null, TimelineFilter(true), 10).map { it.title })
        assertEquals(listOf(true, false, false), items.timeline(OWNER, null, TimelineFilter(true), 10).map { it.read })
    }

    @Test
    fun `the timeline flags items whose link is playable`() {
        val source = sources.insert(OWNER, "http://a.test/f", "Src")
        val at = Instant.parse("2026-10-11T12:00:00Z")
        val video = dated("v", at).copy(link = "https://youtu.be/dQw4w9WgXcQ")
        val story = dated("s", at.minusSeconds(1)).copy(link = "https://example.com/s")
        val linkless = dated("n", at.minusSeconds(2)).copy(link = null)
        items.insertNew(source.id, listOf(video, story, linkless), at)

        val all = items.timeline(OWNER, null, TimelineFilter(true), 10)
        assertEquals(listOf(true, false, false), all.map { it.playable })
    }

    @Test
    fun `a hidden read item counts as listed only when it was read at or after the list was rendered`() {
        val source = sources.insert(OWNER, "http://a.test/f", "Src")
        val at = Instant.parse("2026-10-11T12:00:00Z")
        items.insertNew(
            source.id,
            listOf(dated("newer", at), dated("current", at.minusSeconds(60)), dated("older", at.minusSeconds(120))),
            at,
        )
        fun idOf(title: String): Long =
            jdbc.sql("SELECT id FROM items WHERE title = ?").param(title).query(Long::class.java).single()
        items.markRead(OWNER, idOf("older"), Instant.ofEpochMilli(100))
        items.markRead(OWNER, idOf("newer"), Instant.ofEpochMilli(300))
        val current = idOf("current")
        val publishedAt = at.minusSeconds(60)

        val seen = items.neighbours(OWNER, ListPosition(current, publishedAt), ListFilter(showRead = false, seen = 200))
        assertEquals("newer", seen.previous?.title)
        assertEquals(null, seen.next)

        val unseen = items.neighbours(OWNER, ListPosition(current, publishedAt), ListFilter(showRead = false))
        assertEquals(null, unseen.previous)
        assertEquals(null, unseen.next)

        val all = items.neighbours(OWNER, ListPosition(current, publishedAt), ListFilter(showRead = true))
        assertEquals("newer", all.previous?.title)
        assertEquals("older", all.next?.title)
    }

    @Test
    fun `marking read keeps the first read time and unknown items are reported`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        items.insertNew(source.id, listOf(item("k", "t")), Instant.EPOCH)
        val id = jdbc.sql("SELECT id FROM items").query(Long::class.java).single()

        assertEquals(true, items.markRead(OWNER, id, Instant.ofEpochMilli(100)))
        assertEquals(true, items.markRead(OWNER, id, Instant.ofEpochMilli(200)))
        assertEquals(false, items.markRead(OWNER, id + 1, Instant.ofEpochMilli(300)))

        assertEquals(100L, jdbc.sql("SELECT read_at FROM items").query(Long::class.java).single())
    }

    @Test
    fun `finding an item returns its link, title, source and thumbnail, or nothing when missing`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        items.insertNew(
            source.id,
            listOf(item("k", "t"), FeedItem("l", "u", "http://x.test/a", null, null, "http://x.test/a.jpg")),
            Instant.EPOCH,
        )
        val ids = jdbc.sql("SELECT id FROM items ORDER BY id").query(Long::class.java).list()

        assertEquals(ItemRef(ids[0], null, "t", "T", null), items.find(OWNER, ids[0]))
        assertEquals(ItemRef(ids[1], "http://x.test/a", "u", "T", "http://x.test/a.jpg"), items.find(OWNER, ids[1]))
        assertNull(items.find(OWNER, ids[1] + 1))
    }

    @Test
    fun `mark all read only covers unread items first seen at or before the cutoff`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        val cutoff = Instant.parse("2026-10-11T12:00:00Z")
        items.insertNew(source.id, listOf(item("old", "old")), cutoff.minusSeconds(1))
        items.insertNew(source.id, listOf(item("edge", "edge")), cutoff)
        items.insertNew(source.id, listOf(item("late", "late")), cutoff.plusSeconds(1))

        assertEquals(2, items.markAllRead(OWNER, cutoff, cutoff.plusSeconds(5), null))
        assertEquals(0, items.markAllRead(OWNER, cutoff, cutoff.plusSeconds(9), null))

        assertEquals(listOf("late"), items.timeline(OWNER, null, TimelineFilter(false), 10).map { it.title })
    }

    @Test
    fun `groups are unique ignoring case, listed alphabetically and renamed in place`() {
        val zebra = groups.insert(OWNER, "zebra")
        val apple = groups.insert(OWNER, "Apple")

        assertEquals(listOf("Apple", "zebra"), groups.findAll(OWNER).map { it.name })
        assertEquals(apple, groups.findByName(OWNER, "APPLE"))
        assertNull(groups.findByName(OWNER, "pear"))
        groups.rename(OWNER, zebra.id, "Mango")
        assertEquals(Group(zebra.id, "Mango"), groups.findById(OWNER, zebra.id))
        assertEquals(listOf("Apple", "Mango"), groups.findAll(OWNER).map { it.name })
        assertThrows(DataAccessException::class.java) { groups.insert(OWNER, "apple") }
    }

    @Test
    fun `a group filter scopes the timeline and mark all read, and deleting a group ungroups its sources`() {
        val music = groups.insert(OWNER, "Music")
        val alpha = sources.insert(OWNER, "http://a.test/f", "Alpha")
        val beta = sources.insert(OWNER, "http://b.test/f", "Beta")
        val seen = Instant.parse("2026-10-11T12:00:00Z")
        items.insertNew(alpha.id, listOf(dated("a", seen)), seen)
        items.insertNew(beta.id, listOf(dated("b", seen)), seen)
        sources.assignGroup(OWNER, alpha.id, music.id)

        assertEquals(music.id, sources.findById(alpha.id)?.groupId)
        assertNull(sources.findById(beta.id)?.groupId)
        assertEquals(listOf("a"), items.timeline(OWNER, null, TimelineFilter(false, music.id), 10).map { it.title })
        assertEquals(listOf(GroupSummary(music, 1)), groups.unreadCounts(OWNER))
        assertEquals(2, groups.totalUnread(OWNER))
        assertEquals(1, items.markAllRead(OWNER, seen, seen, music.id))
        assertEquals(listOf("b"), items.timeline(OWNER, null, TimelineFilter(false), 10).map { it.title })
        assertEquals(listOf(GroupSummary(music, 0)), groups.unreadCounts(OWNER))

        groups.delete(OWNER, music.id)

        assertNull(sources.findById(alpha.id)?.groupId)
        assertEquals(2, sources.findAll().size)
    }

    @Test
    fun `favouriting keeps the first favourite time, unfavouriting clears it and unknown items are reported`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        items.insertNew(source.id, listOf(item("k", "t")), Instant.EPOCH)
        val id = idOf("k")

        assertEquals(true, favourites.favourite(OWNER, id, Instant.ofEpochMilli(100)))
        assertEquals(true, favourites.favourite(OWNER, id, Instant.ofEpochMilli(200)))
        assertEquals(false, favourites.favourite(OWNER, id + 1, Instant.ofEpochMilli(300)))
        assertEquals(100L, favouritedAt(id))
        assertEquals(true, items.findPost(OWNER, id)!!.favourite)

        assertEquals(true, favourites.unfavourite(OWNER, id))
        assertEquals(true, favourites.unfavourite(OWNER, id))
        assertEquals(false, favourites.unfavourite(OWNER, id + 1))
        assertNull(favouritedAt(id))
        assertEquals(false, items.findPost(OWNER, id)!!.favourite)
    }

    @Test
    fun `the timeline flags favourites and leaves read state alone`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        items.insertNew(source.id, listOf(item("a", "a"), item("b", "b")), Instant.EPOCH)
        favourites.favourite(OWNER, idOf("b"), Instant.EPOCH)

        assertEquals(listOf(true, false), items.timeline(OWNER, null, TimelineFilter(true), 10).map { it.favourite })
        assertEquals(listOf(false, false), items.timeline(OWNER, null, TimelineFilter(true), 10).map { it.read })
    }

    @Test
    fun `favourites are listed newest first, read or not, and paged by cursor`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        val old = Instant.parse("2026-10-01T00:00:00Z")
        val mid = Instant.parse("2026-10-05T00:00:00Z")
        val new = Instant.parse("2026-10-09T00:00:00Z")
        items.insertNew(
            source.id,
            listOf(dated("old", old), dated("mid", mid), dated("new", new), dated("plain", mid)),
            Instant.EPOCH,
        )
        listOf("old", "mid", "new").forEach { favourites.favourite(OWNER, idOf(it), Instant.EPOCH) }
        items.markRead(OWNER, idOf("new"), Instant.EPOCH)

        assertEquals(listOf("new", "mid", "old"), favourites.favourites(OWNER, null, 10).map { it.title })
        assertEquals(listOf(true, false, false), favourites.favourites(OWNER, null, 10).map { it.read })
        assertEquals(listOf("mid"), favourites.favourites(OWNER, Cursor(new, idOf("new")), 1).map { it.title })
    }

    @Test
    fun `deleting by cutoff spares favourites until they are unfavourited`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        val cutoff = Instant.parse("2026-10-11T12:00:00Z")
        items.insertNew(source.id, listOf(item("kept", "kept"), item("gone", "gone")), cutoff.minusSeconds(1))
        favourites.favourite(OWNER, idOf("kept"), Instant.EPOCH)

        assertEquals(1, items.deleteFirstSeenBefore(cutoff))
        assertEquals(listOf("kept"), keys())

        favourites.unfavourite(OWNER, idOf("kept"))
        assertEquals(1, items.deleteFirstSeenBefore(cutoff))
        assertEquals(emptyList<String>(), keys())
    }

    @Test
    fun `a refetch of a feed keeps the favourite time of an item it already stored`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        items.insertNew(source.id, listOf(item("k", "t")), Instant.EPOCH)
        favourites.favourite(OWNER, idOf("k"), Instant.ofEpochMilli(100))

        items.insertNew(source.id, listOf(item("k", "t"), item("new", "n")), Instant.ofEpochMilli(500))

        assertEquals(100L, favouritedAt(idOf("k")))
        assertNull(favouritedAt(idOf("new")))
    }

    @Test
    fun `saving keeps the first saved time, clearing clears it and unknown items are reported`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        items.insertNew(source.id, listOf(item("k", "t")), Instant.EPOCH)
        val id = idOf("k")

        assertEquals(true, later.save(OWNER, id, Instant.ofEpochMilli(100)))
        assertEquals(true, later.save(OWNER, id, Instant.ofEpochMilli(200)))
        assertEquals(false, later.save(OWNER, id + 1, Instant.ofEpochMilli(300)))
        assertEquals(100L, savedAt(id))
        assertEquals(true, items.findPost(OWNER, id)!!.later)
        assertEquals(true, items.findDetail(OWNER, id)!!.later)

        assertEquals(true, later.clear(OWNER, id))
        assertEquals(true, later.clear(OWNER, id))
        assertEquals(false, later.clear(OWNER, id + 1))
        assertNull(savedAt(id))
        assertEquals(false, items.findPost(OWNER, id)!!.later)
    }

    @Test
    fun `the timeline flags saved items and leaves favourites and read state alone`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        items.insertNew(source.id, listOf(item("a", "a"), item("b", "b")), Instant.EPOCH)
        later.save(OWNER, idOf("b"), Instant.EPOCH)

        val rows = items.timeline(OWNER, null, TimelineFilter(true), 10).associateBy { it.title }
        assertEquals(false, rows.getValue("a").later)
        assertEquals(true, rows.getValue("b").later)
        assertEquals(false, rows.getValue("b").favourite)
        assertEquals(false, rows.getValue("b").read)
    }

    @Test
    fun `saved items are listed earliest saved first, ties by id, read or not, and paged by cursor`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        items.insertNew(source.id, listOf(item("a", "a"), item("b", "b"), item("c", "c")), Instant.EPOCH)
        later.save(OWNER, idOf("c"), Instant.ofEpochMilli(200))
        later.save(OWNER, idOf("a"), Instant.ofEpochMilli(100))
        later.save(OWNER, idOf("b"), Instant.ofEpochMilli(200))
        items.markRead(OWNER, idOf("a"), Instant.EPOCH)

        val all = later.later(OWNER, null, 10)
        assertEquals(listOf("a", "b", "c"), all.map { it.item.title })
        assertEquals(listOf(100L, 200L, 200L), all.map { it.savedAt.toEpochMilli() })
        assertEquals(listOf(true, false, false), all.map { it.item.read })
        val afterB = LaterCursor(Instant.ofEpochMilli(200), idOf("b"))
        assertEquals(listOf("c"), later.later(OWNER, afterB, 10).map { it.item.title })
    }

    @Test
    fun `deleting by cutoff spares saved items until they are cleared`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        val cutoff = Instant.parse("2026-10-11T12:00:00Z")
        items.insertNew(source.id, listOf(item("kept", "kept"), item("gone", "gone")), cutoff.minusSeconds(1))
        later.save(OWNER, idOf("kept"), Instant.EPOCH)

        assertEquals(1, items.deleteFirstSeenBefore(cutoff))
        assertEquals(listOf("kept"), keys())

        later.clear(OWNER, idOf("kept"))
        assertEquals(1, items.deleteFirstSeenBefore(cutoff))
        assertEquals(emptyList<String>(), keys())
    }

    @Test
    fun `a refetch of a feed keeps the saved time of an item it already stored`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        items.insertNew(source.id, listOf(item("k", "t")), Instant.EPOCH)
        later.save(OWNER, idOf("k"), Instant.ofEpochMilli(100))

        items.insertNew(source.id, listOf(item("k", "t")), Instant.ofEpochMilli(500))

        assertEquals(100L, savedAt(idOf("k")))
    }

    private fun savedAt(id: Long): Long? =
        jdbc
            .sql("SELECT later_at FROM items WHERE id = ?")
            .param(id)
            .query { rs, _ -> rs.getObject(1)?.let { (it as Number).toLong() } }
            .list()
            .single()

    private fun idOf(key: String): Long =
        jdbc
            .sql("SELECT id FROM items WHERE item_key = ?")
            .param(key)
            .query(Long::class.java)
            .single()

    private fun favouritedAt(id: Long): Long? =
        jdbc
            .sql("SELECT favourited_at FROM items WHERE id = ?")
            .param(id)
            .query { rs, _ -> rs.getObject(1)?.let { (it as Number).toLong() } }
            .list()
            .single()

    private fun keys(): List<String> = jdbc.sql("SELECT item_key FROM items").query(String::class.java).list()

    private fun dated(
        key: String,
        at: Instant,
    ) = FeedItem(key, key, null, at, null, null)

    private fun item(
        key: String,
        title: String,
    ) = FeedItem(key, title, null, null, null, null)

    private fun pictured(
        key: String,
        thumbnail: String,
    ) = FeedItem(key, key, null, null, null, thumbnail)

    @Test
    fun `finding a detail returns the feed data of an item and nothing when missing`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        val published = Instant.parse("2026-03-04T05:06:07Z")
        val link = "https://soundcloud.com/a/t"
        val feed = FeedItem("t", "Track", link, published, "Artist", "https://img.test/t.jpg", "<p>b</p>")
        items.insertNew(source.id, listOf(feed), Instant.EPOCH)
        val id = jdbc.sql("SELECT id FROM items").query(Long::class.java).single()

        val thumbnail = "https://img.test/t.jpg"
        val expected =
            DetailRef(
                id,
                link,
                "Track",
                "T",
                published,
                "Artist",
                thumbnail,
                "<p>b</p>",
                null,
                sourceUrl = "http://a.test/f",
            )
        assertEquals(expected, items.findDetail(OWNER, id))
        assertNull(items.findDetail(OWNER, id + 1))
    }

    @Test
    fun `a stored oembed is read back with the detail and replaces the earlier one`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        items.insertNew(source.id, listOf(item("t", "Track")), Instant.EPOCH)
        val id = jdbc.sql("SELECT id FROM items").query(Long::class.java).single()

        items.saveOembed(id, Oembed("First", null, "<p>one</p>"))
        items.saveOembed(id, Oembed("Second", "https://i.test/a.jpg", null))

        assertEquals(Oembed("Second", "https://i.test/a.jpg", null), items.findDetail(OWNER, id)?.oembed)
    }

    @Test
    fun `an item with no stored oembed has none in its detail`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        items.insertNew(source.id, listOf(item("t", "Track")), Instant.EPOCH)
        val id = jdbc.sql("SELECT id FROM items").query(Long::class.java).single()

        assertNull(items.findDetail(OWNER, id)?.oembed)
    }

    private fun withBody(
        key: String,
        body: String,
    ) = FeedItem(key, key, null, null, null, null, body)

    @Test
    fun `two owners can hold the same url but only their own sources are listed, found by url or removed`() {
        val mine = sources.insert(OWNER, "http://a.test/f", "Mine")
        val theirs = sources.insert(OTHER, "http://a.test/f", "Theirs")

        assertEquals(listOf(mine.id), sources.findAllOf(OWNER).map { it.id })
        assertEquals(listOf(theirs.id), sources.findAllOf(OTHER).map { it.id })
        assertEquals(listOf(mine.id, theirs.id), sources.findAll().map { it.id })
        assertEquals(true, sources.existsByUrl(OTHER, "http://a.test/f"))
        assertThrows(DataAccessException::class.java) { sources.insert(OWNER, "http://a.test/f", "Twice") }

        assertEquals(false, sources.delete(OWNER, theirs.id))
        assertEquals(2, sources.findAll().size)
        assertEquals(true, sources.delete(OWNER, mine.id))
        assertEquals(listOf(theirs.id), sources.findAll().map { it.id })
    }

    @Test
    fun `a url is only known to the owner who holds it`() {
        sources.insert(OTHER, "http://a.test/theirs", "Theirs")

        assertEquals(false, sources.existsByUrl(OWNER, "http://a.test/theirs"))
        assertEquals(false, sources.existsByUrl(OTHER, "http://a.test/unknown"))
    }

    @Test
    fun `a source cannot be put in another owner's group nor moved by another owner`() {
        val mine = sources.insert(OWNER, "http://a.test/f", "Mine")
        val theirs = groups.insert(OTHER, "Theirs")
        val own = groups.insert(OWNER, "Own")

        assertEquals(false, sources.assignGroup(OWNER, mine.id, theirs.id))
        assertEquals(false, sources.assignGroup(OTHER, mine.id, null))
        assertNull(sources.findById(mine.id)?.groupId)
        assertEquals(true, sources.assignGroup(OWNER, mine.id, own.id))
        assertEquals(own.id, sources.findById(mine.id)?.groupId)
        assertEquals(true, sources.assignGroup(OWNER, mine.id, null))
        assertNull(sources.findById(mine.id)?.groupId)
    }

    @Test
    fun `groups are per owner with the same name twice, scoped reads, writes and counts`() {
        val mine = groups.insert(OWNER, "Music")
        val theirs = groups.insert(OTHER, "music")
        val sourceOfTheirs = sources.insert(OTHER, "http://t.test/f", "T")
        items.insertNew(sourceOfTheirs.id, listOf(item("t", "t")), Instant.EPOCH)
        sources.assignGroup(OTHER, sourceOfTheirs.id, theirs.id)

        assertEquals(listOf(mine), groups.findAll(OWNER))
        assertNull(groups.findById(OWNER, theirs.id))
        assertNull(groups.findByName(OTHER, "MUSIC")?.takeIf { it.id == mine.id })
        assertEquals(theirs, groups.findByName(OTHER, "MUSIC"))
        groups.rename(OWNER, theirs.id, "Hijacked")
        assertEquals("music", groups.findById(OTHER, theirs.id)?.name)
        assertEquals(false, groups.delete(OWNER, theirs.id))
        assertEquals(listOf(GroupSummary(mine, 0)), groups.unreadCounts(OWNER))
        assertEquals(listOf(GroupSummary(theirs, 1)), groups.unreadCounts(OTHER))
        assertEquals(0, groups.totalUnread(OWNER))
        assertEquals(1, groups.totalUnread(OTHER))
        assertEquals(true, groups.delete(OTHER, theirs.id))
        assertEquals(listOf(mine), groups.findAll(OWNER))
    }

    @Test
    fun `items of one owner are invisible and untouchable to another`() {
        val mine = sources.insert(OWNER, "http://a.test/f", "Mine")
        val theirs = sources.insert(OTHER, "http://a.test/f", "Theirs")
        items.insertNew(mine.id, listOf(item("k", "same")), Instant.EPOCH)
        items.insertNew(theirs.id, listOf(item("k", "same")), Instant.EPOCH)
        val mineId =
            jdbc
                .sql("SELECT id FROM items WHERE source_id = ?")
                .param(mine.id)
                .query(Long::class.java)
                .single()
        val theirsId = mineId + 1

        assertEquals(listOf(mineId), items.timeline(OWNER, null, TimelineFilter(true), 10).map { it.id })
        assertEquals(listOf(theirsId), items.timeline(OTHER, null, TimelineFilter(true), 10).map { it.id })
        assertNull(items.find(OWNER, theirsId))
        assertNull(items.findPost(OWNER, theirsId))
        assertNull(items.findDetail(OWNER, theirsId))
        assertEquals(false, items.markRead(OWNER, theirsId, Instant.EPOCH))
        assertEquals(false, favourites.favourite(OWNER, theirsId, Instant.EPOCH))
        assertEquals(false, favourites.unfavourite(OWNER, theirsId))
        assertEquals(false, later.save(OWNER, theirsId, Instant.EPOCH))
        assertEquals(false, later.clear(OWNER, theirsId))
        assertEquals(1, items.markAllRead(OWNER, Instant.EPOCH, Instant.EPOCH, null))
        assertEquals(listOf(theirsId), items.timeline(OTHER, null, TimelineFilter(false), 10).map { it.id })
    }

    @Test
    fun `favourites and saved items are listed per owner`() {
        val mine = sources.insert(OWNER, "http://a.test/f", "Mine")
        val theirs = sources.insert(OTHER, "http://b.test/f", "Theirs")
        items.insertNew(mine.id, listOf(item("m", "m")), Instant.EPOCH)
        items.insertNew(theirs.id, listOf(item("t", "t")), Instant.EPOCH)
        listOf("m" to OWNER, "t" to OTHER).forEach { (key, owner) ->
            favourites.favourite(owner, idOf(key), Instant.EPOCH)
            later.save(owner, idOf(key), Instant.EPOCH)
        }

        assertEquals(listOf("m"), favourites.favourites(OWNER, null, 10).map { it.title })
        assertEquals(listOf("t"), favourites.favourites(OTHER, null, 10).map { it.title })
        assertEquals(listOf("m"), later.later(OWNER, null, 10).map { it.item.title })
        assertEquals(listOf("t"), later.later(OTHER, null, 10).map { it.item.title })
    }
}
