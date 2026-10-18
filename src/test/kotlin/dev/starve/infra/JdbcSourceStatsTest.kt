package dev.starve.infra

import dev.starve.domain.FeedItem
import dev.starve.domain.ReadMark
import dev.starve.domain.SourceActivity
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.jdbc.core.simple.JdbcClient
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import javax.sql.DataSource

class JdbcSourceStatsTest {
    @TempDir
    lateinit var tmp: Path

    private lateinit var dataSource: DataSource
    private lateinit var jdbc: JdbcClient
    private lateinit var sources: JdbcSourceRepository
    private lateinit var items: JdbcItemRepository
    private lateinit var stats: JdbcSourceStats

    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private val since = now.minus(Duration.ofDays(1))
    private lateinit var migratedAt: Instant

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConfig().dataSource(tmp.resolve("t.db").toString())
        val before = Instant.now().minusSeconds(1)
        Flyway
            .configure()
            .dataSource(dataSource)
            .javaMigrations(SearchIndexMigration())
            .load()
            .migrate()
        jdbc = JdbcClient.create(dataSource)
        migratedAt = Instant.ofEpochMilli(trackedSince())
        assertTrue(!migratedAt.isBefore(before) && !migratedAt.isAfter(Instant.now()), "V16 stamped $migratedAt")
        jdbc.seedOwners()
        jdbc
            .sql("UPDATE app_metadata SET value = ? WHERE key = 'opens_tracked_since'")
            .param(since.toEpochMilli().toString())
            .update()
        sources = JdbcSourceRepository(jdbc, SearchIndex(jdbc))
        items = JdbcItemRepository(jdbc, SearchIndex(jdbc))
        stats = JdbcSourceStats(jdbc)
    }

    @AfterEach
    fun tearDown() = (dataSource as AutoCloseable).close()

    @Test
    fun `a source's counts come from its own items, within the window and since tracking began`() {
        val source = sources.insert(OWNER, "http://a.test/f", "A")
        val recent = item("new", now.minus(Duration.ofDays(2)))
        items.insertNew(source.id, listOf(recent, item("old", now.minus(Duration.ofDays(40)))), now)
        items.insertNew(source.id, listOf(item("future", now.plusSeconds(60))), now)
        items.insertNew(source.id, (1..3).map { item("read$it", now.minus(Duration.ofDays(3))) }, now)
        markRead("read1", now)
        markRead("read2", since)
        markRead("read3", since.minusMillis(1))
        items.markRead(OWNER, id("read1"), ReadMark.opened(now))

        val activity = stats.activity(OWNER, now.minus(Duration.ofDays(28)), now).getValue(source.id)

        assertEquals(SourceActivity(now.plusSeconds(60), 4, 3, 2, 1), activity)
    }

    @Test
    fun `the window excludes its start and includes now`() {
        val source = sources.insert(OWNER, "http://a.test/f", "A")
        val start = now.minus(Duration.ofDays(28))
        items.insertNew(source.id, listOf(item("edge", start), item("now", now)), now)

        assertEquals(1, stats.activity(OWNER, start, now).getValue(source.id).recentPosts)
    }

    @Test
    fun `a source without items has no row, and another user's sources are not counted`() {
        val empty = sources.insert(OWNER, "http://a.test/f", "A")
        val theirs = sources.insert(OTHER, "http://b.test/f", "B")
        items.insertNew(theirs.id, listOf(item("x", now)), now)

        val activity = stats.activity(OWNER, now.minus(Duration.ofDays(28)), now)

        assertNull(activity[empty.id])
        assertNull(activity[theirs.id])
    }

    @Test
    fun `an open is stamped once, only on the owner's item, and a skip never stamps or clears it`() {
        val source = sources.insert(OWNER, "http://a.test/f", "A")
        items.insertNew(source.id, listOf(item("x", now), item("y", now)), now)

        items.markRead(OTHER, id("x"), ReadMark.opened(now))
        items.markRead(OWNER, id("y"), ReadMark.skipped(now))
        assertNull(openedAt("x"))
        assertNull(openedAt("y"))
        items.markRead(OWNER, id("x"), ReadMark.opened(now))
        items.markRead(OWNER, id("x"), ReadMark.opened(now.plusSeconds(5)))
        items.markRead(OWNER, id("x"), ReadMark.skipped(now.plusSeconds(9)))
        items.markRead(OWNER, id("y"), ReadMark.opened(now.plusSeconds(7)))

        assertEquals(now.toEpochMilli(), openedAt("x"))
        assertEquals(now.plusSeconds(7).toEpochMilli(), openedAt("y"))
    }

    private fun trackedSince(): Long =
        jdbc
            .sql("SELECT value FROM app_metadata WHERE key = 'opens_tracked_since'")
            .query(String::class.java)
            .single()
            .toLong()

    private fun item(
        key: String,
        published: Instant,
    ) = FeedItem(key, key, null, published, null, null)

    private fun id(title: String): Long =
        jdbc
            .sql("SELECT id FROM items WHERE title = ?")
            .param(title)
            .query(Long::class.java)
            .single()

    private fun markRead(
        title: String,
        at: Instant,
    ) {
        jdbc.sql("UPDATE items SET read_at = ? WHERE title = ?").params(at.toEpochMilli(), title).update()
    }

    private fun openedAt(title: String): Long? =
        jdbc
            .sql("SELECT opened_at FROM items WHERE title = ?")
            .param(title)
            .query { rs, _ -> rs.getLong(1).takeUnless { rs.wasNull() } }
            .list()
            .single()
}
