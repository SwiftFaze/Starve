package dev.starve.acceptance

import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

class EndOfListSteps {
    @Then("the list ends with {string}")
    fun listEndsWith(text: String) = assertEquals(text, endMarker()?.let(::textOf))

    @Then("the list has no end marker")
    fun noEndMarker() = assertNull(endMarker())

    @Then("the end marker refreshes from {string} when an item is read")
    fun endMarkerRefreshes(href: String) {
        val marker = requireNotNull(endMarker()) { "no end marker" }
        assertTrue(marker.contains("hx-get=\"$href\""), marker)
        assertTrue(marker.contains("hx-trigger=\"starve:read from:body\""), marker)
    }

    /** The end marker of the latest response, from its opening tag to its closing one. */
    private fun endMarker(): String? = END.find(World.response.body())?.value

    private fun textOf(marker: String) = TAG.replace(marker, "").replace(SPACE, " ").trim()

    private companion object {
        val END = Regex("<p[^>]*class=\"list-end\"[^>]*>.*?</p>", RegexOption.DOT_MATCHES_ALL)
        val TAG = Regex("<[^>]+>")
        val SPACE = Regex("\\s+")
    }
}
