package dev.starve.infra

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.jdbc.core.simple.JdbcClient
import java.nio.file.Path
import javax.sql.DataSource

class FeedOnlyHostsMigrationTest {
    @TempDir
    lateinit var tmp: Path

    private lateinit var dataSource: DataSource
    private lateinit var jdbc: JdbcClient

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConfig().dataSource(tmp.resolve("m.db").toString())
        jdbc = JdbcClient.create(dataSource)
        flyway("18").migrate()
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
        link: String,
        checkedAt: Long?,
    ) {
        jdbc
            .sql(
                "INSERT INTO items (id, source_id, item_key, title, link, body, thumbnail_url, " +
                    "article_checked_at, published_at, first_seen_at) " +
                    "VALUES (?, 1, ?, 'Post', ?, '<p>Permalink</p>', 'avatar.jpg', ?, 0, 0)",
            ).params(id, "k$id", link, checkedAt)
            .update()
        jdbc
            .sql("INSERT INTO item_search (rowid, title, source, body) VALUES (?, 'Post', 'F', 'Permalink')")
            .param(id)
            .update()
    }

    private fun row(id: Int): List<Any?> =
        jdbc
            .sql(
                "SELECT i.body, i.thumbnail_url, i.article_checked_at, s.body AS indexed FROM items i " +
                    "JOIN item_search s ON s.rowid = i.id WHERE i.id = ?",
            ).param(id)
            .query { rs, _ -> listOf(rs.getString(1), rs.getString(2), rs.getObject(3), rs.getString(4)) }
            .single()

    @Test
    fun `checked items on the default feed-only hosts lose their fetched body, thumbnail and check`() {
        insert(1, "https://github.com/torvalds/linux/commit/abc", 5)
        insert(2, "https://WWW.GitHub.com/SwiftFaze/Starve/releases/tag/v1", 5)
        insert(3, "https://bsky.app/profile/bsky.app/post/3abc", 5)
        insert(4, "http://github.com/a/b", 5)

        flyway("19").migrate()

        (1..4).forEach { assertEquals(listOf(null, null, null, ""), row(it), "item $it") }
    }

    @Test
    fun `unchecked items and items on other hosts are left alone`() {
        insert(1, "https://github.com/torvalds/linux/commit/abc", null)
        insert(2, "https://blog.example/github.com/post", 5)
        insert(3, "https://gist.github.com/a", 5)

        flyway("19").migrate()

        assertEquals(listOf("<p>Permalink</p>", "avatar.jpg", null, "Permalink"), row(1))
        assertEquals(listOf("<p>Permalink</p>", "avatar.jpg", 5, "Permalink"), row(2))
        assertEquals(listOf("<p>Permalink</p>", "avatar.jpg", 5, "Permalink"), row(3))
    }
}
