package dev.starve.infra

import dev.starve.acceptance.FeedServer
import dev.starve.acceptance.FeedSpec
import dev.starve.acceptance.Route
import dev.starve.application.FeedLocator
import dev.starve.application.LocateResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.Duration

class HttpFeedLocatorTest {
    private lateinit var server: FeedServer
    private lateinit var locator: FeedLocator

    @BeforeEach
    fun setUp() {
        server = FeedServer()
        val template = "${server.baseUrl}/sc/{id}.rss"
        val http = BoundedHttp(HttpClient.newHttpClient(), Duration.ofSeconds(10), "test")
        locator = HttpFeedLocator(http, setOf("localhost"), template)
    }

    @AfterEach
    fun tearDown() = server.stop()

    private fun locate(path: String) = locator.locate(server.baseUrl + path)

    @Test
    fun `a feed is located as itself`() {
        server.route("/f.xml", Route.Feed(FeedSpec()))
        assertEquals(LocateResult.Found(server.baseUrl + "/f.xml"), locate("/f.xml"))
    }

    @Test
    fun `non html non feed content is left for the feed fetch to reject`() {
        server.route("/bad.xml", Route.Raw("malformed.xml", "application/xml"))
        assertEquals(LocateResult.Found(server.baseUrl + "/bad.xml"), locate("/bad.xml"))
    }

    @Test
    fun `an html page is located through its first alternate link`() {
        val link = """<link rel="alternate" type="application/atom+xml" href="a.xml">"""
        server.route("/d/page", Route.Html("<html><head>$link</head></html>"))
        assertEquals(LocateResult.Found(server.baseUrl + "/d/a.xml"), locate("/d/page"))
    }

    @Test
    fun `a soundcloud profile page without an alternate link uses the template`() {
        server.route("/artist", Route.Html("<html><meta content=\"soundcloud://users:42\"></html>"))
        assertEquals(LocateResult.Found(server.baseUrl + "/sc/42.rss"), locate("/artist"))
    }

    @Test
    fun `an alternate link wins over the soundcloud marker`() {
        val link = """<link rel="alternate" type="application/rss+xml" href="/own.xml">"""
        server.route("/artist", Route.Html("<html>$link soundcloud://users:42</html>"))
        assertEquals(LocateResult.Found(server.baseUrl + "/own.xml"), locate("/artist"))
    }

    @Test
    fun `a page with nothing to resolve has no feed`() {
        server.route("/plain", Route.Html("<html></html>"))
        assertEquals(LocateResult.NoFeed, locate("/plain"))
    }

    @Test
    fun `the highest success status is still located`() {
        server.route("/edge", Route.Status(299))
        assertEquals(LocateResult.Found(server.baseUrl + "/edge"), locate("/edge"))
    }

    @Test
    fun `error statuses and stalls are unreachable`() {
        server.route("/gone", Route.Status(404))
        server.route("/slow", Route.StallBody)
        assertEquals(LocateResult.Unreachable, locate("/gone"))
        assertEquals(LocateResult.Unreachable, locate("/slow"))
    }
}
