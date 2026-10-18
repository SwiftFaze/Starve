package dev.starve.acceptance

import dev.starve.application.PollService
import dev.starve.application.SourceRepository
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.Duration
import java.time.Instant

/** Broken sources on Sources and the timeline, and feeds that redirect (broken-sources.feature). */
class BrokenSourceSteps {
    private val app = World.app

    @Given("{string} has added the feed URL {string}")
    fun otherUserAdded(
        username: String,
        path: String,
    ) {
        val url = World.feedServer().baseUrl + path
        app.bean(SourceRepository::class.java).insert(app.userId(username), url, url)
    }

    @Given("the feed server now redirects {string} to {string} with HTTP {int}")
    fun redirect(
        from: String,
        to: String,
        status: Int,
    ) = World.feedServer().route(from, Route.Redirect(status, to))

    @Given("the feed server now redirects {string} through {int} hops of HTTP {int}")
    fun redirectChain(
        from: String,
        hops: Int,
        status: Int,
    ) {
        val feed = requireNotNull(World.feedServer().routeOf(from)) { "nothing served at $from" }
        val end = from.replace(".xml", "-end.xml")
        World.feedServer().route(end, feed)
        val stops = listOf(from) + (1 until hops).map { "/hop-$it.xml" } + end
        stops.zipWithNext().forEach { (here, next) -> redirect(here, next, status) }
    }

    @When("{int} hours pass and the poller runs")
    fun hoursPassAndPoll(hours: Int) {
        app.clock.advance(Duration.ofHours(hours.toLong()))
        app.bean(PollService::class.java).pollDue(app.clock.instant(), Duration.ZERO)
    }

    @Then("the source {string} is failed, not broken")
    fun failedNotBroken(title: String) {
        val row = row(title)
        assertTrue(row.contains("<span class=\"tag\">Failed</span>"), row)
        assertFalse(row.contains("Broken"), row)
    }

    @Then("the source {string} is broken, failing since {int} hours ago")
    fun brokenSince(
        title: String,
        hours: Int,
    ) {
        val row = row(title)
        assertTrue(row.contains("<span class=\"tag\">Broken</span>"), row)
        val since = requireNotNull(SINCE.find(row)) { "no failing-since time in $row" }.groupValues[1]
        val expected = app.clock.instant().minus(Duration.ofHours(hours.toLong()))
        val drift = Duration.between(Instant.parse(since), expected).abs()
        assertTrue(drift < Duration.ofMinutes(1), "failing since $since, expected about $expected")
    }

    @Then("the source {string} shows the error {string}")
    fun showsError(
        title: String,
        error: String,
    ) = assertTrue(row(title).contains(Html.escape(error)), row(title))

    @Then("the source {string} has the feed URL {string}")
    fun hasFeedUrl(
        title: String,
        path: String,
    ) {
        val url = World.feedServer().baseUrl + path
        assertTrue(row(title).contains("<span class=\"url\" title=\"$url\">$url</span>"), row(title))
    }

    @Then("the source {string} says it moved to {string}, already one of my sources")
    fun saysMoved(
        title: String,
        path: String,
    ) {
        val moved = requireNotNull(MOVED.find(row(title))) { "no move note in ${row(title)}" }.value
        val text = moved.replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ").trim()
        assertEquals("Moved to ${World.feedServer().baseUrl}$path, already one of your sources", text)
    }

    @Then("the source {string} does not say it moved")
    fun doesNotSayMoved(title: String) = assertFalse(row(title).contains("class=\"moved\""), row(title))

    @Then("the timeline notice reads {string} and links to {string}")
    fun noticeReads(
        text: String,
        href: String,
    ) {
        val notice = requireNotNull(notice("/feed")) { "no broken-sources notice" }
        assertTrue(notice.startsWith("<a href=\"$href\""), notice)
        assertEquals(text, notice.replace(Regex("<[^>]+>"), "").trim())
        assertEquals(notice, notice("/feed?show=all"))
    }

    @Then("the timeline has no broken-sources notice")
    fun noNotice() = assertEquals(null, notice("/feed"))

    @Then("the Favourites view has no broken-sources notice")
    fun noNoticeOnFavourites() {
        assertEquals(null, notice("/feed?favourites=1"))
        assertEquals(null, notice("/feed?later=1"))
        assertEquals(null, notice("/feed?q=entry"))
    }

    private fun notice(path: String): String? =
        NOTICE
            .find(app.get(path).body())
            ?.groupValues
            ?.get(1)
            ?.replace(Regex("\\s+"), " ")
            ?.trim()

    private fun row(title: String): String {
        val rows =
            app
                .get("/sources")
                .body()
                .split("<li class=\"source\"")
                .drop(1)
        return requireNotNull(rows.firstOrNull { it.contains("data-title=\"${Html.escape(title)}\"") }) {
            "no source titled '$title'"
        }
    }

    private companion object {
        val SINCE = Regex("""failing since <time datetime="([^"]+)"""")
        val MOVED = Regex("""<p class="moved"[^>]*>.*?</p>""", RegexOption.DOT_MATCHES_ALL)
        val NOTICE = Regex("""<p class="broken-note">(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    }
}
