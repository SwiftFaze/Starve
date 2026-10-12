package dev.starve.acceptance

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.SimpleDriverDataSource
import java.time.Instant

class TitleSteps {
    private val app = World.app

    // ---- fixture setup -------------------------------------------------------------------

    @Given(
        "the feed server serves {string} as an RSS 2.0 feed titled {string} " +
            "whose item title in the XML is {string}",
    )
    fun serveWithItemTitle(
        path: String,
        title: String,
        itemTitle: String,
    ) = World.feedServer().route(path, Route.Feed(FeedSpec(title = title, itemTitle = itemTitle)))

    @Given("the database is migrated only up to before the title fix")
    fun migratedBeforeTitleFix() {
        Flyway
            .configure()
            .dataSource(dataSource())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("4"))
            .load()
            .migrate()
    }

    @Given("it holds a source {string} with stored items titled {string} and {string}")
    fun holdsSourceWithItems(
        source: String,
        first: String,
        second: String,
    ) {
        val jdbc = JdbcClient.create(dataSource())
        val sourceId =
            jdbc
                .sql("INSERT INTO sources (url, title) VALUES (?, ?) RETURNING id")
                .params("http://blog.test/feed", source)
                .query(Long::class.java)
                .single()
        val seen = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        listOf(first, second).forEachIndexed { index, title ->
            jdbc
                .sql(
                    "INSERT INTO items (source_id, item_key, title, published_at, first_seen_at) " +
                        "VALUES (?, ?, ?, ?, ?)",
                ).params(sourceId, "item-$index", title, seen, seen)
                .update()
        }
    }

    // ---- assertions ----------------------------------------------------------------------

    @Then("the source {string} has a stored item titled {string}")
    fun hasStoredItemTitled(
        source: String,
        title: String,
    ) {
        val count =
            app
                .jdbc()
                .sql(
                    "SELECT COUNT(*) FROM items i JOIN sources s ON s.id = i.source_id " +
                        "WHERE s.title = ? AND i.title = ?",
                ).params(source, title)
                .query(Long::class.java)
                .single()
        assertEquals(1L, count, "no stored item titled '$title' in '$source'")
    }

    @Then("the timeline shows an item titled {string}")
    fun timelineShowsTitle(title: String) {
        val page = app.get("/").body()
        assertTrue(page.contains("data-title=\"${Html.escape(title)}\""), "timeline lacks '$title'")
    }

    private fun dataSource() = SimpleDriverDataSource(org.sqlite.JDBC(), "jdbc:sqlite:${app.dbPath}")
}
