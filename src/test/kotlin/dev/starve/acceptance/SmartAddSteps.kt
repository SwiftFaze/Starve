package dev.starve.acceptance

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals

class SmartAddSteps {
    private val app = World.app

    @Given("the feed server serves {string} as an Atom feed titled {string} with {int} entries")
    fun serveAtom(
        path: String,
        title: String,
        entries: Int,
    ) = World.feedServer().route(path, Route.Feed(FeedSpec(atom = true, title = title, entries = entries)))

    @Given(
        "the feed server serves {string} as an HTML page " +
            "whose head links the alternate feed {string} of type {string}",
    )
    fun serveHtmlWithAlternate(
        path: String,
        href: String,
        type: String,
    ) = serveHtml(path, alternate(href, type))

    @Given("the feed server serves {string} as an HTML page whose head links the alternate feeds {string} and {string}")
    fun serveHtmlWithTwoAlternates(
        path: String,
        first: String,
        second: String,
    ) = serveHtml(path, alternate(first, RSS) + alternate(second, RSS))

    @Given("the feed server serves {string} as an HTML page without any feed link")
    fun serveHtmlWithoutFeedLink(path: String) = serveHtml(path, """<link rel="stylesheet" href="/site.css">""")

    @Given("the feed server serves {string} as a SoundCloud profile page for user id {string}")
    fun serveSoundCloudProfile(
        path: String,
        userId: String,
    ) = serveHtml(path, """<meta property="al:ios:url" content="soundcloud://users:$userId">""")

    @Given("the feed server serves {string} as malformed XML")
    fun serveMalformedXml(path: String) = World.feedServer().route(path, Route.Raw("malformed.xml", "application/xml"))

    @Given("SoundCloud is configured to use the feed server as its host")
    fun soundCloudUsesFeedServer() {
        val server = World.feedServer()
        app.restartWith(
            "--starve.soundcloud.hosts=localhost",
            "--starve.soundcloud.feed-url-template=${server.baseUrl}/feeds/users/soundcloud:users:{id}/sounds.rss",
        )
    }

    @Then("the source {string} is subscribed at the feed URL {string}")
    fun subscribedAt(
        title: String,
        path: String,
    ) {
        val url =
            app
                .jdbc()
                .sql("SELECT url FROM sources WHERE title = ?")
                .param(title)
                .query(String::class.java)
                .single()
        assertEquals(World.feedServer().baseUrl + path, url)
    }

    private fun alternate(
        href: String,
        type: String,
    ) = """<link rel="alternate" type="$type" href="$href">"""

    private fun serveHtml(
        path: String,
        head: String,
    ) = World.feedServer().route(path, Route.Html("<!DOCTYPE html><html><head>$head</head><body></body></html>"))

    private companion object {
        const val RSS = "application/rss+xml"
    }
}
