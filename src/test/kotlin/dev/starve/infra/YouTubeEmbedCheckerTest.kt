package dev.starve.infra

import dev.starve.acceptance.FeedServer
import dev.starve.acceptance.Route
import dev.starve.domain.Playable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.Duration

class YouTubeEmbedCheckerTest {
    private val video = Playable.YouTube("dQw4w9WgXcQ")
    private lateinit var server: FeedServer
    private lateinit var checker: YouTubeEmbedChecker

    @BeforeEach
    fun setUp() {
        server = FeedServer()
        checker = checkerFor(server.baseUrl, "test-agent")
    }

    @AfterEach
    fun tearDown() = server.stop()

    @Test
    fun `an ok answer allows embedding and the canonical watch url is queried`() {
        server.routeOembed("dQw4w9WgXcQ", 200)

        assertTrue(checker.allowsEmbedding(video))

        val query = server.requests.single().query
        assertEquals("url=https://www.youtube.com/watch?v=dQw4w9WgXcQ&format=json", query)
        assertEquals("test-agent", server.requests.single().userAgent)
    }

    @Test
    fun `401 and 403 disable embedding`() {
        server.routeOembed("dQw4w9WgXcQ", 401)
        assertFalse(checker.allowsEmbedding(video))
        server.routeOembed("dQw4w9WgXcQ", 403)
        assertFalse(checker.allowsEmbedding(video))
    }

    @Test
    fun `other statuses fail open`() {
        listOf(404, 500, 503).forEach {
            server.routeOembed("dQw4w9WgXcQ", it)
            assertTrue(checker.allowsEmbedding(video), "status $it")
        }
    }

    @Test
    fun `a stalled endpoint fails open once the timeout passes`() {
        server.route("/oembed/dQw4w9WgXcQ", Route.Stall)
        val started = System.nanoTime()

        assertTrue(checker.allowsEmbedding(video))
        assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(3))
    }

    @Test
    fun `an unreachable endpoint fails open`() {
        val dead = FeedServer().also { it.stop() }

        assertTrue(checkerFor(dead.baseUrl, "t").allowsEmbedding(video))
    }

    private fun checkerFor(
        baseUrl: String,
        agent: String,
    ): YouTubeEmbedChecker {
        val http = BoundedHttp(HttpClient.newHttpClient(), Duration.ofMillis(500), agent)
        return YouTubeEmbedChecker(http, "$baseUrl/oembed")
    }
}
