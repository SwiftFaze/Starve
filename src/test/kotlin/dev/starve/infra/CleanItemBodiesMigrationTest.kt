package dev.starve.infra

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
import javax.sql.DataSource

class CleanItemBodiesMigrationTest {
    @TempDir
    lateinit var tmp: Path

    private lateinit var dataSource: DataSource
    private lateinit var jdbc: JdbcClient

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConfig().dataSource(tmp.resolve("m.db").toString())
        jdbc = JdbcClient.create(dataSource)
        flyway("14").migrate()
        jdbc.sql("INSERT INTO users (id, username, password_hash, created_at) VALUES (1, 'rob', 'x', 0)").update()
        jdbc.sql("INSERT INTO sources (id, owner_id, url, title) VALUES (1, 1, 'http://x.test/f', 'F')").update()
    }

    @AfterEach
    fun tearDown() = (dataSource as AutoCloseable).close()

    private fun flyway(target: String) =
        Flyway
            .configure()
            .dataSource(dataSource)
            .javaMigrations(DecodeItemTitlesMigration(), SearchIndexMigration(), CleanItemBodiesMigration())
            .target(target)
            .load()

    private fun insert(
        id: Int,
        title: String,
        body: String?,
    ) = jdbc
        .sql(
            "INSERT INTO items (id, source_id, item_key, title, link, body, published_at, first_seen_at) " +
                "VALUES (?, 1, ?, ?, 'https://x.test/post', ?, 0, 0)",
        ).params(id, "k$id", title, body)
        .update()

    private fun bodyOf(id: Int) =
        jdbc
            .sql("SELECT body FROM items WHERE id = ?")
            .param(id)
            .query(String::class.java)
            .optional()
            .orElse(null)

    @Test
    fun `heading permalink marks are removed from stored bodies`() {
        insert(1, "Post", "<h2>Workshop<a href=\"https://x.test/post#workshop\">#</a></h2><p>Text</p>")

        flyway("15").migrate()

        assertEquals("<h2>Workshop</h2><p>Text</p>", bodyOf(1))
    }

    @Test
    fun `a leading h1 that repeats the title is dropped, any other is kept`() {
        insert(1, "Celebrating 70 years", "<h1>Celebrating 70 Years</h1><p>Text</p>")
        insert(2, "Post", "<h1>A different heading</h1><p>Text</p>")
        insert(3, "Post", "<p>Intro</p><h1>Post</h1><p>Text</p>")
        insert(4, "Celebrating 70 years", "<h1>Celebrating 70 years<a href=\"#c\">#</a></h1>\n <p>Text</p>")

        flyway("15").migrate()

        assertEquals("<p>Text</p>", bodyOf(1))
        assertEquals("<h1>A different heading</h1><p>Text</p>", bodyOf(2))
        assertEquals("<p>Intro</p><h1>Post</h1><p>Text</p>", bodyOf(3))
        assertEquals("<p>Text</p>", bodyOf(4))
    }

    @Test
    fun `clean bodies, missing bodies and bodies that would empty are left alone`() {
        insert(1, "Post", "<p>Already <b>clean</b></p>")
        insert(2, "Post", null)
        insert(3, "Post", "<h1>Post</h1>")

        flyway("15").migrate()

        assertEquals("<p>Already <b>clean</b></p>", bodyOf(1))
        assertNull(bodyOf(2))
        assertEquals("<h1>Post</h1>", bodyOf(3))
    }

    @Test
    fun `it is version 15, described, transactional and checksummed by version alone`() {
        val migration = CleanItemBodiesMigration()

        assertEquals("15", migration.version.version)
        assertEquals("clean stored post bodies again", migration.description)
        assertTrue(migration.canExecuteInTransaction())
        assertNull(migration.checksum)
    }
}
