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

class DecodeItemTitlesMigrationTest {
    @TempDir
    lateinit var tmp: Path

    private lateinit var dataSource: DataSource
    private lateinit var jdbc: JdbcClient

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConfig().dataSource(tmp.resolve("m.db").toString())
        jdbc = JdbcClient.create(dataSource)
        flyway("4").migrate()
        jdbc.sql("INSERT INTO sources (id, url, title) VALUES (1, 'http://x.test/f', 'F')").update()
    }

    @AfterEach
    fun tearDown() = (dataSource as AutoCloseable).close()

    private fun flyway(target: String) =
        Flyway
            .configure()
            .dataSource(dataSource)
            .javaMigrations(DecodeItemTitlesMigration())
            .target(target)
            .load()

    private fun insert(
        id: Int,
        title: String,
    ) = jdbc
        .sql(
            "INSERT INTO items (id, source_id, item_key, title, published_at, first_seen_at) " +
                "VALUES (?, 1, ?, ?, 0, 0)",
        ).params(id, "k$id", title)
        .update()

    private fun titleOf(id: Int) =
        jdbc
            .sql("SELECT title FROM items WHERE id = ?")
            .param(id)
            .query(String::class.java)
            .single()

    @Test
    fun `stored titles with references are decoded and other titles are left alone`() {
        insert(1, "There&#8217;s &amp; more")
        insert(2, "Plain")
        insert(3, "Tom & Jerry &notareal;")

        flyway("5").migrate()

        assertEquals("There’s & more", titleOf(1))
        assertEquals("Plain", titleOf(2))
        assertEquals("Tom & Jerry &notareal;", titleOf(3))
    }

    @Test
    fun `it is version 5 and runs once`() {
        insert(1, "a &amp;amp; b")

        flyway("5").migrate()
        flyway("5").migrate()

        assertEquals("a &amp; b", titleOf(1))
        assertEquals("5", DecodeItemTitlesMigration().version.version)
    }

    @Test
    fun `it is described, run inside a transaction and checksummed by version alone`() {
        val migration = DecodeItemTitlesMigration()

        assertEquals("decode html entities in item titles", migration.description)
        assertTrue(migration.canExecuteInTransaction())
        assertNull(migration.checksum)
    }
}
