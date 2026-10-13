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

/** What a seeded item links to and carries as its stored body. */
data class Content(
    val link: String?,
    val body: String?,
)

/** Steps for reader-pane.feature: seeding posts with bodies and reading the reader fragment. */
class ReaderSteps {
    private val app = World.app

    // ---- seeding -------------------------------------------------------------------------

    @ParameterType(
        "with the link \"([^\"]*)\" and the stored body \"([^\"]*)\"" +
            "|with the link \"([^\"]*)\" and no stored body|with no link and no stored body",
    )
    fun content(
        linkWithBody: String?,
        body: String?,
        linkWithoutBody: String?,
    ) = Content(linkWithBody ?: linkWithoutBody, body)

    @Given("the source {string} has an( unread) item {string} {content} published {int} days ago")
    fun item(
        source: String,
        title: String,
        content: Content,
        days: Int,
    ) {
        val sources = app.bean(SourceRepository::class.java)
        val owner =
            sources.findAll().firstOrNull { it.title == source }
                ?: sources.insert("http://$source.test/feed", source)
        val published = Instant.now().minus(Duration.ofDays(days.toLong()))
        val item = FeedItem(title, title, content.link, published, null, null, content.body)
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
        assertTrue(tag.contains("hx-get=\"/items/${itemId(title)}/reader\""), tag)
        assertTrue(tag.contains("hx-target=\"#reader\""), tag)
    }

    @Then("the title of {string} still links to its open endpoint without JavaScript")
    fun titleKeepsOpenLink(title: String) {
        val tag = titleTag(title)
        assertTrue(tag.contains("href=\"/items/${itemId(title)}/open\""), tag)
    }

    @Then("the title of {string} does not request a reader")
    fun titleDoesNotRequestReader(title: String) = assertFalse(titleTag(title).contains("hx-get"), titleTag(title))

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
    ) = assertTrue(linkTag(label)?.contains("href=\"$url\"") == true, body())

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

    @Then("the reader asks a video in the pane to step down to Mini")
    fun readerStepsVideoDown() = assertTrue(body().contains("data-step-video=\"mini\""), body())

    // ---- helpers -------------------------------------------------------------------------

    private fun requestReaderOf(id: Long) {
        World.response = app.get("/items/$id/reader", mapOf("HX-Request" to "true"))
    }

    private fun body(): String = World.response.body()

    private fun group(pattern: Regex) = requireNotNull(pattern.find(body())) { "no $pattern" }.groupValues[1]

    private fun titleTag(title: String): String =
        requireNotNull(TITLE_LINK.find(World.entry(title))) { "no title link in ${World.entry(title)}" }.value

    private fun linkTag(label: String): String? {
        val link = Regex("<a [^>]*>\\s*${Regex.escape(label)}\\s*</a>")
        return link.find(body())?.value
    }

    private fun itemId(title: String): Long =
        app
            .jdbc()
            .sql("SELECT id FROM items WHERE title = ?")
            .param(title)
            .query(Long::class.java)
            .single()

    private companion object {
        val TITLE_LINK = Regex("<a class=\"title\"[^>]*>")
        val READER_PANE = Regex("<aside[^>]*id=\"reader\"[^>]*>")
        val EMPTY_READER_PANE = Regex("<aside[^>]*id=\"reader\"[^>]*></aside>")
        val READER_TITLE = Regex("<h2 class=\"reader-title\"[^>]*>(.*?)</h2>", RegexOption.DOT_MATCHES_ALL)
        val READER_SOURCE = Regex("<span class=\"source\"[^>]*>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
        val READER_DATE = Regex("<time [^>]*>\\d{4}-\\d{2}-\\d{2}")
        val READER_BODY = Regex("<div class=\"reader-body\"[^>]*>(.*)</div>", RegexOption.DOT_MATCHES_ALL)
        val READER_CLOSE = Regex("<button[^>]*class=\"reader-close\"[^>]*>")
    }
}
