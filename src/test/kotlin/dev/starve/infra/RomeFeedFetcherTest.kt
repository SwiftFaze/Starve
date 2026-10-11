package dev.starve.infra

import dev.starve.acceptance.FeedServer
import dev.starve.acceptance.Route
import dev.starve.application.FeedFetchException
import dev.starve.application.FeedFetcher
import dev.starve.application.FeedIngestor
import dev.starve.application.FetchResult
import dev.starve.application.InvalidFeedException
import dev.starve.application.PollService
import dev.starve.application.SourceRepository
import dev.starve.domain.FeedItem
import dev.starve.domain.Source
import dev.starve.domain.Validators
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import dev.starve.application.MemoryItems as BaseMemoryItems

class RomeFeedFetcherTest {
    private lateinit var server: FeedServer
    private lateinit var fetcher: FeedFetcher

    @BeforeEach
    fun setUp() {
        server = FeedServer()
        fetcher = RomeFeedFetcher(BoundedHttp(HttpClient.newHttpClient(), Duration.ofMillis(500), "test"))
    }

    @AfterEach
    fun tearDown() = server.stop()

    @Test
    fun `a body that stalls after the headers times out as a fetch error`() {
        server.route("/slow.xml", Route.StallBody)
        val started = System.nanoTime()
        assertThrows(FeedFetchException::class.java) { fetcher.fetch("${server.baseUrl}/slow.xml", Validators()) }
        assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(3))
    }

    @Test
    fun `a stalled source records an error and does not stop other sources polling`() {
        server.route("/slow.xml", Route.StallBody)
        server.route("/ok.xml", Route.Feed(dev.starve.acceptance.FeedSpec(title = "Ok", entries = 2)))
        val repo = MemorySources(listOf("${server.baseUrl}/slow.xml", "${server.baseUrl}/ok.xml"))
        val items = MemoryItems()
        val ingestor = FeedIngestor(repo, items, Clock.systemUTC())

        PollService(repo, fetcher, ingestor).pollAll()

        assertTrue(repo.errors[1L]?.isNotBlank() == true)
        assertEquals(null, repo.errors[2L])
        assertEquals(2, items.stored)
    }

    @Test
    fun `fetch returns the parsed feed when the server answers in time`() {
        server.route("/ok.xml", Route.Feed(dev.starve.acceptance.FeedSpec(title = "Ok", entries = 1)))
        val result = fetcher.fetch("${server.baseUrl}/ok.xml", Validators()) as FetchResult.Fetched
        assertEquals("Ok", result.feed.title)
    }

    @Test
    fun `entries are mapped with trimmed titles, update dates, authors and thumbnails`() {
        server.route("/rich.xml", Route.Raw("atom-rich.xml", "application/atom+xml"))

        val feed = (fetcher.fetch("${server.baseUrl}/rich.xml", Validators()) as FetchResult.Fetched).feed

        assertEquals("Spaced Title", feed.title)
        val (first, second, third) = feed.items
        assertEquals("First", first.title)
        assertEquals("http://example.test/1", first.link)
        assertEquals(Instant.parse("2026-01-02T10:00:00Z"), first.publishedAt)
        assertEquals("Ann", first.author)
        assertEquals("http://example.test/t1.jpg", first.thumbnailUrl)
        assertEquals("(untitled)", second.title)
        assertEquals(null, second.link)
        assertEquals(null, second.author)
        assertEquals(null, second.thumbnailUrl)
        assertEquals("http://example.test/e3.png", third.thumbnailUrl)
    }

    @Test
    fun `blank titles and authors are treated as absent`() {
        server.route("/blank.xml", Route.Raw("rss-blank.xml", "application/rss+xml"))

        val feed = (fetcher.fetch("${server.baseUrl}/blank.xml", Validators()) as FetchResult.Fetched).feed

        assertEquals(null, feed.title)
        assertEquals("(untitled)", feed.items.single().title)
        assertEquals(null, feed.items.single().author)
    }

    @Test
    fun `any 2xx status is parsed as a feed and other statuses are fetch errors`() {
        server.route("/200", Route.Status(200))
        server.route("/299", Route.Status(299))
        server.route("/300", Route.Status(300))

        assertThrows(InvalidFeedException::class.java) { fetcher.fetch("${server.baseUrl}/200", Validators()) }
        assertThrows(InvalidFeedException::class.java) { fetcher.fetch("${server.baseUrl}/299", Validators()) }
        assertThrows(FeedFetchException::class.java) { fetcher.fetch("${server.baseUrl}/300", Validators()) }
    }

    @Test
    fun `an unreachable server is a fetch error with a message`() {
        val url = "${server.baseUrl}/gone.xml"
        server.stop()

        val error = assertThrows(FeedFetchException::class.java) { fetcher.fetch(url, Validators()) }

        assertTrue(error.message!!.isNotBlank())
    }

    @Test
    fun `an interrupted fetch fails and keeps the interrupt flag`() {
        server.route("/ok.xml", Route.Feed(dev.starve.acceptance.FeedSpec()))
        Thread.currentThread().interrupt()

        val url = "${server.baseUrl}/ok.xml"

        val error = assertThrows(FeedFetchException::class.java) { fetcher.fetch(url, Validators()) }

        assertEquals("interrupted", error.message)
        assertTrue(Thread.interrupted())
    }

    private class MemorySources(
        urls: List<String>,
    ) : SourceRepository {
        private val all = urls.mapIndexed { i, u -> Source(i + 1L, u, u, Validators(), null, null) }
        val errors = mutableMapOf<Long, String?>()

        override fun findAll() = all

        override fun findById(id: Long) = all.firstOrNull { it.id == id }

        override fun existsByUrl(url: String) = all.any { it.url == url }

        override fun insert(
            url: String,
            title: String,
        ): Source = error("unused")

        override fun delete(id: Long) = error("unused")

        override fun assignGroup(
            sourceId: Long,
            groupId: Long?,
        ) = error("unused")

        override fun recordSuccess(
            id: Long,
            title: String,
            validators: Validators,
            at: Instant,
        ) {
            errors[id] = null
        }

        override fun recordFailure(
            id: Long,
            error: String,
            at: Instant,
        ) {
            errors[id] = error
        }
    }

    private class MemoryItems : BaseMemoryItems() {
        var stored = 0

        override fun insertNew(
            sourceId: Long,
            items: List<FeedItem>,
            firstSeenAt: Instant,
        ) {
            stored += items.size
        }
    }
}
