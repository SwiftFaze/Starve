package dev.starve.acceptance

import com.fasterxml.jackson.databind.ObjectMapper
import dev.starve.application.ItemRepository
import dev.starve.domain.Catalogue
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for media-detail-view.feature: the detail view of a SoundCloud or YouTube item and its oEmbed fixture. */
class MediaDetailSteps {
    private val app = World.app

    @Before
    fun forgetOembedQueries() = World.oembed.clearRequests()

    // ---- the SoundCloud oEmbed fixture ---------------------------------------------------

    @Given(
        "the SoundCloud oEmbed endpoint responds to the track {string} " +
            "with the author {string}, the thumbnail {string} and the description {string}",
    )
    fun oembedWithAllFields(
        track: String,
        author: String,
        thumbnail: String,
        description: String,
    ) = answerOembed(track, oembedJson(author = author, thumbnail = thumbnail, description = description))

    @Given("the SoundCloud oEmbed endpoint responds to the track {string} with the author {string} and no description")
    fun oembedWithoutDescription(
        track: String,
        author: String,
    ) = answerOembed(track, oembedJson(author = author))

    @Given(
        "the SoundCloud oEmbed endpoint responds to the track {string} " +
            "with the author {string} and the description {string}",
    )
    fun oembedWithAuthorAndDescription(
        track: String,
        author: String,
        description: String,
    ) = answerOembed(track, oembedJson(author = author, description = description))

    @Given("the SoundCloud oEmbed endpoint responds to the track {string} with the description {string}")
    fun oembedWithDescription(
        track: String,
        description: String,
    ) = answerOembed(track, oembedJson(description = description))

    @Given("the SoundCloud oEmbed endpoint responds to the track {string} with HTTP {int}")
    fun oembedWithStatus(
        track: String,
        status: Int,
    ) = World.oembed.routeSoundCloud(track, Route.Status(status))

    @Given("the SoundCloud oEmbed endpoint responds to the track {string} with an unreadable body")
    fun oembedUnreadable(track: String) = answerOembed(track, "<html>not a JSON document</html>")

    // ---- actions -------------------------------------------------------------------------

    @When("I request the detail view of the item {string} via htmx")
    fun requestDetail(title: String) = requestDetailOf(itemId(title))

    @When("I request the detail view of the unknown item {int}")
    fun requestUnknownDetail(id: Int) = requestDetailOf(id.toLong())

    @When("the application is restarted")
    fun restartApplication() = app.restart()

    // ---- assertions on the detail fragment -----------------------------------------------

    @Then("the detail view shows the title {string}")
    fun showsTitle(title: String) = assertEquals(Html.escape(title), group(TITLE), body())

    @Then("the detail view shows the artist {string}")
    fun showsArtist(artist: String) = assertEquals(Html.escape(artist), group(ARTIST), body())

    @Then("the detail view shows a published date")
    fun showsDate() = assertTrue(DATE.containsMatchIn(body()), body())

    @Then("the detail view shows the artwork {string}")
    fun showsArtwork(url: String) = assertEquals(url, artwork(), body())

    @Then("the detail view shows the item's thumbnail as its artwork")
    fun showsFeedThumbnail() = assertEquals(Seed.THUMBNAIL, artwork(), body())

    @Then("the detail view description contains {string}")
    fun descriptionContains(html: String) = assertTrue(group(DESCRIPTION).contains(html), body())

    @Then("the detail view has no description")
    fun hasNoDescription() = assertFalse(body().contains("class=\"reader-body\""), body())

    @Then("the detail view has a Play control")
    fun hasPlay() = assertTrue(PLAY.containsMatchIn(body()), body())

    @Then("the Play control of the detail view loads the player of {string} into the dock")
    fun playLoadsPlayer(title: String) {
        val tag = requireNotNull(PLAY.find(body())) { "no Play control in ${body()}" }.value
        assertTrue(tag.contains("hx-get=\"/items/${itemId(title)}/player\""), tag)
        assertTrue(tag.contains("hx-target=\"#dock\""), tag)
    }

