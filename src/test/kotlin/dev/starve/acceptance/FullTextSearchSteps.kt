package dev.starve.acceptance

import dev.starve.application.FetchSuccess
import dev.starve.application.GroupRepository
import dev.starve.application.ItemRepository
import dev.starve.application.SourceRepository
import dev.starve.domain.FeedItem
import dev.starve.domain.Validators
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant

/** Steps for full-text-search.feature: the search URL, the search box, the index and the V10 back-fill. */
class FullTextSearchSteps {
    private val app = World.app
    private val legacy = LegacyDatabase(app)

    // ---- fixtures ------------------------------------------------------------------------

    @Given("the source {string} has an item {string} with the stored body {string} published {int} days ago")
    fun itemWithBody(
        source: String,
        title: String,
        body: String,
        days: Int,
    ) {
        val published = Instant.now().minus(Duration.ofDays(days.toLong()))
        val item = FeedItem(title, title, "http://example.test/${title.replace(' ', '-')}", published, null, null, body)
        app.bean(ItemRepository::class.java).insertNew(ownerOf(source), listOf(item), Instant.now())
    }

    @Given("the source {string} has an unread playable item {string} published {int} days ago")
    fun playableItem(
        source: String,
        title: String,
        days: Int,
    ) {
        val published = Instant.now().minus(Duration.ofDays(days.toLong()))
        val link = "https://www.youtube.com/watch?v=abcdefghijk"
        app.bean(ItemRepository::class.java).insertNew(
            ownerOf(source),
            listOf(FeedItem(title, title, link, published, null, null)),
            Instant.now(),
        )
    }

    @Given("the database is migrated only up to before full-text search")
    fun migratedBeforeSearch() = legacy.migrateOnlyTo("6")

    @Given("it holds a source {string} with a stored item titled {string} having the stored body {string}")
    fun holdsSourceWithBodyItem(
        source: String,
        title: String,
        body: String,
    ) = legacy.holdWithBody(source, title, body)

    @When("the source {string} is renamed to {string}")
    fun renameSource(
        current: String,
        renamed: String,
    ) {
        val sources = app.bean(SourceRepository::class.java)
        val source = sources.findAllOf(app.currentUserId()).single { it.title == current }
        sources.recordSuccess(source.id, FetchSuccess(renamed, Validators(), Instant.now()))
    }

    // ---- actions -------------------------------------------------------------------------

    /**
     * A plain search, written as a regular expression because the outline values contain quotes of their own
     * (`"cats"`, `a"b'c`), which a {string} parameter cannot carry. The lookahead keeps the group form out.
     */
    @When("^I search for \"(?!.*\" while )(.*)\"$")
    fun search(text: String) = requestSearch("/feed?q=${encode(text)}")

    @When("I search for {string} while showing only unread items")
    fun searchWhileShowingUnread(text: String) = requestSearch("/feed?q=${encode(text)}")

    @When("I search for {string} while viewing the group {string}")
    fun searchWhileViewingGroup(
        text: String,
        group: String,
    ) {
        val groupId = app.groupId(group)
        requestSearch("/feed?q=${encode(text)}&group=$groupId")
    }

    @When("I search for {string} while viewing the Favourites")
    fun searchWhileViewingFavourites(text: String) = requestSearch("/feed?q=${encode(text)}&favourites=1")

    @When("I clear the search via htmx while viewing the Favourites")
    fun clearSearchInFavourites() = requestHtmx("/feed?q=&favourites=1")

    @When("I search for {string} while viewing the Later")
    fun searchWhileViewingLater(text: String) = requestSearch("/feed?q=${encode(text)}&later=1")

    @When("I clear the search via htmx while viewing the Later")
    fun clearSearchInLater() = requestHtmx("/feed?q=&later=1")

    @When("I clear the search via htmx while viewing the group {string}")
    fun clearSearchInGroup(group: String) {
        val groupId = app.groupId(group)
        requestHtmx("/feed?q=&group=$groupId")
    }

    @When("I search for {string} via htmx")
    fun searchViaHtmx(text: String) {
        World.response = app.get("/feed?q=${encode(text)}", HTMX)
        World.pages = listOf(World.response.body())
    }

    // ---- assertions ----------------------------------------------------------------------

    @Then("the search form carries {string} as {string}")
    fun searchFormCarries(
        name: String,
        value: String,
    ) {
        val form =
            World.response
                .body()
                .substringAfter("<form class=\"search\"")
                .substringBefore("</form>")
        assertTrue(form.contains("name=\"$name\" value=\"$value\""), form)
        assertTrue(form.contains("hx-include=\"closest form\""), form)
    }

    @Then("the search form carries the group {string}")
    fun searchFormCarriesGroup(group: String) =
        searchFormCarries(
            "group",
            app
                .groupId(group)
                .toString(),
        )

    @Then("the search box contains {string}")
    fun searchBoxContains(text: String) = assertTrue(searchBox().contains("value=\"$text\""), searchBox())

    @Then("the search box requests the timeline via htmx as you type with a debounce")
    fun requestsViaHtmxWithDebounce() {
        assertTrue(searchBox().contains("hx-get=\"/feed\""), searchBox())
        assertTrue(searchBox().contains("hx-trigger=\"input changed delay:300ms, search\""), searchBox())
    }

    @Then("the search box pushes the search URL into the browser history")
    fun pushesSearchUrl() = assertTrue(searchBox().contains("hx-push-url=\"true\""), searchBox())

    @Then("the response is a fragment without the top bar")
    fun isFragment() {
        val body = World.response.body()
        assertFalse(body.contains("<header"), "fragment has the top bar")
        assertFalse(body.contains("<!DOCTYPE"), "fragment is a whole document")
        assertTrue(body.contains("id=\"sheet\""), "fragment lacks the sheet")
    }

    @Then("the timeline entry {string} offers Play")
    fun offersPlay(title: String) {
        val entry = World.entry(title)
        assertTrue(entry.contains("class=\"play\""), "no Play control: $entry")
    }

    @Then("the search index holds {int} entries")
    fun indexHolds(count: Int) {
        val rows =
            app
                .jdbc()
                .sql("SELECT COUNT(*) FROM item_search")
                .query(Long::class.java)
                .single()
        assertEquals(count.toLong(), rows)
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun requestSearch(path: String) {
        World.response = app.get(path)
        World.pages = listOf(World.response.body())
    }

    private fun requestHtmx(path: String) {
        World.response = app.get(path, HTMX)
        World.pages = listOf(World.response.body())
    }

    private fun searchBox(): String =
        Regex("<input type=\"search\"[^>]*>").find(World.response.body())?.value
            ?: error("no search box on the page")

    private fun ownerOf(source: String) = app.ownedSource(source).id

    private fun encode(text: String): String = URLEncoder.encode(text, StandardCharsets.UTF_8)

    private companion object {
        val HTMX = mapOf("HX-Request" to "true")
    }
}
