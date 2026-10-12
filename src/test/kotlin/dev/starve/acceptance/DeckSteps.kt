package dev.starve.acceptance

import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for player-deck.feature and theme-switch.feature: deck controls, waveform, placements, splitter, theme. */
class DeckSteps {
    private val app = World.app

    @Then("the response has a play\\/pause control, a seek control and a volume control")
    fun hasTransport() {
        listOf(PAUSE, SEEK, VOLUME).forEach { assertTrue(it.containsMatchIn(body()), "no ${it.pattern} in ${body()}") }
    }

    @Then("the response shows elapsed and total time")
    fun showsTimes() {
        assertTrue(body().contains("class=\"elapsed\""), body())
        assertTrue(body().contains("class=\"total\""), body())
    }

    @Then("the response draws a waveform of {int} bars")
    fun drawsWaveform(bars: Int) = assertEquals(bars, waveform(body()).size, body())

    @Then("requesting the player of {string} again draws the same waveform")
    fun sameWaveformAgain(title: String) {
        val first = waveform(body())
        val id =
            app
                .jdbc()
                .sql("SELECT id FROM items WHERE title = ?")
                .param(title)
                .query(Long::class.java)
                .single()
        val again = app.get("/items/$id/player", mapOf("HX-Request" to "true")).body()
        assertEquals(first, waveform(again))
    }

    @Then("the response offers the video placements {string}, {string} and {string}")
    fun offersPlacements(
        first: String,
        second: String,
        third: String,
    ) {
        val labels = PLACEMENT.findAll(body()).map { it.groupValues[2] }.toList()
        assertEquals(listOf(first, second, third), labels, body())
    }

    @Then("the placement {string} is selected")
    fun placementSelected(label: String) {
        val pressed =
            PLACEMENT
                .findAll(body())
                .filter { it.groupValues[1] == "true" }
                .map { it.groupValues[2] }
                .toList()
        assertEquals(listOf(label), pressed, body())
    }

    @Then("the response offers no video placement")
    fun offersNoPlacement() = assertFalse(body().contains("class=\"placement\""), body())

    @Then("the timeline has a list splitter")
    fun hasSplitter() = assertTrue(SPLITTER.containsMatchIn(body()), body())

    @Then("the page loads the deck script")
    fun loadsDeckScript() = assertTrue(body().contains("<script src=\"/deck.js\""), body())

    @Then("the page has a theme switch")
    fun hasThemeSwitch() = assertTrue(THEME_SWITCH.containsMatchIn(body()), body())

    @Then("the page loads the theme script before its stylesheet")
    fun loadsThemeFirst() {
        val script = body().indexOf("<script src=\"/theme.js\"></script>")
        val sheet = body().indexOf("<link rel=\"stylesheet\" href=\"/starve.css\">")
        assertTrue(script in 0 until sheet, body())
    }

    private fun body(): String = World.response.body()

    /** The bar heights of the first (unlit) waveform layer. */
    private fun waveform(page: String): List<String> {
        val layer = requireNotNull(BARS.find(page)) { "no waveform in $page" }.value
        return BAR_HEIGHT.findAll(layer).map { it.groupValues[1] }.toList()
    }

    private companion object {
        val PAUSE = Regex("<button[^>]*class=\"pause\"")
        val SEEK = Regex("<input[^>]*class=\"seek\"[^>]*type=\"range\"")
        val VOLUME = Regex("<input[^>]*class=\"level\"[^>]*type=\"range\"")
        val BARS = Regex("<svg class=\"bars\".*?</svg>", RegexOption.DOT_MATCHES_ALL)
        val BAR_HEIGHT = Regex("<rect[^>]*height=\"(\\d+)\"")
        val PLACEMENT =
            Regex("<button[^>]*data-place=\"[a-z]+\"[^>]*aria-pressed=\"(true|false)\"[^>]*>([^<]+)</button>")
        val SPLITTER = Regex("<div class=\"splitter\"[^>]*role=\"separator\"")
        val THEME_SWITCH = Regex("<button type=\"button\" class=\"theme-switch\"")
    }
}
