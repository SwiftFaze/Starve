package dev.starve.infra

import dev.starve.acceptance.FeedServer
import dev.starve.acceptance.Route
import dev.starve.application.Oembed
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.Duration

class SoundCloudOembedClientTest {
    private val track = "https://soundcloud.com/artist/track"
    private lateinit var server: FeedServer

    @BeforeEach
    fun setUp() {
        server = FeedServer()
    }

    @AfterEach
    fun tearDown() = server.stop()

    private fun client(timeout: Duration = Duration.ofSeconds(5)) =
        SoundCloudOembedClient(
            BoundedHttp(HttpClient.newHttpClient(), timeout, "test-agent"),
            "${server.baseUrl}${FeedServer.SOUNDCLOUD_OEMBED_PATH}",
        )

    private fun answer(json: String) = server.routeSoundCloud(track, Route.Json(json))

    @Test
    fun `the details come from the json, with the description sanitised and the html field ignored`() {
        answer(
            """{"author_name":"Artist","thumbnail_url":"https://i1.example/a.jpg",""" +
                """"description":"<p>Fine</p><script>alert(1)</script>","html":"<iframe src=x></iframe>"}""",
        )

        assertEquals(Oembed("Artist", "https://i1.example/a.jpg", "<p>Fine</p>"), client().fetch(track))
    }

    @Test
    fun `the track is asked for as the url query in json`() {
        answer("""{"author_name":"Artist"}""")

        client().fetch(track)

        assertEquals("url=$track&format=json", server.requests.single().query)
        assertEquals("test-agent", server.requests.single().userAgent)
    }

    @Test
    fun `a document without a description has none`() {
        answer("""{"author_name":"Artist"}""")

        assertEquals(Oembed("Artist", null, null), client().fetch(track))
    }

    @Test
    fun `a thumbnail that is not a web link is dropped`() {
        answer("""{"author_name":"Artist","thumbnail_url":"javascript:alert(1)"}""")

        assertEquals(Oembed("Artist", null, null), client().fetch(track))
    }

    @Test
    fun `an error status gives no details`() {
        listOf(404, 500).forEach {
            server.routeSoundCloud(track, Route.Status(it))
            assertNull(client().fetch(track), "status $it")
        }
    }

    @Test
    fun `a body that is not a JSON object gives no details`() {
        answer("<html>not a JSON document</html>")
        assertNull(client().fetch(track))

        answer("[1, 2]")
        assertNull(client().fetch(track))
    }

    @Test
    fun `a stalled endpoint gives no details once the timeout passes`() {
        server.routeSoundCloud(track, Route.Stall)

        assertNull(client(Duration.ofMillis(500)).fetch(track))
    }
}
