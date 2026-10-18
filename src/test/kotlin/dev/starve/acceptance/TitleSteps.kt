package dev.starve.acceptance

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class TitleSteps {
    private val app = World.app
    private val legacy = LegacyDatabase(app)

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

    @Given(
        "the feed server serves {string} as an RSS 2.0 feed titled {string} " +
            "whose item has no title and the description {string}",
    )
    fun serveUntitledItem(
        path: String,
        title: String,
        description: String,
    ) = World.feedServer().route(path, Route.Feed(FeedSpec(title = title, itemTitle = "", description = description)))

    @Given("the database is migrated only up to before the title fix")
    fun migratedBeforeTitleFix() = legacy.migrateOnlyTo("4")

    @Given("it holds a source {string} with stored items titled {string} and {string}")
    fun holdsSourceWithItems(
        source: String,
        first: String,
        second: String,
    ) = legacy.hold(source, first, second)

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
        val page = app.get("/feed").body()
        assertTrue(page.contains("data-title=\"${Html.escape(title)}\""), "timeline lacks '$title'")
    }
}
