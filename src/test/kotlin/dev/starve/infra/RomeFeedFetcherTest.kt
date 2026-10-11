package dev.starve.infra

import dev.starve.acceptance.FeedServer
import dev.starve.acceptance.Route
import dev.starve.application.FeedFetchException
import dev.starve.application.FeedFetcher
import dev.starve.application.FeedIngestor
import dev.starve.application.FetchResult
import dev.starve.application.ItemRepository
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

class RomeFeedFetcherTest {
    private lateinit var server: FeedServer
    private lateinit var fetcher: FeedFetcher

    @BeforeEach
    fun setUp() {
        server = FeedServer()
        fetcher = RomeFeedFetcher(HttpClient.newHttpClient(), Duration.ofMillis(500), "test")
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

    private class MemoryItems : ItemRepository {
        var stored = 0

        override fun insertNew(
            sourceId: Long,
            items: List<FeedItem>,
            firstSeenAt: Instant,
        ) {
            stored += items.size
        }

        override fun deleteFirstSeenBefore(cutoff: Instant) = 0
    }
}
