package dev.starve.infra

import dev.starve.domain.PollInterval
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.jdbc.core.simple.JdbcClient
import java.nio.file.Path
import javax.sql.DataSource

class JdbcSourceScheduleTest {
    @TempDir
    lateinit var tmp: Path

    private lateinit var dataSource: DataSource
    private lateinit var jdbc: JdbcClient
    private lateinit var sources: JdbcSourceRepository
    private lateinit var schedule: JdbcSourceSchedule

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
        schedule = JdbcSourceSchedule(jdbc)
    }

    @AfterEach
    fun tearDown() = (dataSource as AutoCloseable).close()

    @Test
    fun `a new source is on the default until an interval is set, and can go back to it`() {
        val source = sources.insert(OWNER, "http://a.test/f", "A")
        assertNull(sources.findById(source.id)!!.pollInterval)

        assertTrue(schedule.setInterval(OWNER, source.id, PollInterval.SIX_HOURS))
        assertEquals(PollInterval.SIX_HOURS, sources.findById(source.id)!!.pollInterval)
        assertTrue(schedule.setInterval(OWNER, source.id, null))
        assertNull(sources.findById(source.id)!!.pollInterval)
    }

    @Test
    fun `another user's source, or none, is left alone`() {
        val source = sources.insert(OWNER, "http://a.test/f", "A")

        assertFalse(schedule.setInterval(OTHER, source.id, PollInterval.DAILY))
        assertFalse(schedule.setInterval(OWNER, source.id + 1, PollInterval.DAILY))
        assertNull(sources.findById(source.id)!!.pollInterval)
    }

    @Test
    fun `stored minutes that are no longer offered read as the default`() {
        val source = sources.insert(OWNER, "http://a.test/f", "A")
        jdbc.sql("UPDATE sources SET poll_interval_minutes = 45 WHERE id = ?").param(source.id).update()

        assertNull(sources.findById(source.id)!!.pollInterval)
    }
}
