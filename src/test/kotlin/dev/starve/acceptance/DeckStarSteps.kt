package dev.starve.acceptance

import io.cucumber.java.ParameterType
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Steps for deck-player-fixes.feature: the favourite star in the deck and the detail view, each star's item-specific
 * id, and the out-of-band refresh of the other stars after a toggle.
 */
class DeckStarSteps {
    private val app = World.app

    /** The place a toggle was clicked in, as a query string: the timeline row sends none. */
    @ParameterType("[a-z][a-z ,]*")
    fun starPlaces(text: String): List<String> = text.split(",").map { it.trim() }

    // ---- actions -------------------------------------------------------------------------

    @When("I {word} the item {string} via htmx from its timeline row")
    fun toggleFromRow(
        verb: String,
        title: String,
    ) = toggle(verb, itemId(title).toString(), "", HTMX)

    @When("I {word} the item {string} via htmx from the deck")
    fun toggleFromDeck(
        verb: String,
        title: String,
    ) = toggle(verb, itemId(title).toString(), "?from=deck", HTMX)

    @When("I {word} the item {string} via htmx from the detail view")
    fun toggleFromDetail(
        verb: String,
        title: String,
    ) = toggle(verb, itemId(title).toString(), "?from=detail", HTMX)

    @When("I {word} the item {string} from the deck")
    fun toggleFromDeckWithoutJavascript(
        verb: String,
        title: String,
    ) = toggle(verb, itemId(title).toString(), "?from=deck")

    @When("I {word} the item {string} from the detail view")
    fun toggleFromDetailWithoutJavascript(
        verb: String,
        title: String,
    ) = toggle(verb, itemId(title).toString(), "?from=detail")

    @When("I {word} the unknown item {word} via htmx from the deck")
    fun toggleUnknownFromDeck(
        verb: String,
        id: String,
    ) = toggle(verb, id, "?from=deck", HTMX)

    @When("I {word} the unknown item {word} via htmx from the detail view")
    fun toggleUnknownFromDetail(
        verb: String,
        id: String,
    ) = toggle(verb, id, "?from=detail", HTMX)

    // ---- the deck's and the detail view's stars ------------------------------------------

    @Then("the deck has an empty star")
    fun deckHasEmptyStar() = assertStarState(firstStar(), false)

    @Then("the deck has a filled star")
    fun deckHasFilledStar() = assertStarState(firstStar(), true)

    @Then("the detail view has an empty star")
    fun detailHasEmptyStar() = assertStarState(firstStar(), false)

    @Then("the detail view has a filled star")
    fun detailHasFilledStar() = assertStarState(firstStar(), true)

    @Then("the deck's star favourites {string} via htmx")
    fun deckStarFavourites(title: String) = assertStar(firstStar(), false, "favourite", itemId(title))

    @Then("the deck's star unfavourites {string} via htmx")
    fun deckStarUnfavourites(title: String) = assertStar(firstStar(), true, "unfavourite", itemId(title))

    @Then("the detail view's star favourites {string} via htmx")
    fun detailStarFavourites(title: String) = assertStar(firstStar(), false, "favourite", itemId(title))

    @Then("the detail view's star unfavourites {string} via htmx")
    fun detailStarUnfavourites(title: String) = assertStar(firstStar(), true, "unfavourite", itemId(title))

    @Then("the deck's star has an id naming the item {string}")
    fun deckStarNamesItem(title: String) = assertIdNames(firstStar(), "deck-star", itemId(title))

    @Then("the detail view's star has an id naming the item {string}")
    fun detailStarNamesItem(title: String) = assertIdNames(firstStar(), "detail-star", itemId(title))

    @Then("the reader's star has an id naming the item {string}")
    fun readerStarNamesItem(title: String) = assertIdNames(firstStar(), "reader-star", itemId(title))

    // ---- the toggle answers --------------------------------------------------------------

    @Then("the response shows the deck's filled star that unfavourites {string} via htmx")
    fun answerDeckFilled(title: String) = assertAnswerStar("deck-star", title, true)

    @Then("the response shows the deck's empty star that favourites {string} via htmx")
    fun answerDeckEmpty(title: String) = assertAnswerStar("deck-star", title, false)

