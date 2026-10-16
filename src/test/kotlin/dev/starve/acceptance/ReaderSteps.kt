package dev.starve.acceptance

import dev.starve.application.ItemRepository
import dev.starve.application.SourceRepository
import dev.starve.domain.FeedItem
import io.cucumber.java.ParameterType
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.Duration
import java.time.Instant

/**
 * What a seeded item carries: an optional feed artist ("by"), link, thumbnail (", a thumbnail") and stored
 * body. Built from the text the [SEED] parameter matched.
 */
data class Seed(
    val artist: String?,
    val link: String?,
    val thumbnail: String?,
    val body: String?,
) {
    companion object {
        const val THUMBNAIL = "https://img.example/feed-thumb.jpg"
        private val ARTIST = Regex(" by \"([^\"]*)\"")
        private val LINK = Regex("with the link \"([^\"]*)\"")
        private val BODY = Regex("stored body \"([^\"]*)\"")

        fun parse(text: String): Seed =
            Seed(
                artist = ARTIST.find(text)?.groupValues?.get(1),
                link = LINK.find(text)?.groupValues?.get(1),
                thumbnail = if (text.contains(", a thumbnail")) THUMBNAIL else null,
                body = BODY.find(text)?.groupValues?.get(1),
            )
    }
}

/** The reader's or detail view's link to the source's own feed. */
private val FEED_LINK = Regex("<a[^>]*class=\"toggle feed-link\"[^>]*>")

/** Steps for reader-pane.feature and media-detail-view.feature: seeding items and reading the reader fragment. */
class ReaderSteps {
    private val app = World.app

    @Then("the reader links to the feed of the source {string}, opening in a new tab")
    fun linksToSourceFeed(title: String) {
        val feed = Html.escape(app.ownedSource(title).url)
        val link =
            requireNotNull(FEED_LINK.find(World.response.body())) { "no feed link in ${World.response.body()}" }.value
        assertTrue(link.contains("href=\"$feed\""), link)
        assertTrue(link.contains("target=\"_blank\"") && link.contains("rel=\"noopener noreferrer\""), link)
    }

    // ---- seeding -------------------------------------------------------------------------

    @ParameterType(
        "(?: by \"[^\"]*\")?(?: with the link \"[^\"]*\"| with no link)(?:, a thumbnail)?" +
            "(?: and the stored body \"[^\"]*\"| and no stored body)?",
    )
    fun seed(text: String): Seed = Seed.parse(text)

    @Given("the source {string} has an( unread) item {string}{seed} published {int} days ago")
    fun item(
        source: String,
        title: String,
        seed: Seed,
        days: Int,
    ) {
        val owner = app.ownedSource(source)
        val published = Instant.now().minus(Duration.ofDays(days.toLong()))
        val link = seed.link?.let { World.expandFeedServer(it) }
        val item = FeedItem(title, title, link, published, seed.artist, seed.thumbnail, seed.body)
        app.bean(ItemRepository::class.java).insertNew(owner.id, listOf(item), Instant.now())
    }

    // ---- actions -------------------------------------------------------------------------

    @When("I request the reader of the item {string} via htmx")
    fun requestReader(title: String) = requestReaderOf(itemId(title))

    @When("I request the reader of the unknown item {int}")
    fun requestUnknownReader(id: Int) = requestReaderOf(id.toLong())

    // ---- assertions on the home page -----------------------------------------------------

    @Then("the title of {string} requests its reader into the reader pane via htmx")
    fun titleRequestsReader(title: String) {
        val tag = titleTag(title)
        assertTrue(Regex("hx-get=\"/items/${itemId(title)}/reader[?\"]").containsMatchIn(tag), tag)
        assertTrue(tag.contains("hx-target=\"#reader\""), tag)
    }

    @Then("the title of {string} still links to its open endpoint without JavaScript")
    fun titleKeepsOpenLink(title: String) {
        val tag = titleTag(title)
        assertTrue(tag.contains("href=\"/items/${itemId(title)}/open\""), tag)
    }

    @Then("the title of {string} does not request a reader")
    fun titleDoesNotRequestReader(title: String) = assertFalse(titleTag(title).contains("/reader\""), titleTag(title))

