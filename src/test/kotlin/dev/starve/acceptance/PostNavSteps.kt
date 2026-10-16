package dev.starve.acceptance

import dev.starve.domain.Playable
import io.cucumber.java.ParameterType
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Steps for mobile-post-view.feature: the Previous and Next links in a post's head, the list a post is opened
 * from, and the deck's Open post control.
 */
class PostNavSteps {
    private val app = World.app

    @ParameterType("the plain timeline|the Favourites view|the timeline showing read items|the group \"[^\"]*\"")
    fun listContext(text: String): String = text

    @When("I request the reader of the item {string} via htmx from {listContext}")
    fun requestReaderFrom(
        title: String,
        context: String,
    ) = requestFrom("reader", app.itemId(title), context)

    @When("I request the detail view of the item {string} via htmx from {listContext}")
    fun requestDetailFrom(
        title: String,
        context: String,
    ) = requestFrom("detail", app.itemId(title), context)

    @Then("the reader offers Previous opening the post of {string} via htmx")
    fun readerOffersPrevious(title: String) = assertOffers("prev", title)

    @Then("the reader offers Next opening the post of {string} via htmx")
    fun readerOffersNext(title: String) = assertOffers("next", title)

    @Then("the detail view offers Previous opening the post of {string} via htmx")
    fun detailOffersPrevious(title: String) = assertOffers("prev", title)

    @Then("the detail view offers Next opening the post of {string} via htmx")
    fun detailOffersNext(title: String) = assertOffers("next", title)

    @Then("the reader offers no Previous")
    fun readerOffersNoPrevious() = assertOffersNothing("prev")

    @Then("the reader offers no Next")
    fun readerOffersNoNext() = assertOffersNothing("next")

    @Then("the detail view offers no Previous")
    fun detailOffersNoPrevious() = assertOffersNothing("prev")

    @Then("the detail view offers no Next")
    fun detailOffersNoNext() = assertOffersNothing("next")

    @Then("the Next control opens the detail view of {string} and not its player")
    fun nextOpensDetailNotPlayer(title: String) {
        val id = app.itemId(title)
        val tag = requireNotNull(navTag("next")) { "no Next control in ${body()}" }
        assertTrue(tag.contains("hx-get=\"/items/$id/detail"), tag)
        assertFalse(tag.contains("/player"), tag)
    }

    @Then("the deck offers Open post opening the detail view of {string} into the reader pane via htmx")
    fun deckOffersOpenPost(title: String) {
        val id = app.itemId(title)
        val tag = requireNotNull(OPEN_POST.find(body())) { "no Open post in ${body()}" }.value
        assertTrue(tag.contains("hx-get=\"/items/$id/detail\""), tag)
        assertTrue(tag.contains("hx-target=\"#reader\""), tag)
    }

    @Then("the Open post control is outside the video's container")
    fun openPostIsOutsideVideo() {
        val at = body().indexOf("class=\"open-post\"")
        assertTrue(at >= 0, body())
        val videoStarts = VIDEO_CONTAINERS.map { body().indexOf(it) }.filter { it >= 0 }
        assertTrue(videoStarts.all { it > at }, body())
    }

    /**
     * When the scenario's list was rendered: captured once, at its first list request, so the Givens before it
     * (read marks) fall before it and the reads the requests make fall at or after it.
     */
    private var seenAt: Long? = null

    private fun requestFrom(
        page: String,
        id: Long,
        context: String,
    ) {
        val seen = seenAt ?: System.currentTimeMillis().also { seenAt = it }
        val query = listOfNotNull(listQuery(context).ifEmpty { null }, "seen=$seen").joinToString("&")
        val path = "/items/$id/$page?$query"
        World.response = app.get(path, mapOf("HX-Request" to "true"))
    }

    /** The query string a list context stands for, as the list's own links write it. */
    private fun listQuery(context: String): String =
        when (context) {
            "the plain timeline" -> ""
            "the Favourites view" -> "favourites=1"
            "the timeline showing read items" -> "show=all"
            else -> "group=" + groupRef(context.removePrefix("the group ").trim('"'))
        }

    /** The id of the named group, or the name itself when there is no such group (so the server answers 404). */
    private fun groupRef(name: String): String =
        app
            .jdbc()
            .sql("SELECT id FROM groups WHERE name = ? ORDER BY id LIMIT 1")
            .param(name)
            .query(Long::class.java)
            .optional()
            .map(Long::toString)
            .orElse(name)

    private fun assertOffers(
        kind: String,
        title: String,
    ) {
        val id = app.itemId(title)
        val tag = requireNotNull(navTag(kind)) { "no $kind control in ${body()}" }
        val page = if (Playable.of(linkOf(id)) != null) "detail" else "reader"
        assertTrue(Regex("hx-get=\"/items/$id/$page[?\"]").containsMatchIn(tag), tag)
        assertTrue(tag.contains("hx-target=\"#reader\""), tag)
    }

    private fun assertOffersNothing(kind: String) = assertFalse(navTag(kind) != null, body())

    private fun navTag(kind: String): String? = Regex("<a[^>]*class=\"post-$kind\"[^>]*>").find(body())?.value

    private fun linkOf(id: Long): String? =
        app
            .jdbc()
            .sql("SELECT link FROM items WHERE id = ?")
            .param(id)
            .query(String::class.java)
            .optional()
            .orElse(null)

    private fun body(): String = World.response.body()

    private companion object {
        val OPEN_POST = Regex("<button[^>]*class=\"open-post\"[^>]*>")
        val VIDEO_CONTAINERS = listOf("class=\"ratio-16x9\"", "class=\"sc-frame\"")
    }
}
