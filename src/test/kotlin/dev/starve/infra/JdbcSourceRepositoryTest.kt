package dev.starve.infra

import dev.starve.application.FetchSuccess
import dev.starve.application.TimelineFilter
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

/** A source's failure streak and its permanent moves. */
class JdbcSourceRepositoryTest {
    @TempDir
    lateinit var tmp: Path

    private lateinit var dataSource: DataSource
    private lateinit var sources: JdbcSourceRepository
    private lateinit var items: JdbcItemRepository

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConfig().dataSource(tmp.resolve("t.db").toString())
        Flyway
            .configure()
            .dataSource(dataSource)
            .javaMigrations(SearchIndexMigration())
            .load()
            .migrate()
        val jdbc = JdbcClient.create(dataSource)
        jdbc.seedOwners()
        sources = JdbcSourceRepository(jdbc, SearchIndex(jdbc))
        items = JdbcItemRepository(jdbc, SearchIndex(jdbc))
    }

    @AfterEach
    fun tearDown() = (dataSource as AutoCloseable).close()

    @Test
    fun `a failure streak starts at its first failure, and a success ends it`() {
        val source = sources.insert(OWNER, "http://a.test/f", "T")
        val first = Instant.parse("2026-10-11T12:00:00Z")
        assertNull(sources.findById(source.id)!!.failingSince)

        sources.recordFailure(source.id, "bad", first)
        sources.recordFailure(source.id, "worse", first.plusSeconds(3600))
        val failing = sources.findById(source.id)!!
        assertEquals(first, failing.failingSince)
        assertEquals("worse", failing.lastError)
        assertEquals(first.plusSeconds(3600), failing.lastFetchedAt)

        sources.recordSuccess(source.id, FetchSuccess("T", Validators(), first.plusSeconds(7200)))
        assertNull(sources.findById(source.id)!!.failingSince)
        sources.recordFailure(source.id, "again", first.plusSeconds(9000))
        assertEquals(first.plusSeconds(9000), sources.findById(source.id)!!.failingSince)
    }

    @Test
    fun `a move to a new URL rewrites the source and keeps its items`() {
        val source = sources.insert(OWNER, "http://a.test/old", "T")
        items.insertNew(source.id, listOf(FeedItem("k1", "k1", null, Instant.EPOCH, null, null)), Instant.EPOCH)

        sources.recordSuccess(source.id, FetchSuccess("T", Validators(), Instant.EPOCH, "http://a.test/new"))

        val moved = sources.findById(source.id)!!
        assertEquals("http://a.test/new", moved.url)
        assertNull(moved.movedTo)
        assertEquals(1, items.timeline(OWNER, null, TimelineFilter(true), 10).size)
    }

    @Test
    fun `a move to a URL the owner already follows is remembered, not adopted, until a plain success`() {
        val old = sources.insert(OWNER, "http://a.test/old", "Old")
        sources.insert(OWNER, "http://a.test/new", "New")
        val elsewhere = sources.insert(OTHER, "http://a.test/old", "Theirs")

        sources.recordSuccess(old.id, FetchSuccess("Old", Validators(), Instant.EPOCH, "http://a.test/new"))
        sources.recordSuccess(elsewhere.id, FetchSuccess("Theirs", Validators(), Instant.EPOCH, "http://a.test/new"))

        assertEquals("http://a.test/old", sources.findById(old.id)!!.url)
        assertEquals("http://a.test/new", sources.findById(old.id)!!.movedTo)
        assertEquals("http://a.test/new", sources.findById(elsewhere.id)!!.url)
        assertNull(sources.findById(elsewhere.id)!!.movedTo)

        sources.recordSuccess(old.id, FetchSuccess("Old", Validators(), Instant.EPOCH))
        assertNull(sources.findById(old.id)!!.movedTo)
    }
}
