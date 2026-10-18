package dev.starve.acceptance

import dev.starve.application.PollService
import dev.starve.application.RetentionService
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.Duration
import java.time.Instant

class IngestionSteps {
    private val app = World.app
    private var lastPage: String by World::sourcesPage
    private var removedSourceId = 0L
    private val itemIdByAgeDays = mutableMapOf<Int, Long>()

    @After
    fun stopFeedServer() {
        World.feeds?.stop()
        World.feeds = null
    }

    @Given("a feed server is running")
    fun feedServerRunning() {
        World.feeds = FeedServer()
    }

    // ---- fixture setup -------------------------------------------------------------------

    @Given("the feed server serves {string} as an RSS 2.0 feed titled {string} with {int} entries")
    fun serveRss(
        path: String,
        title: String,
        entries: Int,
    ) = serve(path, FeedSpec(title = title, entries = entries))

    @Given("the feed server serves {string} as an RSS 2.0 feed titled {string} with {int} entries and an ETag")
    fun serveRssWithEtag(
        path: String,
        title: String,
        entries: Int,
    ) = serve(path, FeedSpec(title = title, entries = entries, etag = true))

    @Given("the feed server serves {string} as an RSS 2.0 feed titled {string} with {int} entries lacking a guid")
    fun serveRssWithoutGuid(
        path: String,
        title: String,
        entries: Int,
    ) = serve(path, FeedSpec(title = title, entries = entries, noGuid = true))

    @Given(
        "the feed server serves {string} as an RSS 2.0 feed titled {string} " +
            "with {int} entries lacking a published date",
    )
    fun serveRssWithoutDate(
        path: String,
        title: String,
        entries: Int,
    ) = serve(path, FeedSpec(title = title, entries = entries, noDate = true))

    @Given("the feed server serves {string} as an RSS 2.0 feed without a title with {int} entries")
    fun serveRssWithoutTitle(
        path: String,
        entries: Int,
    ) = serve(path, FeedSpec(title = null, entries = entries))

    @Given("the feed server serves {string} as an Atom feed titled {string} with {int} entries having thumbnails")
    fun serveAtomWithThumbnails(
        path: String,
        title: String,
        entries: Int,
    ) = serve(path, FeedSpec(atom = true, title = title, entries = entries, thumbnails = true))

    @Given(
        "the feed server serves {string} as an RSS 2.0 feed titled {string} with {int} entries having iTunes cover art",
    )
    fun serveRssWithCoverArt(
        path: String,
        title: String,
        entries: Int,
    ) = serve(path, FeedSpec(title = title, entries = entries, coverArt = true))

    @Given(
        "the feed server serves {string} as an RSS 2.0 feed titled {string} " +
            "with {int} entries having the iTunes cover art {string}",
    )
    fun serveRssWithGivenCoverArt(
        path: String,
        title: String,
        entries: Int,
        art: String,
    ) = serve(path, FeedSpec(title = title, entries = entries, coverArt = true, coverArtUrl = art))

    @Given("the feed server serves {string} as an HTML page")
    fun serveHtml(path: String) = World.feedServer().route(path, Route.Raw("page.html", "text/html"))

    @Given("the feed server responds to {string} with HTTP {int}")
    fun respondWithStatus(
        path: String,
        status: Int,
    ) = World.feedServer().route(path, Route.Status(status))

    @Given("the feed server now responds to {string} with {}")
    fun nowRespondsWith(
        path: String,
        failure: String,
    ) {
        val route =
            when (failure) {
                "malformed XML" -> Route.Raw("malformed.xml", "application/xml")
                "an HTML page" -> Route.Raw("page.html", "text/html")
                "no response in time" -> Route.Stall
                else -> Route.Status(failure.removePrefix("HTTP ").toInt())
            }
        World.feedServer().route(path, route)
    }

    @Given("the feed {string} now has {int} entries")
    fun feedNowHasEntries(
        path: String,
        entries: Int,
    ) {
        val current = World.feedServer().routeOf(path) as Route.Feed
        serve(path, current.spec.copy(entries = entries))
    }

    // ---- actions -------------------------------------------------------------------------

    @When("I add the feed URL {string}")
    fun addFeedUrl(path: String) = addRaw(World.feedServer().baseUrl + path)

    @Given("I have added the feed URL {string}")
    fun haveAddedFeedUrl(path: String) = addRaw(World.feedServer().baseUrl + path)

    @When("I add the raw feed URL {string}")
    fun addRawFeedUrl(url: String) = addRaw(url)

    /** A background pass in which every source on the default interval is due, whenever it was last fetched. */
    @When("the poller runs")
    fun pollerRuns() = app.bean(PollService::class.java).pollDue(app.clock.instant(), Duration.ZERO)

    @When("the retention job runs")
    fun retentionRuns() {
        app.bean(RetentionService::class.java).purgeOld()
    }

    @When("I remove the source {string}")
    fun removeSource(title: String) {
        removedSourceId = sourceId(title)
        app.postForm("/sources/$removedSourceId/delete")
    }

    @Given("the stored items of {string} were first seen {int}, {int} and {int} days ago")
    fun backdateItems(
        title: String,
        first: Int,
        second: Int,
        third: Int,
    ) {
        val ids = itemIds(title)
        listOf(first, second, third).forEachIndexed { index, days ->
            val seen = Instant.now().minus(Duration.ofDays(days.toLong())).toEpochMilli()
            app
                .jdbc()
                .sql("UPDATE items SET first_seen_at = ? WHERE id = ?")
                .params(seen, ids[index])
                .update()
            itemIdByAgeDays[days] = ids[index]
        }
    }

