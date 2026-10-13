package dev.starve.infra

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.jdbc.core.simple.JdbcClient
import java.nio.file.Path
import javax.sql.DataSource

class SearchIndexMigrationTest {
    @TempDir
    lateinit var tmp: Path

    private lateinit var dataSource: DataSource
    private lateinit var jdbc: JdbcClient

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConfig().dataSource(tmp.resolve("s.db").toString())
        jdbc = JdbcClient.create(dataSource)
        flyway("6").migrate()
        jdbc.sql("INSERT INTO sources (id, url, title) VALUES (1, 'http://x.test/f', 'Retro Weekly')").update()
        jdbc
            .sql(
                "INSERT INTO items (id, source_id, item_key, title, body, published_at, first_seen_at) " +
                    "VALUES (1, 1, 'a', 'Weekend post', '<p>Ancient <a href=\"https://x.test\" " +
                    "class=\"fancy\">zeppelin</a> lore</p>', 0, 0)",
            ).update()
        jdbc
            .sql(
                "INSERT INTO items (id, source_id, item_key, title, published_at, first_seen_at) " +
                    "VALUES (2, 1, 'b', 'Bodiless', 0, 0)",
            ).update()
    }

    @AfterEach
    fun tearDown() = (dataSource as AutoCloseable).close()

    private fun flyway(target: String) =
        Flyway
            .configure()
            .dataSource(dataSource)
            .javaMigrations(DecodeItemTitlesMigration(), SearchIndexMigration())
            .target(target)
            .load()

    private fun matches(expression: String): List<Long> =
        jdbc
            .sql("SELECT rowid FROM item_search WHERE item_search MATCH ? ORDER BY rowid")
            .param(expression)
            .query(Long::class.java)
            .list()

    @Test
    fun `existing items are back-filled with their title, source and body text`() {
        flyway("10").migrate()

        assertEquals(listOf(1L), matches("\"zeppelin\""))
        assertEquals(listOf(1L, 2L), matches("\"retro\""))
        assertEquals(listOf(2L), matches("\"bodiless\""))
    }

    @Test
    fun `markup in a stored body is not indexed`() {
        flyway("10").migrate()

        assertEquals(emptyList<Long>(), matches("\"href\""))
        assertEquals(emptyList<Long>(), matches("\"fancy\""))
    }

    @Test
    fun `it is version 10 and described, runs in a transaction and is checksummed by version alone`() {
        val migration = SearchIndexMigration()

        assertEquals("10", migration.version.version)
        assertEquals("index titles, sources and post bodies for full-text search", migration.description)
        assertTrue(migration.canExecuteInTransaction())
        assertEquals(null, migration.checksum)
    }
}