    @Then("the response shows the reader's filled star that unfavourites {string} via htmx")
    fun answerReaderFilled(title: String) = assertAnswerStar("reader-star", title, true)

    @Then("the response shows the detail view's filled star that unfavourites {string} via htmx")
    fun answerDetailFilled(title: String) = assertAnswerStar("detail-star", title, true)

    @Then("the response also updates the {starPlaces} stars for {string} out of band")
    fun answerUpdatesOthers(
        places: List<String>,
        title: String,
    ) {
        val id = itemId(title)
        places.forEach { place ->
            val prefix = PLACE_PREFIX[place] ?: error("no star place '$place'")
            val tag = requireNotNull(starTag("$prefix-$id")) { "no $place star in ${body()}" }
            assertTrue(tag.contains("hx-swap-oob=\"true\""), tag)
        }
    }

    @Then("every out-of-band star in the response is empty")
    fun everyOobStarEmpty() {
        val stars = oobStars()
        assertTrue(stars.isNotEmpty(), body())
        stars.forEach { assertTrue(it.contains("data-favourite=\"false\""), it) }
    }

    @Then("every out-of-band star in the response has an id naming the item {string}")
    fun everyOobStarNamesItem(title: String) {
        val stars = oobStars()
        assertTrue(stars.isNotEmpty(), body())
        stars.forEach { assertTrue(Regex("id=\"$ANY_STAR-${itemId(title)}\"").containsMatchIn(it), it) }
    }

    @Then("no star in the response names the item {string}")
    fun noStarNamesOther(title: String) {
        val id = itemId(title)
        assertFalse(Regex("id=\"$ANY_STAR-$id\"").containsMatchIn(body()), body())
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun body(): String = World.response.body()

    private fun toggle(
        verb: String,
        id: String,
        query: String,
        headers: Map<String, String> = emptyMap(),
    ) {
        World.actionHasRun = true
        World.response = app.postForm("/items/$id/$verb$query", headers)
    }

    private fun itemId(title: String): Long = app.itemId(title)

    private fun firstStar(): String = requireNotNull(STAR.find(body())) { "no star in ${body()}" }.value

    private fun starTag(domId: String): String? = Regex("<form[^>]*id=\"$domId\"[^>]*>").find(body())?.value

    private fun oobStars(): List<String> =
        STAR_FORM
            .findAll(body())
            .map { it.value }
            .filter { it.contains("hx-swap-oob=\"true\"") }
            .toList()

    private fun assertAnswerStar(
        prefix: String,
        title: String,
        favourite: Boolean,
    ) {
        val id = itemId(title)
        val tag = requireNotNull(starTag("$prefix-$id")) { "no $prefix-$id in ${body()}" }
        assertStar(tag, favourite, if (favourite) "unfavourite" else "favourite", id)
    }

    private fun assertStar(
        tag: String,
        favourite: Boolean,
        verb: String,
        id: Long,
    ) {
        assertStarState(tag, favourite)
        assertTrue(tag.contains("hx-post=\"/items/$id/$verb"), tag)
    }

    private fun assertStarState(
        tag: String,
        favourite: Boolean,
    ) = assertTrue(tag.contains("data-favourite=\"$favourite\""), tag)

    private fun assertIdNames(
        tag: String,
        prefix: String,
        id: Long,
    ) = assertTrue(Regex("id=\"$prefix-$id\"").containsMatchIn(tag), tag)

    private companion object {
        val HTMX = mapOf("HX-Request" to "true")
        val STAR = Regex("<form[^>]*class=\"star\"[^>]*>")
        val STAR_FORM = Regex("<form[^>]*>")

        /** The id prefix of any star: the row's, the deck's, the reader's or the detail view's. */
        const val ANY_STAR = "(?:star|deck-star|reader-star|detail-star)"

        /** The star id prefix of each place a star can sit, as the feature names them. */
        val PLACE_PREFIX =
            mapOf(
                "timeline row" to "star",
                "deck" to "deck-star",
                "reader" to "reader-star",
                "detail view" to "detail-star",
            )
    }
}