    @Then("the detail view carries the catalogue number of {string}")
    fun carriesCatalogueNumber(title: String) {
        val number = Catalogue.number(itemId(title))
        assertTrue(body().contains("data-item=\"$number\""), body())
    }

    @Then("the detail view has a Close control")
    fun hasClose() = assertTrue(CLOSE.containsMatchIn(body()), body())

    @Then("the detail view has an {string} link to {string}")
    fun hasLink(
        label: String,
        url: String,
    ) = assertTrue(Html.anchor(body(), label)?.contains("href=\"$url\"") == true, body())

    @Then("the detail view does not ask a video in the pane to step down")
    fun keepsVideoPlacement() = assertFalse(body().contains("data-step-video"), body())

    // ---- assertions on the title and the oEmbed fixture ----------------------------------

    @Then("the title of {string} requests its detail view into the reader pane via htmx")
    fun titleRequestsDetail(title: String) {
        val tag = World.titleLink(title)
        assertTrue(tag.contains("hx-get=\"/items/${itemId(title)}/detail\""), tag)
        assertTrue(tag.contains("hx-target=\"#reader\""), tag)
    }

    @Then("the title of {string} does not request a detail view")
    fun titleDoesNotRequestDetail(title: String) = assertFalse(World.titleLink(title).contains("/detail\""), title)

    @Then("the response does not contain {string}")
    fun responseDoesNotContain(text: String) = assertFalse(World.response.body().contains(text), World.response.body())

    @Then("the SoundCloud oEmbed endpoint was asked for the track {string} {int} time")
    fun askedForTrack(
        track: String,
        times: Int,
    ) = assertEquals(times, World.oembed.soundCloudQueries(track))

    @Then("no SoundCloud oEmbed request was made")
    fun noOembedRequest() = assertTrue(World.oembed.requests.none { it.path == FeedServer.SOUNDCLOUD_OEMBED_PATH })

    // ---- helpers -------------------------------------------------------------------------

    private fun requestDetailOf(id: Long) {
        World.response = app.get("/items/$id/detail", mapOf("HX-Request" to "true"))
    }

    private fun answerOembed(
        track: String,
        json: String,
    ) = World.oembed.routeSoundCloud(track, Route.Json(json))

    /** The oEmbed document with only the fields given; a null field is left out, as SoundCloud can omit it. */
    private fun oembedJson(
        author: String? = null,
        thumbnail: String? = null,
        description: String? = null,
    ): String =
        ObjectMapper().writeValueAsString(
            mapOf("author_name" to author, "thumbnail_url" to thumbnail, "description" to description)
                .filterValues { it != null },
        )

    private fun body(): String = World.response.body()

    private fun group(pattern: Regex): String {
        val page = body()
        return requireNotNull(pattern.find(page)) { "no $pattern in $page" }.groupValues[1]
    }

    private fun artwork(): String = group(ARTWORK)

    private fun itemId(title: String): Long = app.itemId(title)

    private companion object {
        val TITLE = Regex("<h2 class=\"reader-title\"[^>]*>(.*?)</h2>", RegexOption.DOT_MATCHES_ALL)
        val ARTIST = Regex("<span class=\"detail-artist\"[^>]*>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
        val DATE = Regex("<time [^>]*>\\d{4}-\\d{2}-\\d{2}")
        val ARTWORK = Regex("<img class=\"detail-art\"[^>]*src=\"([^\"]*)\"")
        val DESCRIPTION = Regex("<div class=\"reader-body\"[^>]*>(.*)</div>", RegexOption.DOT_MATCHES_ALL)
        val PLAY = Regex("<button[^>]*class=\"detail-play\"[^>]*>")
        val CLOSE = Regex("<button[^>]*class=\"reader-close\"[^>]*>")
    }
}
