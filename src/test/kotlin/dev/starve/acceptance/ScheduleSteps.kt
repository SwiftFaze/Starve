package dev.starve.acceptance

import dev.starve.application.PollService
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.core.env.Environment
import java.time.Duration

/** The interval selector, the scheduler tick and Fetch now on Sources (per-source-interval.feature). */
class ScheduleSteps {
    private val app = World.app

    @When("I set the interval of {string} to {string}")
    fun setInterval(
        title: String,
        interval: String,
    ) {
        World.response = app.postForm("/sources/${app.sourceId(title)}/interval", "interval" to interval)
    }

    @Given("I have set the interval of {string} to {string}")
    fun haveSetInterval(
        title: String,
        interval: String,
    ) {
        setInterval(title, interval)
        assertEquals(REDIRECT, World.response.statusCode(), World.response.body())
    }

    @Given("the feed server forgets the requests so far")
    fun forgetRequests() = World.feedServer().clearRequests()

    @When("{int} minutes pass and the scheduler ticks")
    fun minutesPassAndTick(minutes: Int) = passAndTick(Duration.ofMinutes(minutes.toLong()))

    @When("{int} hours pass and the scheduler ticks")
    fun hoursPassAndTick(hours: Int) = passAndTick(Duration.ofHours(hours.toLong()))

    @When("I press Fetch now on {string} via htmx")
    fun fetchNowViaHtmx(title: String) {
        World.response = app.postForm("/sources/${app.sourceId(title)}/fetch", HTMX)
    }

    @When("I press Fetch now on {string}")
    fun fetchNow(title: String) {
        World.response = app.postForm("/sources/${app.sourceId(title)}/fetch")
    }

    @When("the background fetches complete")
    fun backgroundFetchesComplete() = app.importRunner.runPending()

    @When("I request the row of {string}")
    fun requestRow(title: String) {
        World.response = app.get("/sources/${app.sourceId(title)}/row", HTMX)
    }

    @Then("the interval choices of {string} are {string}")
    fun intervalChoices(
        title: String,
        choices: String,
    ) = assertEquals(choices, OPTION.findAll(select(title)).joinToString(", ") { it.groupValues[2].trim() })

    @Then("the interval of {string} reads {string}")
    fun intervalReads(
        title: String,
        label: String,
    ) {
        World.response = app.get("/sources")
        val options = OPTION.findAll(select(title)).toList()
        val shown = options.firstOrNull { "selected" in it.groupValues[1] } ?: options.first()
        assertEquals(label, shown.groupValues[2].trim())
    }

    @Then("the response is the row of {string} saying {string}")
    fun responseIsRow(
        title: String,
        text: String,
    ) {
        val body = World.response.body().trim()
        assertTrue(body.startsWith("<li class=\"source\""), body)
        assertTrue(body.contains("data-title=\"${Html.escape(title)}\""), body)
        assertTrue(body.contains(text), body)
        assertFalse(body.contains("<html"), body)
    }

    @Then("the row refreshes itself from {string} every 2 seconds")
    fun rowRefreshes(path: String) {
        val tag = requireNotNull(ROW.find(World.response.body())) { "no row" }.value
        val expected = path.replace(Regex("<id of (.+)>")) { app.sourceId(it.groupValues[1]).toString() }
        assertTrue(tag.contains("hx-get=\"$expected\""), tag)
        assertTrue(tag.contains("hx-trigger=\"every 2s\""), tag)
        assertTrue(tag.contains("hx-swap=\"outerHTML\""), tag)
    }

    @Then("the row of {string} does not refresh itself")
    fun rowDoesNotRefresh(title: String) = assertFalse(rowTag(title).contains("hx-get"), rowTag(title))

    @Then("the row of {string} shows the error {string}")
    fun rowShowsError(
        title: String,
        error: String,
    ) = assertTrue(rowTag(title).isNotEmpty() && World.response.body().contains(error), World.response.body())

    @Then("the Fetch now button of {string} is disabled")
    fun fetchNowDisabled(title: String) = assertTrue(fetchButton(title).contains("disabled"), fetchButton(title))

    @Then("the Fetch now button of {string} is enabled")
    fun fetchNowEnabled(title: String) = assertFalse(fetchButton(title).contains("disabled"), fetchButton(title))

    private fun passAndTick(time: Duration) {
        app.clock.advance(time)
        val environment = app.bean(Environment::class.java)
        val default = environment.getProperty("starve.poll.interval", Duration::class.java, DEFAULT)
        app.bean(PollService::class.java).pollDue(app.clock.instant(), default)
    }

    private fun select(title: String): String {
        val name = Regex.escape(Html.escape("How often to fetch $title"))
        val pattern = Regex("<select [^>]*aria-label=\"$name\"[^>]*>(.*?)</select>", RegexOption.DOT_MATCHES_ALL)
        return requireNotNull(pattern.find(World.response.body())) { "no interval selector for $title" }.groupValues[1]
    }

    private fun rowTag(title: String): String {
        val tag = requireNotNull(ROW.find(World.response.body())) { "no row" }.value
        assertTrue(tag.contains("data-title=\"${Html.escape(title)}\""), tag)
        return tag
    }

    private fun fetchButton(title: String): String {
        val label = Regex.escape(Html.escape("Fetch $title now"))
        val button = Regex("<button[^>]*aria-label=\"$label\"[^>]*>")
        return requireNotNull(button.find(World.response.body())) { "no Fetch now button for $title" }.value
    }

    private companion object {
        val HTMX = mapOf("HX-Request" to "true")
        val OPTION = Regex("<option([^>]*)>([^<]*)</option>")
        val ROW = Regex("<li class=\"source\"[^>]*>")
        val DEFAULT: Duration = Duration.ofMinutes(30)
        const val REDIRECT = 302
    }
}
