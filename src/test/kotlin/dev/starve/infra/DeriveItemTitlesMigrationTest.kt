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

class DeriveItemTitlesMigrationTest {
    @TempDir
    lateinit var tmp: Path

    private lateinit var dataSource: DataSource
    private lateinit var jdbc: JdbcClient

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConfig().dataSource(tmp.resolve("m.db").toString())
        jdbc = JdbcClient.create(dataSource)
        flyway("19").migrate()
        jdbc.sql("INSERT INTO users (id, username, password_hash, created_at) VALUES (1, 'rob', 'x', 0)").update()
        jdbc.sql("INSERT INTO sources (id, owner_id, url, title) VALUES (1, 1, 'http://x.test/f', 'F')").update()
    }

    @AfterEach
    fun tearDown() = (dataSource as AutoCloseable).close()

    private fun flyway(target: String) =
        Flyway
            .configure()
            .dataSource(dataSource)
            .javaMigrations(
                DecodeItemTitlesMigration(),
                SearchIndexMigration(),
                CleanItemBodiesMigration(),
                DeriveItemTitlesMigration(),
            ).target(target)
            .load()

    private fun insert(
        id: Int,
        title: String,
        body: String?,
    ) {
        jdbc
            .sql(
                "INSERT INTO items (id, source_id, item_key, title, body, published_at, first_seen_at) " +
                    "VALUES (?, 1, ?, ?, ?, 0, 0)",
            ).params(id, "k$id", title, body)
            .update()
        jdbc
            .sql("INSERT INTO item_search (rowid, title, source, body) VALUES (?, ?, 'F', '')")
            .params(id, title)
            .update()
    }

    private fun titles(id: Int): Pair<String, String> =
        jdbc
            .sql("SELECT i.title, s.title FROM items i JOIN item_search s ON s.rowid = i.id WHERE i.id = ?")
            .param(id)
            .query { rs, _ -> rs.getString(1) to rs.getString(2) }
            .single()

    @Test
    fun `untitled items get a title from their text, in the item and its search row`() {
        insert(1, "(untitled)", "<p>Happy opening day of <b>hockey</b> season!</p>")
        insert(2, "(untitled)", "<p>Look [contains quote post or other embedded content]</p>")

        flyway("21").migrate()

        assertEquals("Happy opening day of hockey season!" to "Happy opening day of hockey season!", titles(1))
        assertEquals("Look" to "Look", titles(2))
    }

    @Test
    fun `titled items, and untitled ones without a body or text, are left alone`() {
        insert(1, "A title", "<p>Some text</p>")
        insert(2, "(untitled)", null)
        insert(3, "(untitled)", "<p>[image]</p>")

        flyway("21").migrate()

        assertEquals("A title" to "A title", titles(1))
        assertEquals("(untitled)" to "(untitled)", titles(2))
        assertEquals("(untitled)" to "(untitled)", titles(3))
    }

    @Test
    fun `it is V21, runs once in a transaction and says what it does`() {
        val migration = DeriveItemTitlesMigration()

        assertEquals("21", migration.version.version)
        assertEquals("derive titles of untitled items from their text", migration.description)
        assertNull(migration.checksum)
        assertTrue(migration.canExecuteInTransaction())
    }
}
