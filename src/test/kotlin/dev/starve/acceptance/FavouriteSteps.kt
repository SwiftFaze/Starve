package dev.starve.acceptance

import dev.starve.application.FavouriteRepository
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.URI
import java.time.Duration
import java.time.Instant

/** Steps for favourites.feature: the star toggles, the Favourites view, retention and the upgrade. */
class FavouriteSteps {
    private val app = World.app
    private val legacy = LegacyDatabase(app)

    @Before
    fun resetAction() {
        World.actionHasRun = false
    }

    // ---- fixture setup -------------------------------------------------------------------

    /** Also an arrangement before the first action, and an assertion after it (see [World.actionHasRun]). */
    @Given("the item {string} is a favourite")
    fun itemIsFavourite(title: String) {
        if (World.actionHasRun) {
            assertTrue(isFavourite(title), "'$title' is not a favourite")
        } else {
            favourite(itemId(title))
        }
    }

    @Given("every item is a favourite")
    fun everyItemIsFavourite() {
        app
            .jdbc()
            .sql("UPDATE items SET favourited_at = ?")
            .param(Instant.now().toEpochMilli())
            .update()
    }

    @Given("the item first seen {int} days ago is a favourite")
    fun itemFirstSeenAgoIsFavourite(days: Int) = favourite(idFirstSeenDaysAgo(days))

    @Given("the item {string} was first seen {int} days ago")
    fun itemFirstSeenAgo(
        title: String,
        days: Int,
    ) {
        app
            .jdbc()
            .sql("UPDATE items SET first_seen_at = ? WHERE title = ?")
            .params(Instant.now().minus(Duration.ofDays(days.toLong())).toEpochMilli(), title)
            .update()
    }

    @Given("the database is migrated only up to before favourites")
    fun migratedBeforeFavourites() = legacy.migrateOnlyTo("6")

    // ---- actions -------------------------------------------------------------------------

    @When("I favourite the item {string} via htmx")
    fun favouriteViaHtmx(title: String) = toggle("favourite", itemId(title).toString(), HTMX)

    @When("I favourite the item {string} via htmx from the reader")
    fun favouriteFromReader(title: String) = toggle("favourite", itemId(title).toString(), HTMX, "?from=reader")

    @When("I favourite the item {string}")
    fun favouriteWithoutJavascript(title: String) = toggle("favourite", itemId(title).toString())

    @When("I unfavourite the item {string} via htmx")
    fun unfavouriteViaHtmx(title: String) = toggle("unfavourite", itemId(title).toString(), HTMX)

    @When("I unfavourite the item first seen {int} days ago")
    fun unfavouriteFirstSeenAgo(days: Int) = toggle("unfavourite", idFirstSeenDaysAgo(days).toString())

    /** The unknown-item outline: the id is sent exactly as written, so "abc" stays non-numeric. */
    @When("I {word} the unknown item {word} via htmx")
    fun toggleUnknown(
        action: String,
        id: String,
    ) {
        require(action == "favourite" || action == "unfavourite") { "no toggle '$action'" }
        toggle(action, id, HTMX)
    }

    @When("I request the timeline of the group {string} with the Favourites filter")
    fun requestGroupWithFavourites(group: String) {
        val id =
            app
                .jdbc()
                .sql("SELECT id FROM groups WHERE name = ?")
                .param(group)
                .query(Long::class.java)
                .single()
        fetch("/?group=$id&favourites=1")
    }

    // ---- assertions on the toggle responses ----------------------------------------------

    @Then("the response shows a filled star that unfavourites {string} via htmx")
    fun responseShowsFilled(title: String) = assertStar(firstStar(), true, "unfavourite", itemId(title))

    @Then("the response shows an empty star that favourites {string} via htmx")
    fun responseShowsEmpty(title: String) = assertStar(firstStar(), false, "favourite", itemId(title))

    @Then("the response also updates the star of the timeline entry {string} out of band")
    fun responseUpdatesRowOutOfBand(title: String) {
        val tag = requireNotNull(Regex("<form[^>]*id=\"star-${itemId(title)}\"[^>]*>").find(body())) { body() }
        assertTrue(tag.value.contains("hx-swap-oob=\"true\""), tag.value)
    }

    @Then("the response does not ask a video in the pane to step down")
    fun responseKeepsVideoPlacement() = assertFalse(body().contains("data-step-video"), body())

    @Then("the response redirects to {string}")
    fun responseRedirectsTo(target: String) {
        val location =
            World.response
                .headers()
                .firstValue("Location")
                .orElse(null)
        assertEquals(target, location?.let { URI.create(it).path }, "Location: $location")
    }

    // ---- assertions on the timeline ------------------------------------------------------

    @Then("the timeline entry {string} has an empty star")
    fun entryHasEmptyStar(title: String) = assertStarState(entryStar(title), false)

    @Then("the timeline entry {string} has a filled star")
    fun entryHasFilledStar(title: String) = assertStarState(entryStar(title), true)

    @Then("the star of {string} favourites it via htmx without a page reload")
    fun starFavouritesViaHtmx(title: String) {
        val tag = entryStar(title)
        assertTrue(tag.contains("hx-post=\"/items/${itemId(title)}/favourite\""), tag)
    }

    @Then("the page has no {string} control")
    fun pageHasNoControl(text: String) = assertFalse(body().contains(text), "page has '$text'")

    @Then("the item {string} is not a favourite")
    fun itemIsNotFavourite(title: String) = assertFalse(isFavourite(title), "'$title' is a favourite")

    @Then("the item {string} no longer exists")
    fun itemNoLongerExists(title: String) = assertEquals(0L, countItems(title))

    @Then("the item {string} still exists")
    fun itemStillExists(title: String) = assertEquals(1L, countItems(title))

