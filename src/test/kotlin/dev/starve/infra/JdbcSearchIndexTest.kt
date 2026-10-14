package dev.starve.infra

import dev.starve.domain.Cursor
import dev.starve.domain.FeedItem
import dev.starve.domain.SearchRules
import dev.starve.domain.Validators
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.jdbc.core.simple.JdbcClient
import java.nio.file.Path
import java.time.Instant
import javax.sql.DataSource

class JdbcSearchIndexTest {
    @TempDir
    lateinit var tmp: Path

    private lateinit var dataSource: DataSource
    private lateinit var jdbc: JdbcClient
    private lateinit var sources: JdbcSourceRepository
    private lateinit var items: JdbcItemRepository
    private lateinit var searching: JdbcSearchRepository
    private lateinit var favourites: JdbcFavouriteRepository
    private val now = Instant.parse("2026-10-11T12:00:00Z")

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConfig().dataSource(tmp.resolve("search.db").toString())
        Flyway
            .configure()
            .dataSource(dataSource)
            .javaMigrations(SearchIndexMigration())
            .load()
            .migrate()
        jdbc = JdbcClient.create(dataSource)
        val index = SearchIndex(jdbc)
        sources = JdbcSourceRepository(jdbc, index)
        items = JdbcItemRepository(jdbc, index)
        searching = JdbcSearchRepository(jdbc)
        favourites = JdbcFavouriteRepository(jdbc)
    }

    @AfterEach
    fun tearDown() = (dataSource as AutoCloseable).close()

    private fun feedItem(
        key: String,
        title: String,
        body: String? = null,
        publishedAt: Instant? = now,
    ) = FeedItem(key, title, "http://a.test/$key", publishedAt, null, null, body)

    private fun search(text: String): List<String> =
        searching
            .search(SearchRules.matchExpression(text)!!, null, 100)
            .map { it.title }

    private fun indexRows(): Long = jdbc.sql("SELECT COUNT(*) FROM item_search").query(Long::class.java).single()

    @Test
    fun `an inserted item is searchable by title, source name and body text`() {
        val source = sources.insert("http://a.test/f", "Retro Weekly")
        items.insertNew(source.id, listOf(feedItem("k1", "Weekend post", "<p>All about <em>zeppelins</em></p>")), now)

        assertEquals(listOf("Weekend post"), search("weekend"))
        assertEquals(listOf("Weekend post"), search("retro"))
        assertEquals(listOf("Weekend post"), search("zeppelins"))
        assertEquals(emptyList<String>(), search("em"))
    }

    @Test
    fun `a body filled in by a later fetch is indexed once and then kept`() {
        val source = sources.insert("http://a.test/f", "Blog")
        items.insertNew(source.id, listOf(feedItem("k1", "Post")), now)
        items.insertNew(source.id, listOf(feedItem("k1", "Post", "<p>Second zeppelin</p>")), now)
        items.insertNew(source.id, listOf(feedItem("k1", "Post", "<p>Third dirigible</p>")), now)

        assertEquals(listOf("Post"), search("zeppelin"))
        assertEquals(emptyList<String>(), search("dirigible"))
        assertEquals(1L, indexRows())
    }

    @Test
    fun `renaming a source moves its items to the new name`() {
        val source = sources.insert("http://a.test/f", "Old Name")
        items.insertNew(source.id, listOf(feedItem("k1", "Episode")), now)

        sources.recordSuccess(source.id, "Brand New", Validators(), now)

        assertEquals(listOf("Episode"), search("brand"))
        assertEquals(emptyList<String>(), search("old"))
    }

    @Test
    fun `retention removes deleted items from the index`() {
        val source = sources.insert("http://a.test/f", "Daily")
        items.insertNew(source.id, listOf(feedItem("old", "Ancient")), now.minusSeconds(90L * 86400))
        items.insertNew(source.id, listOf(feedItem("new", "Fresh")), now)

        items.deleteFirstSeenBefore(now.minusSeconds(30L * 86400))

        assertEquals(1L, indexRows())
        assertEquals(emptyList<String>(), search("ancient"))
    }

    @Test
    fun `retention keeps a favourite searchable and drops an unstarred item of the same age`() {
        val source = sources.insert("http://a.test/f", "Daily")
        val old = now.minusSeconds(90L * 86400)
        items.insertNew(source.id, listOf(feedItem("kept", "Treasured"), feedItem("lost", "Forgotten")), old)
        val keptId = searching.search(SearchRules.matchExpression("treasured")!!, null, 10).single().id
        favourites.favourite(keptId, now)

        items.deleteFirstSeenBefore(now.minusSeconds(30L * 86400))

        assertEquals(listOf("Treasured"), search("treasured"))
        assertEquals(emptyList<String>(), search("forgotten"))
    }

    @Test
    fun `removing a source removes its items from the index`() {
        val gone = sources.insert("http://g.test/f", "Gone")
        val kept = sources.insert("http://k.test/f", "Kept")
        items.insertNew(gone.id, listOf(feedItem("g", "Match here")), now)
        items.insertNew(kept.id, listOf(feedItem("k", "Match there")), now)

        sources.delete(gone.id)

        assertEquals(1L, indexRows())
        assertEquals(listOf("Match there"), search("match"))
    }

    @Test
    fun `search returns matches newest first with the page cursor applied`() {
        val source = sources.insert("http://a.test/f", "Alpha")
        items.insertNew(source.id, listOf(feedItem("old", "Match old", publishedAt = now.minusSeconds(3600))), now)
        items.insertNew(source.id, listOf(feedItem("new", "Match new", publishedAt = now)), now)
        items.insertNew(source.id, listOf(feedItem("other", "Unrelated", publishedAt = now)), now)

        val all = searching.search(SearchRules.matchExpression("match")!!, null, 10)
        val afterNew = searching.search(SearchRules.matchExpression("match")!!, Cursor(now, all.first().id), 10)

        assertEquals(listOf("Match new", "Match old"), all.map { it.title })
        assertEquals(listOf("Match old"), afterNew.map { it.title })
    }
}
