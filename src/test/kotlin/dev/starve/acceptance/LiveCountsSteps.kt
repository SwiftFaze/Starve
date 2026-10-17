package dev.starve.acceptance

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

class LiveCountsSteps {
    private val app = World.app

    // ---- actions -------------------------------------------------------------------------

    @When("I request the counts of the group {string}")
    fun requestGroupCounts(group: String) = fetch("/feed/counts?group=${app.groupId(group)}")

    @When("I request the counts of the Favourites view")
    fun requestFavouritesCounts() = fetch("/feed/counts?favourites=1")

    // ---- assertions ----------------------------------------------------------------------

    @Then("the response asks the page to refresh its unread counts")
    fun asksForRefresh() = assertEquals("starve:read", trigger())

    @Then("the page refreshes its counts from {string} when an item is read")
    fun refreshesFrom(template: String) {
        val href = GROUP_NAME.replace(template) { app.groupId(it.groupValues[1]).toString() }
        val listener = requireNotNull(LISTENER.find(World.response.body())) { "no counts listener" }.value
        assertTrue(listener.contains("hx-get=\"${href.replace("&", "&amp;")}\""), listener)
        assertTrue(listener.contains("hx-trigger=\"starve:read from:body\""), listener)
    }

    @Then("the counts show {string} on the obi")
    fun obiShows(text: String) = assertEquals(text, obiCount())

    @Then("the counts show no count on the obi")
    fun obiShowsNothing() = assertNull(obiCount())

    // ---- helpers -------------------------------------------------------------------------

    private fun fetch(path: String) {
        World.response = app.get(path, mapOf("HX-Request" to "true"))
    }

    private fun trigger(): String? =
        World.response
            .headers()
            .firstValue("HX-Trigger")
            .orElse(null)

    private fun obiCount(): String? = OBI_COUNT.find(World.response.body())?.groupValues?.get(1)

    private companion object {
        val GROUP_NAME = Regex("\\{([^}]+)}")
        val LISTENER = Regex("<[^>]*id=\"counts\"[^>]*>")
        val OBI_COUNT = Regex("<span[^>]*id=\"series-unread\"[^>]*>([^<]*)</span>")
    }
}
