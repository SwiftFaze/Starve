package dev.starve.infra

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.simple.JdbcClient
import java.nio.file.Path
import javax.sql.DataSource

/** V14: sources and groups gain a NOT NULL owner; rows that exist already go to the first account. */
class PerUserMigrationTest {
    @TempDir
    lateinit var tmp: Path

    private lateinit var dataSource: DataSource
    private lateinit var jdbc: JdbcClient

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConfig().dataSource(tmp.resolve("m.db").toString())
        jdbc = JdbcClient.create(dataSource)
        flyway("13").migrate()
        jdbc.seedOwners()
        jdbc.sql("INSERT INTO groups (id, name) VALUES (4, 'Music')").update()
        jdbc
            .sql(
                "INSERT INTO sources (id, url, title, group_id, favicon_url) " +
                    "VALUES (7, 'http://a.test/f', 'Blog', 4, 'http://a.test/i.png')",
            ).update()
        jdbc
            .sql(
                "INSERT INTO items (id, source_id, item_key, title, body, published_at, first_seen_at, read_at, " +
                    "favourited_at, later_at) VALUES (9, 7, 'k', 'Post', '<p>b</p>', 5, 6, 8, 10, 11)",
            ).update()
        jdbc.sql("INSERT INTO item_search (rowid, title, source, body) VALUES (9, 'Post', 'Blog', 'b')").update()
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

    private fun scalar(sql: String): String? =
        jdbc
            .sql(sql)
            .query(String::class.java)
            .optional()
            .orElse(null)

    @Test
    fun `existing rows keep their ids and links and go to the first account`() {
        flyway("14").migrate()

        assertEquals("1", scalar("SELECT owner_id FROM sources WHERE id = 7"))
        assertEquals("1", scalar("SELECT owner_id FROM groups WHERE id = 4"))
        assertEquals("4", scalar("SELECT group_id FROM sources WHERE id = 7"))
        assertEquals("http://a.test/i.png", scalar("SELECT favicon_url FROM sources WHERE id = 7"))
        assertEquals("7|k|Post|<p>b</p>|5|6|8|10|11", scalar(ITEM_ROW))
        assertEquals("9", scalar("SELECT rowid FROM item_search WHERE item_search MATCH 'post'"))
    }

    @Test
    fun `the rebuilt tables keep their foreign keys and ids keep counting upwards`() {
        flyway("14").migrate()

        jdbc.sql("INSERT INTO groups (owner_id, name) VALUES (1, 'News')").update()
        jdbc.sql("INSERT INTO sources (owner_id, url, title, group_id) VALUES (2, 'http://b.test/f', 'B', 5)").update()

        assertEquals("5", scalar("SELECT id FROM groups WHERE name = 'News'"))
        assertEquals("8", scalar("SELECT id FROM sources WHERE title = 'B'"))
        jdbc.sql("DELETE FROM groups WHERE id = 4").update()
        assertEquals(null, scalar("SELECT group_id FROM sources WHERE id = 7"))
        jdbc.sql("DELETE FROM sources WHERE id = 7").update()
        assertEquals("0", scalar("SELECT COUNT(*) FROM items"))
        assertEquals("0", scalar("SELECT COUNT(*) FROM item_search"))
    }

    @Test
    fun `an owner is required and urls and names are unique per owner`() {
        flyway("14").migrate()

        assertThrows(DataAccessException::class.java) {
            jdbc.sql("INSERT INTO sources (url, title) VALUES ('http://x.test', 'X')").update()
        }
        assertThrows(DataAccessException::class.java) {
            jdbc.sql("INSERT INTO groups (name) VALUES ('X')").update()
        }
        assertThrows(DataAccessException::class.java) {
            jdbc.sql("INSERT INTO sources (owner_id, url, title) VALUES (1, 'http://a.test/f', 'Again')").update()
        }
        assertThrows(DataAccessException::class.java) {
            jdbc.sql("INSERT INTO groups (owner_id, name) VALUES (1, 'MUSIC')").update()
        }
        jdbc.sql("INSERT INTO sources (owner_id, url, title) VALUES (2, 'http://a.test/f', 'Theirs')").update()
        jdbc.sql("INSERT INTO groups (owner_id, name) VALUES (2, 'music')").update()
    }

    @Test
    fun `deleting an account removes its sources, groups, items and index rows`() {
        flyway("14").migrate()

        jdbc.sql("DELETE FROM users WHERE id = 1").update()

        assertEquals("0", scalar("SELECT COUNT(*) FROM sources"))
        assertEquals("0", scalar("SELECT COUNT(*) FROM groups"))
        assertEquals("0", scalar("SELECT COUNT(*) FROM items"))
        assertEquals("0", scalar("SELECT COUNT(*) FROM item_search"))
    }

    private companion object {
        const val ITEM_ROW =
            "SELECT source_id || '|' || item_key || '|' || title || '|' || body || '|' || published_at || '|' || " +
                "first_seen_at || '|' || read_at || '|' || favourited_at || '|' || later_at FROM items WHERE id = 9"
    }
}
