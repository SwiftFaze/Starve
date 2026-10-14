package dev.starve.infra

import dev.starve.acceptance.FeedServer
import dev.starve.acceptance.Route
import dev.starve.application.FaviconFinder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.Duration

class HttpFaviconFinderTest {
    private lateinit var server: FeedServer
    private lateinit var finder: FaviconFinder

    @BeforeEach
    fun setUp() {
        server = FeedServer()
        finder = HttpFaviconFinder(BoundedHttp(HttpClient.newHttpClient(), Duration.ofSeconds(10), "test"))
    }

    @AfterEach
    fun tearDown() = server.stop()

    @Test
    fun `an icon link in the page wins over the root favicon`() {
        server.route("/blog", Route.Html("""<html><head><link rel="icon" href="/static/i.png"></head></html>"""))
        server.route("/favicon.ico", Route.Icon)

        assertEquals("${server.baseUrl}/static/i.png", finder.find("${server.baseUrl}/blog"))
    }

    @Test
    fun `a page without an icon link falls back to the root favicon when it is an image`() {
        server.route("/blog", Route.Html("<html><head></head></html>"))
        server.route("/favicon.ico", Route.Icon)

        assertEquals("${server.baseUrl}/favicon.ico", finder.find("${server.baseUrl}/blog"))
    }

    @Test
    fun `an unreadable page still falls back to the root favicon`() {
        server.route("/blog", Route.Status(500))
        server.route("/favicon.ico", Route.Icon)

        assertEquals("${server.baseUrl}/favicon.ico", finder.find("${server.baseUrl}/blog"))
    }

    @Test
    fun `a root favicon that is not an image is not an icon`() {
        server.route("/blog", Route.Html("<html></html>"))
        server.route("/favicon.ico", Route.Html("<html>soft 404</html>"))

        assertNull(finder.find("${server.baseUrl}/blog"))
    }

    @Test
    fun `a missing root favicon is not an icon`() {
        server.route("/blog", Route.Html("<html></html>"))

        assertNull(finder.find("${server.baseUrl}/blog"))
    }

    @Test
    fun `a root favicon answering with an error status is not an icon even when typed as an image`() {
        server.route("/blog", Route.Html("<html></html>"))
        server.route("/favicon.ico", Route.Status(404))

        assertNull(finder.find("${server.baseUrl}/blog"))
    }
}
