package dev.starve.acceptance

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.Instant

/** The Sources page's fetch times, obi meta and add-source hint (sources-status-fit.feature). */
class SourcesStatusSteps {
    private val app = World.app

    @Given("the source {string} was last fetched at {string}")
    fun lastFetchedAt(
        title: String,
        instant: String,
    ) {
        val id = app.ownedSource(title).id
        app
            .jdbc()
            .sql("UPDATE sources SET last_fetched_at = ? WHERE id = ?")
            .params(Instant.parse(instant).toEpochMilli().toString(), id)
            .update()
    }

    @Then("the source {string} says {string}")
    fun sourceSays(
        title: String,
        text: String,
    ) = assertEquals(text, textOf(group(fetchedLine(title), FETCHED)))

    @Then("the fetch time of {string} is a time element for {string} titled {string}")
    fun fetchTimeElement(
        title: String,
        instant: String,
        full: String,
    ) {
        val time = group(fetchedLine(title), TIME)
        assertTrue(time.contains("datetime=\"$instant\""), time)
        assertTrue(time.contains("title=\"$full\""), time)
    }

    @Then("the Sources meta reads {string}")
    fun metaReads(text: String) = assertEquals(text, textOf(group(body(), META)))

    @Then("the add-source field's placeholder is {string}")
    fun placeholder(text: String) = assertTrue(group(body(), ADD_FIELD).contains("placeholder=\"$text\""))

    @Then("the add-source field is described by the hint {string}")
    fun describedBy(text: String) {
        val field = group(body(), ADD_FIELD)
        val hintId = requireNotNull(DESCRIBED_BY.find(field)) { "no aria-describedby on $field" }.groupValues[1]
        val hint = Regex("<p [^>]*id=\"$hintId\"[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
        assertEquals(text, textOf(group(body(), hint)))
    }

    private fun fetchedLine(title: String): String {
        val row =
            Regex(
                "<li class=\"source\" data-title=\"${Regex.escape(title)}\">(.*?)</li>",
                RegexOption.DOT_MATCHES_ALL,
            )
        return group(body(), row)
    }

    private fun body(): String = World.response.body()

    private fun group(
        text: String,
        pattern: Regex,
    ): String = requireNotNull(pattern.find(text)) { "no $pattern in $text" }.groupValues[1]

    private fun textOf(html: String): String = html.replace(TAG, "").replace(SPACE, " ").trim()

    private companion object {
        val FETCHED = Regex("<span class=\"last-fetched\">(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
        val TIME = Regex("(<time [^>]*>)")
        val META = Regex("<p class=\"series-meta\">(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
        val ADD_FIELD = Regex("(<input [^>]*id=\"add-url\"[^>]*>)")
        val DESCRIBED_BY = Regex("aria-describedby=\"([^\"]+)\"")
        val TAG = Regex("<[^>]+>")
        val SPACE = Regex("\\s+")
    }
}
