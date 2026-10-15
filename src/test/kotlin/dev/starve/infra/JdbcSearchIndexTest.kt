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
    private lateinit var later: JdbcLaterRepository
    private lateinit var articles: JdbcArticleRepository
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
        jdbc.seedOwners()
        val index = SearchIndex(jdbc)
        sources = JdbcSourceRepository(jdbc, index)
        items = JdbcItemRepository(jdbc, index)
        searching = JdbcSearchRepository(jdbc)
        favourites = JdbcFavouriteRepository(jdbc)
        later = JdbcLaterRepository(jdbc)
        articles = JdbcArticleRepository(jdbc, index)
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
            .search(OWNER, SearchRules.matchExpression(text)!!, null, 100)
            .map { it.title }

    private fun indexRows(): Long = jdbc.sql("SELECT COUNT(*) FROM item_search").query(Long::class.java).single()

    private fun insertedTeaserId(): Long {
        val source = sources.insert(OWNER, "http://a.test/f", "Retro Weekly")
        items.insertNew(source.id, listOf(feedItem("k1", "Teaser post", "<p>Short teaser</p>")), now)
        return jdbc.sql("SELECT id FROM items").query(Long::class.java).single()
    }

    @Test
    fun `a fetched article body is searchable and the teaser words no longer match`() {
        val id = insertedTeaserId()

        articles.recordArticleCheck(id, "<p>Full text about <em>dirigibles</em></p>", null, now)

        assertEquals(listOf("Teaser post"), search("dirigibles"))
        assertEquals(emptyList<String>(), search("short"))
    }

    @Test
    fun `a check that stores no body leaves the old body words searchable`() {
        val id = insertedTeaserId()

        articles.recordArticleCheck(id, null, "http://a.test/og.jpg", now)

        assertEquals(listOf("Teaser post"), search("teaser"))
    }

    @Test
    fun `an inserted item is searchable by title, source name and body text`() {
        val source = sources.insert(OWNER, "http://a.test/f", "Retro Weekly")
        items.insertNew(source.id, listOf(feedItem("k1", "Weekend post", "<p>All about <em>zeppelins</em></p>")), now)

        assertEquals(listOf("Weekend post"), search("weekend"))
        assertEquals(listOf("Weekend post"), search("retro"))
        assertEquals(listOf("Weekend post"), search("zeppelins"))
        assertEquals(emptyList<String>(), search("em"))
    }

    @Test
    fun `a body filled in by a later fetch is indexed once and then kept`() {
        val source = sources.insert(OWNER, "http://a.test/f", "Blog")
        items.insertNew(source.id, listOf(feedItem("k1", "Post")), now)
        items.insertNew(source.id, listOf(feedItem("k1", "Post", "<p>Second zeppelin</p>")), now)
        items.insertNew(source.id, listOf(feedItem("k1", "Post", "<p>Third dirigible</p>")), now)

        assertEquals(listOf("Post"), search("zeppelin"))
        assertEquals(emptyList<String>(), search("dirigible"))
        assertEquals(1L, indexRows())
    }

    @Test
    fun `renaming a source moves its items to the new name`() {
        val source = sources.insert(OWNER, "http://a.test/f", "Old Name")
        items.insertNew(source.id, listOf(feedItem("k1", "Episode")), now)

        sources.recordSuccess(source.id, "Brand New", Validators(), now)

        assertEquals(listOf("Episode"), search("brand"))
        assertEquals(emptyList<String>(), search("old"))
    }

    @Test
    fun `deleting a source removes only its items from the index`() {
        val gone = sources.insert(OWNER, "http://a.test/gone", "Gone")
        val kept = sources.insert(OWNER, "http://a.test/kept", "Kept")
        items.insertNew(gone.id, listOf(feedItem("g1", "Vanishing")), now)
        items.insertNew(kept.id, listOf(feedItem("k1", "Staying")), now)

        sources.delete(OWNER, gone.id)

        assertEquals(1L, indexRows())
        assertEquals(listOf("Staying"), search("staying"))
    }

    @Test
    fun `retention removes deleted items from the index`() {
        val source = sources.insert(OWNER, "http://a.test/f", "Daily")
        items.insertNew(source.id, listOf(feedItem("old", "Ancient")), now.minusSeconds(90L * 86400))
        items.insertNew(source.id, listOf(feedItem("new", "Fresh")), now)

        items.deleteFirstSeenBefore(now.minusSeconds(30L * 86400))

        assertEquals(1L, indexRows())
        assertEquals(emptyList<String>(), search("ancient"))
    }

    @Test
    fun `retention keeps a favourite searchable and drops an unstarred item of the same age`() {
        val source = sources.insert(OWNER, "http://a.test/f", "Daily")
        val old = now.minusSeconds(90L * 86400)
        items.insertNew(source.id, listOf(feedItem("kept", "Treasured"), feedItem("lost", "Forgotten")), old)
        val keptId = searching.search(OWNER, SearchRules.matchExpression("treasured")!!, null, 10).single().id
        favourites.favourite(OWNER, keptId, now)

        items.deleteFirstSeenBefore(now.minusSeconds(30L * 86400))

        assertEquals(listOf("Treasured"), search("treasured"))
        assertEquals(emptyList<String>(), search("forgotten"))
    }

    @Test
    fun `retention keeps an item saved for later searchable and drops an unsaved item of the same age`() {
        val source = sources.insert(OWNER, "http://a.test/f", "Daily")
        val old = now.minusSeconds(90L * 86400)
        items.insertNew(source.id, listOf(feedItem("kept", "Queued"), feedItem("lost", "Forgotten")), old)
        val keptId = searching.search(OWNER, SearchRules.matchExpression("queued")!!, null, 10).single().id
        later.save(OWNER, keptId, now)

        items.deleteFirstSeenBefore(now.minusSeconds(30L * 86400))

        assertEquals(listOf("Queued"), search("queued"))
        assertEquals(emptyList<String>(), search("forgotten"))
    }

    @Test
    fun `a search result carries the later flag`() {
        val source = sources.insert(OWNER, "http://a.test/f", "Daily")
        items.insertNew(source.id, listOf(feedItem("a", "Queued"), feedItem("b", "Queued too")), now)
        val match = SearchRules.matchExpression("queued")!!
        later.save(OWNER, searching.search(OWNER, match, null, 10).first { it.title == "Queued" }.id, now)

        val flags = searching.search(OWNER, match, null, 10).associate { it.title to it.later }

        assertEquals(mapOf("Queued" to true, "Queued too" to false), flags)
    }

    @Test
    fun `removing a source removes its items from the index`() {
        val gone = sources.insert(OWNER, "http://g.test/f", "Gone")
        val kept = sources.insert(OWNER, "http://k.test/f", "Kept")
        items.insertNew(gone.id, listOf(feedItem("g", "Match here")), now)
        items.insertNew(kept.id, listOf(feedItem("k", "Match there")), now)

        sources.delete(OWNER, gone.id)

        assertEquals(1L, indexRows())
        assertEquals(listOf("Match there"), search("match"))
    }

    @Test
    fun `search returns matches newest first with the page cursor applied`() {
        val source = sources.insert(OWNER, "http://a.test/f", "Alpha")
        items.insertNew(source.id, listOf(feedItem("old", "Match old", publishedAt = now.minusSeconds(3600))), now)
        items.insertNew(source.id, listOf(feedItem("new", "Match new", publishedAt = now)), now)
        items.insertNew(source.id, listOf(feedItem("other", "Unrelated", publishedAt = now)), now)

        val all = searching.search(OWNER, SearchRules.matchExpression("match")!!, null, 10)
        val afterNew = searching.search(OWNER, SearchRules.matchExpression("match")!!, Cursor(now, all.first().id), 10)

        assertEquals(listOf("Match new", "Match old"), all.map { it.title })
        assertEquals(listOf("Match old"), afterNew.map { it.title })
    }

    @Test
    fun `search only returns the owner's items, including matches on the source name`() {
        val mine = sources.insert(OWNER, "http://a.test/f", "Zebra Weekly")
        val theirs = sources.insert(OTHER, "http://b.test/f", "Zebra Daily")
        items.insertNew(mine.id, listOf(feedItem("m", "Quantum mine")), now)
        items.insertNew(theirs.id, listOf(feedItem("t", "Quantum theirs")), now)
        val quantum = SearchRules.matchExpression("quantum")!!
        val zebra = SearchRules.matchExpression("zebra")!!

        assertEquals(listOf("Quantum mine"), searching.search(OWNER, quantum, null, 10).map { it.title })
        assertEquals(listOf("Quantum theirs"), searching.search(OTHER, quantum, null, 10).map { it.title })
        assertEquals(listOf("Quantum mine"), searching.search(OWNER, zebra, null, 10).map { it.title })
        assertEquals(emptyList<String>(), searching.search(3, quantum, null, 10).map { it.title })
    }

    @Test
    fun `deleting an owner removes their items from the index and nobody else's`() {
        val mine = sources.insert(OWNER, "http://a.test/f", "Mine")
        val theirs = sources.insert(OTHER, "http://b.test/f", "Theirs")
        items.insertNew(mine.id, listOf(feedItem("m", "Match mine")), now)
        items.insertNew(theirs.id, listOf(feedItem("t", "Match theirs")), now)

        jdbc.sql("DELETE FROM users WHERE id = ?").param(OTHER).update()

        assertEquals(1L, indexRows())
        assertEquals(listOf("Match mine"), search("match"))
    }
}
