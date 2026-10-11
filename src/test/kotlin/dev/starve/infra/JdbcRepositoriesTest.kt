package dev.starve.infra

import dev.starve.domain.FeedItem
import dev.starve.domain.Validators
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
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

    private fun item(
        key: String,
        title: String,
    ) = FeedItem(key, title, null, null, null, null)
}
