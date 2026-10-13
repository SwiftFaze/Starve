package dev.starve.acceptance

import dev.starve.infra.BodySanitizer
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for post-bodies.feature: feeds carrying bodies, and what was stored for them. */
class PostBodySteps {
    private val app = World.app
    private val legacy = LegacyDatabase(app)

    // ---- fixture setup -------------------------------------------------------------------

    @Given("the feed server serves {string} as an RSS 2.0 feed titled {string} whose entry has the content:encoded")
    fun serveContentEncoded(
        path: String,
        title: String,
        html: String,
    ) = serve(path, FeedSpec(title = title, content = html.trim()))

    @Given(
        "the feed server serves {string} as an RSS 2.0 feed titled {string} " +
            "whose entry has the content:encoded {string}",
    )
    fun serveContentEncodedInline(
        path: String,
        title: String,
        html: String,
    ) = serve(path, FeedSpec(title = title, content = html))

    @Given("the feed server serves {string} as an RSS 2.0 feed titled {string} whose entry has the description")
    fun serveDescription(
        path: String,
        title: String,
        html: String,
    ) = serve(path, FeedSpec(title = title, description = html.trim()))

    @Given(
        "the feed server serves {string} as an RSS 2.0 feed titled {string} " +
            "whose entry has the description {string} and the content:encoded {string}",
    )
    fun serveBoth(
        path: String,
        title: String,
        description: String,
        content: String,
    ) = serve(path, FeedSpec(title = title, description = description, content = content))

    @Given(
        "the feed server serves {string} as an RSS 2.0 feed titled {string} " +
            "whose entry has a content:encoded paragraph of {int} characters",
    )
    fun serveLongParagraph(
        path: String,
        title: String,
        length: Int,
    ) = serve(path, FeedSpec(title = title, content = "<p>" + "a".repeat(length) + "</p>"))

    @Given("the feed server serves {string} as an Atom feed titled {string} whose entry has the HTML content {string}")
    fun serveAtomContent(
        path: String,
        title: String,
        html: String,
    ) = serve(path, FeedSpec(atom = true, title = title, content = html))

    @Given("the feed server now serves {string} with the content:encoded {string}")
    fun nowServesContent(
        path: String,
        html: String,
    ) {
        val current = World.feedServer().routeOf(path) as Route.Feed
        serve(path, current.spec.copy(content = html))
    }

    @Given("the stored body of the item of {string} is removed")
    fun removeBody(feed: String) {
        app
            .jdbc()
            .sql("UPDATE items SET body = NULL WHERE source_id = (SELECT id FROM sources WHERE title = ?)")
            .param(feed)
            .update()
    }

    @Given("the database is migrated only up to before post bodies")
    fun migratedBeforePostBodies() = legacy.migrateOnlyTo("5")

    @Given("it holds a source {string} with a stored item titled {string}")
    fun holdsSourceWithItem(
        source: String,
        title: String,
    ) = legacy.hold(source, title)

    // ---- assertions ----------------------------------------------------------------------

    @Then("the stored body of the item of {string} is {string}")
    fun bodyIs(
        feed: String,
        expected: String,
    ) = assertEquals(substituted(expected), bodyOf(feed))

    @Then("the item of {string} has no stored body")
    fun feedItemHasNoBody(feed: String) = assertNull(bodyOf(feed))

    @Then("the item {string} of {string} has no stored body")
    fun itemHasNoBody(
        title: String,
        source: String,
    ) = assertNull(
        app
            .jdbc()
            .sql(
                "SELECT body FROM items i JOIN sources s ON s.id = i.source_id " +
                    "WHERE s.title = ? AND i.title = ?",
            ).params(source, title)
            .query(String::class.java)
            .list()
            .firstOrNull(),
    )

    @Then("the stored body of the item of {string} contains {string}")
    fun bodyContains(
        feed: String,
        text: String,
    ) = assertTrue(bodyOf(feed)!!.contains(substituted(text)), "body lacks '$text': ${bodyOf(feed)}")

    @Then("the stored body of the item of {string} contains no {string}")
    fun bodyContainsNo(
        feed: String,
        text: String,
    ) = assertFalse(bodyOf(feed)!!.contains(text, ignoreCase = true), "body has '$text': ${bodyOf(feed)}")

    @Then("the stored body of the item of {string} contains an image from {string}")
    fun bodyContainsImage(
        feed: String,
        url: String,
    ) = assertTrue(bodyOf(feed)!!.contains("<img src=\"${substituted(url)}\""), bodyOf(feed))

    @Then("the stored body of the item of {string} contains the link {string}")
    fun bodyContainsLink(
        feed: String,
        url: String,
    ) = assertTrue(bodyOf(feed)!!.contains("href=\"${substituted(url)}\""), bodyOf(feed))

    @Then("the stored body of the item of {string} contains the text {string}")
    fun bodyContainsText(
        feed: String,
        text: String,
    ) = assertTrue(Jsoup.parse(bodyOf(feed)!!).text().contains(text), bodyOf(feed))

    @Then("every link in the stored body of the item of {string} has target {string} and rel {string}")
    fun linksTargetAndRel(
        feed: String,
        target: String,
        rel: String,
    ) {
        val links = Jsoup.parse(bodyOf(feed)!!).select("a")
        assertTrue(links.isNotEmpty(), "no links in ${bodyOf(feed)}")
        links.forEach {
            assertEquals(target, it.attr("target"))
            assertEquals(rel, it.attr("rel"))
        }
    }

    @Then("sanitising the stored body of the item of {string} again leaves it unchanged")
    fun sanitisingIsIdempotent(feed: String) {
        val link =
            app
                .jdbc()
                .sql("SELECT link FROM items WHERE source_id = (SELECT id FROM sources WHERE title = ?)")
                .param(feed)
                .query(String::class.java)
                .single()
        assertEquals(bodyOf(feed), BodySanitizer.sanitize(bodyOf(feed), link))
    }

    @Then("the stored body of the item of {string} is at most {int} characters long")
    fun bodyIsAtMost(
        feed: String,
        length: Int,
    ) = assertTrue(bodyOf(feed)!!.length <= length, "body has ${bodyOf(feed)!!.length} characters")

    // ---- helpers -------------------------------------------------------------------------

    private fun serve(
        path: String,
        spec: FeedSpec,
    ) = World.feedServer().route(path, Route.Feed(spec))

    private fun substituted(text: String) = text.replace("<feed server>", World.feedServer().baseUrl)

    private fun bodyOf(feed: String): String? =
        app
            .jdbc()
            .sql("SELECT body FROM items WHERE source_id = (SELECT id FROM sources WHERE title = ?)")
            .param(feed)
            .query(String::class.java)
            .list()
            .firstOrNull()
}