    // ---- assertions on the page ----------------------------------------------------------

    @Then("the sources page lists a source titled {string}")
    fun pageListsSource(title: String) = assertTrue(block(title) != null, "no source titled '$title'")

    @Then("the sources page lists a source titled with the feed URL {string}")
    fun pageListsSourceByUrl(path: String) = pageListsSource(World.feedServer().baseUrl + path)

    @Then("the sources page does not list a source titled {string}")
    fun pageLacksSource(title: String) = assertTrue(block(title) == null, "unexpected source '$title'")

    @Then("the sources page shows the message {string}")
    fun pageShowsMessage(message: String) = assertTrue(lastPage.contains(message), "no '$message' in $lastPage")

    @Then("the source {string} shows a last fetched time")
    fun showsLastFetched(title: String) = assertTrue(requireBlock(title).contains("class=\"last-fetched\""))

    @Then("the source {string} shows no error")
    fun showsNoError(title: String) = assertFalse(requireBlock(title).contains("class=\"error\""))

    @Then("the source {string} shows an error")
    fun showsError(title: String) = assertTrue(requireBlock(title).contains("class=\"error\""))

    // ---- assertions on storage -----------------------------------------------------------

    @Then("the source {string} has {int} stored items")
    fun hasStoredItems(
        title: String,
        count: Int,
    ) = assertEquals(count, itemIds(title).size)

    @Then("the stored items of {string} have a title, a link and a published date")
    fun itemsHaveCoreFields(title: String) {
        assertEveryItem(title, "i.title <> '' AND i.link IS NOT NULL AND i.published_at > 0")
    }

    @Then("every stored item of {string} has the thumbnail URL {string}")
    fun itemsHaveThumbnail(
        title: String,
        url: String,
    ) = assertEquals(listOf(url), thumbnails(title).distinct())

    @Then("every stored item of {string} has a thumbnail URL")
    fun itemsHaveThumbnails(title: String) = assertEveryItem(title, "i.thumbnail_url LIKE 'http%'")

    @Then("the stored items of {string} have a published date equal to their first-seen time")
    fun publishedEqualsFirstSeen(title: String) = assertEveryItem(title, "i.published_at = i.first_seen_at")

    @Then("the feed server received a conditional request for {string} with the stored ETag")
    fun conditionalRequestSeen(path: String) {
        val etag =
            app
                .jdbc()
                .sql("SELECT etag FROM sources")
                .query(String::class.java)
                .single()
        assertEquals(FeedServer.ETAG, etag)
        assertTrue(World.feedServer().requests.any { it.path == path && it.ifNoneMatch == etag })
    }

    @Then("there are no subscriptions")
    fun noSubscriptions() = assertEquals(0L, subscriptionCount())

    @Then("there are {int} subscriptions")
    fun subscriptionsCountPlural(count: Int) = subscriptionsCount(count)

    @Then("there is {int} subscription")
    fun subscriptionsCount(count: Int) = assertEquals(count.toLong(), subscriptionCount())

    @Then("no items are stored for {string}")
    fun noItemsStored(
        @Suppress("UNUSED_PARAMETER") title: String,
    ) = assertEquals(0L, count("SELECT COUNT(*) FROM items WHERE source_id = ?", removedSourceId))

    @Then("the item first seen {int} days ago no longer exists")
    fun itemGone(days: Int) {
        val id = itemIdByAgeDays.getValue(days)
        assertEquals(0L, count("SELECT COUNT(*) FROM items WHERE id = ?", id))
    }

    @Then("the items first seen {int} and {int} days ago still exist")
    fun itemsRemain(
        first: Int,
        second: Int,
    ) = listOf(first, second).forEach {
        assertEquals(1L, count("SELECT COUNT(*) FROM items WHERE id = ?", itemIdByAgeDays.getValue(it)))
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun serve(
        path: String,
        spec: FeedSpec,
    ) = World.feedServer().route(path, Route.Feed(spec))

    private fun addRaw(url: String) {
        lastPage = app.postForm("/sources", "url" to url).body()
    }

    private fun block(title: String): String? =
        app
            .get("/sources")
            .body()
            .split("<li class=\"source\"")
            .drop(1)
            .firstOrNull { it.contains("data-title=\"${Html.escape(title)}\"") }

    private fun requireBlock(title: String) = requireNotNull(block(title)) { "no source titled '$title'" }

    private fun sourceId(title: String) = app.sourceId(title)

    private fun itemIds(title: String): List<Long> =
        app
            .jdbc()
            .sql("SELECT i.id FROM items i WHERE i.source_id = ? ORDER BY i.id")
            .param(sourceId(title))
            .query(Long::class.java)
            .list()

    private fun assertEveryItem(
        title: String,
        condition: String,
    ) {
        val total = itemIds(title).size
        assertTrue(total > 0, "no items for '$title'")
        val matching =
            count(
                "SELECT COUNT(*) FROM items i WHERE i.source_id = ? AND ($condition)",
                sourceId(title),
            )
        assertEquals(total.toLong(), matching)
    }

    private fun thumbnails(title: String): List<String?> =
        app
            .jdbc()
            .sql("SELECT i.thumbnail_url FROM items i WHERE i.source_id = ?")
            .param(sourceId(title))
            .query { rs, _ -> rs.getString(1) }
            .list()

    private fun subscriptionCount() = count("SELECT COUNT(*) FROM sources WHERE owner_id = ?", app.currentUserId())

    private fun count(
        sql: String,
        vararg params: Any,
    ): Long =
        app
            .jdbc()
            .sql(sql)
            .params(params.toList())
            .query(Long::class.java)
            .single()
}
