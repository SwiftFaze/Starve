package dev.starve.acceptance

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for resume-position.feature: the deck reporting where playback stopped, and resuming there. */
class ResumeSteps {
    private val app = World.app
    private var otherBrowser: ApplicationHarness.Browser? = null

    @Given("the deck reported that {string} stopped at {int} seconds of {int}")
    @When("the deck reports that {string} stopped at {int} seconds of {int}")
    fun stoppedOf(
        title: String,
        at: Int,
        duration: Int,
    ) = report(title, "at" to at.toString(), "duration" to duration.toString())

    @Given("the deck reported that {string} stopped at {int} seconds")
    @When("the deck reports that {string} stopped at {int} seconds")
    fun stopped(
        title: String,
        at: Int,
    ) = report(title, "at" to at.toString())

    @When("I sign in again in another browser")
    fun anotherBrowser() {
        otherBrowser = app.rememberedBrowser(World.OWNER)
    }

    @When("I request the home page {string} in that browser")
    fun requestInOtherBrowser(path: String) {
        World.response = requireNotNull(otherBrowser) { "no other browser" }.get(path, emptyMap())
        World.pages = listOf(World.response.body())
    }

    @Then("the Play control of {string} reads {string}")
    fun playReads(
        title: String,
        label: String,
    ) {
        val play = requireNotNull(PLAY.find(World.entry(title))) { "no Play control: ${World.entry(title)}" }
        assertEquals(label, play.groupValues[2])
        assertTrue(play.groupValues[1].contains("data-label=\"$label\""), play.value)
    }

    @Then("the player resumes at {int} seconds, showing {string}")
    fun resumesAt(
        seconds: Int,
        elapsed: String,
    ) {
        val body = World.response.body()
        assertTrue(body.contains("data-resume=\"$seconds\""), body)
        assertTrue(body.contains("<time class=\"elapsed\">$elapsed</time>"), body)
    }

    @Then("the player starts from the beginning")
    fun fromTheBeginning() {
        val body = World.response.body()
        assertFalse(body.contains("data-resume="), body)
        assertTrue(body.contains("<time class=\"elapsed\">0:00</time>"), body)
    }

    private fun report(
        title: String,
        vararg fields: Pair<String, String>,
    ) {
        World.response = app.postForm("/items/${app.itemId(title)}/position", *fields)
    }

    private companion object {
        val PLAY = Regex("<button type=\"button\" class=\"play\"([^>]*)>([^<]*)</button>")
    }
}