    @Then("the page has a reader pane")
    fun hasReaderPane() = assertTrue(READER_PANE.containsMatchIn(body()), body())

    @Then("the reader pane is empty")
    fun readerPaneIsEmpty() = assertTrue(EMPTY_READER_PANE.containsMatchIn(body()), body())

    // ---- assertions on the reader fragment -----------------------------------------------

    @Then("the reader shows the title {string}")
    fun readerShowsTitle(title: String) = assertEquals(Html.escape(title), group(READER_TITLE), body())

    @Then("the reader shows the source {string}")
    fun readerShowsSource(source: String) = assertEquals(Html.escape(source), group(READER_SOURCE), body())

    @Then("the reader shows a published date")
    fun readerShowsDate() = assertTrue(READER_DATE.containsMatchIn(body()), body())

    @Then("the reader body contains {string}")
    fun readerBodyContains(html: String) = assertTrue(group(READER_BODY).contains(html), body())

    @Then("the response contains the text {string} escaped")
    fun containsEscaped(text: String) = assertTrue(body().contains(Html.escape(text)), body())

    @Then("the reader has no body")
    fun readerHasNoBody() = assertFalse(body().contains("class=\"reader-body\""), body())

    @Then("the reader says the post has no text to show here")
    fun readerSaysNoText() = assertTrue(body().contains("no text to show here"), body())

    @Then("the reader has an {string} link to {string}")
    fun readerHasLink(
        label: String,
        url: String,
    ) = assertTrue(linkTag(label)?.contains("href=\"${World.expandFeedServer(url)}\"") == true, body())

    @Then("the {string} link opens in a new tab with rel {string}")
    fun linkOpensInNewTab(
        label: String,
        rel: String,
    ) {
        val tag = requireNotNull(linkTag(label)) { "no '$label' link in ${body()}" }
        assertTrue(tag.contains("target=\"_blank\"") && tag.contains("rel=\"$rel\""), tag)
    }

    @Then("the reader has no {string} link")
    fun readerHasNoLink(label: String) = assertEquals(null, linkTag(label), body())

    @Then("the reader has a Close control")
    fun readerHasClose() = assertTrue(READER_CLOSE.containsMatchIn(body()), body())

    @Then("the reader does not ask a video in the pane to step down")
    fun readerKeepsVideoPlacement() = assertFalse(body().contains("data-step-video"), body())

    // ---- helpers -------------------------------------------------------------------------

    private fun requestReaderOf(id: Long) {
        World.bodyBeforeReader =
            app
                .jdbc()
                .sql("SELECT body FROM items WHERE id = ?")
                .param(id)
                .query(String::class.java)
                .optional()
                .orElse(null)
        val started = System.nanoTime()
        World.response = app.get("/items/$id/reader", mapOf("HX-Request" to "true"))
        World.readerMillis = (System.nanoTime() - started) / NANOS_PER_MILLI
    }

    private fun body(): String = World.response.body()

    private fun group(pattern: Regex) = requireNotNull(pattern.find(body())) { "no $pattern" }.groupValues[1]

    private fun titleTag(title: String): String = World.titleLink(title)

    private fun linkTag(label: String): String? = Html.anchor(body(), label)

    private fun itemId(title: String): Long = app.itemId(title)

    private companion object {
        val READER_PANE = Regex("<aside[^>]*id=\"reader\"[^>]*>")
        val EMPTY_READER_PANE = Regex("<aside[^>]*id=\"reader\"[^>]*></aside>")
        val READER_TITLE = Regex("<h2 class=\"reader-title\"[^>]*>(.*?)</h2>", RegexOption.DOT_MATCHES_ALL)
        val READER_SOURCE = Regex("<span class=\"source\"[^>]*>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
        val READER_DATE = Regex("<time [^>]*>\\d{4}-\\d{2}-\\d{2}")
        val READER_BODY = Regex("<div class=\"reader-body\"[^>]*>(.*)</div>", RegexOption.DOT_MATCHES_ALL)
        val READER_CLOSE = Regex("<button[^>]*class=\"reader-close\"[^>]*>")
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