    @Then("the items first seen {int}, {int} and {int} days ago still exist")
    fun itemsRemain(
        first: Int,
        second: Int,
        third: Int,
    ) = listOf(first, second, third).forEach {
        assertEquals(1L, count("SELECT COUNT(*) FROM items WHERE id = ?", idFirstSeenDaysAgo(it)), "$it days ago")
    }

    // ---- assertions on the reader --------------------------------------------------------

    @Then("the reader has an empty star")
    fun readerHasEmptyStar() = assertTrue(readerStar().contains("data-favourite=\"false\""), readerStar())

    @Then("the reader has a filled star")
    fun readerHasFilledStar() = assertTrue(readerStar().contains("data-favourite=\"true\""), readerStar())

    @Then("the reader's star favourites {string} via htmx")
    fun readerStarFavourites(title: String) = assertStar(readerStar(), false, "favourite", itemId(title))

    @Then("the reader's star unfavourites {string} via htmx")
    fun readerStarUnfavourites(title: String) = assertStar(readerStar(), true, "unfavourite", itemId(title))

    // ---- assertions on the navigation ----------------------------------------------------

    @Then("the navigation ends with the {string} entry linking to {string}")
    fun navigationEndsWith(
        label: String,
        url: String,
    ) {
        val last = navAnchors().last()
        assertTrue(last.first.contains("class=\"favourites-link\""), last.first)
        assertEquals(label, last.second)
        assertTrue(last.first.contains("href=\"$url\""), last.first)
    }

    @Then("the {string} entry follows the group {string}")
    fun entryFollowsGroup(
        label: String,
        group: String,
    ) {
        val anchors = navAnchors()
        val index = anchors.indexOfFirst { it.second == label }
        assertTrue(index > 0, "no '$label' entry")
        val previous = anchors[index - 1]
        val isGroup = previous.first.contains("class=\"group-link\"") && previous.second.startsWith("$group (")
        assertTrue(isGroup, previous.second)
    }

    @Then("the {string} entry is marked as current")
    fun entryMarkedCurrent(label: String) {
        val tag = requireNotNull(navAnchors().firstOrNull { it.second == label }) { "no '$label' entry" }.first
        assertTrue(tag.contains("aria-current=\"page\""), tag)
    }

    @Then("no group entry is marked as current")
    fun noGroupEntryCurrent() =
        assertEquals(
            emptyList<String>(),
            navAnchors()
                .filter {
                    it.first.contains("class=\"group-link\"") &&
                        it.first.contains("aria-current")
                }.map { it.second },
        )

    // ---- helpers -------------------------------------------------------------------------

    private fun body(): String = World.response.body()

    private fun toggle(
        verb: String,
        id: String,
        headers: Map<String, String> = emptyMap(),
        query: String = "",
    ) {
        World.actionHasRun = true
        World.response = app.postForm("/items/$id/$verb$query", headers)
    }

    private fun fetch(path: String) {
        World.response = app.get(path)
        World.pages = listOf(World.response.body())
    }

    private fun favourite(id: Long) = app.bean(FavouriteRepository::class.java).favourite(id, Instant.now())

    private fun firstStar(): String = requireNotNull(STAR.find(body())) { "no star in ${body()}" }.value

    private fun readerStar(): String =
        requireNotNull(Regex("<form[^>]*id=\"reader-star\"[^>]*>").find(body())) { "no reader star in ${body()}" }.value

    private fun entryStar(title: String): String =
        requireNotNull(STAR.find(World.entry(title))) { "no star in ${World.entry(title)}" }.value

    private fun assertStar(
        tag: String,
        favourite: Boolean,
        verb: String,
        id: Long,
    ) {
        assertTrue(tag.contains("data-favourite=\"$favourite\""), tag)
        assertTrue(tag.contains("hx-post=\"/items/$id/$verb"), tag)
    }

    private fun navAnchors(): List<Pair<String, String>> {
        val nav = requireNotNull(GROUPS_NAV.find(body())) { "no groups navigation" }.value
        return ANCHOR.findAll(nav).map { it.groupValues[1] to it.groupValues[2] }.toList()
    }

    private fun isFavourite(title: String): Boolean =
        count("SELECT COUNT(*) FROM items WHERE title = ? AND favourited_at IS NOT NULL", title) == 1L

    private fun countItems(title: String): Long = count("SELECT COUNT(*) FROM items WHERE title = ?", title)

    private fun assertStarState(
        tag: String,
        favourite: Boolean,
    ) = assertTrue(tag.contains("data-favourite=\"$favourite\""), tag)

    private fun itemId(title: String): Long =
        app
            .jdbc()
            .sql("SELECT id FROM items WHERE title = ?")
            .param(title)
            .query(Long::class.java)
            .single()

    /** The item first seen [days] ago; the backdating steps spread items a few days apart, so an hour is plenty. */
    private fun idFirstSeenDaysAgo(days: Int): Long {
        val at = Instant.now().minus(Duration.ofDays(days.toLong())).toEpochMilli()
        val hour = Duration.ofHours(1).toMillis()
        return app
            .jdbc()
            .sql("SELECT id FROM items WHERE first_seen_at BETWEEN ? AND ?")
            .params(at - hour, at + hour)
            .query(Long::class.java)
            .single()
    }

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

    private companion object {
        val HTMX = mapOf("HX-Request" to "true")
        val STAR = Regex("<form[^>]*class=\"star\"[^>]*>")
        val GROUPS_NAV = Regex("<nav class=\"groups\".*?</nav>", RegexOption.DOT_MATCHES_ALL)
        val ANCHOR = Regex("(<a [^>]*>)([^<]*)</a>")
    }
}
