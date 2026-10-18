package dev.starve.application

import dev.starve.domain.Feed
import dev.starve.domain.PollInterval
import dev.starve.domain.Source
import dev.starve.domain.Validators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant

class SourceScheduleServiceTest {
    private val stored =
        mutableListOf(
            Source(1, "http://a.test/f", "A", Validators(), null, null),
            Source(2, "http://b.test/f", "B", Validators(), null, null),
        )
    private val sources = Lookup(MemorySources(stored, mutableMapOf(2L to OTHER_OWNER)))
    private val fetched = mutableListOf<String>()
    private val fetcher =
        object : FeedFetcher {
            override fun fetch(
                url: String,
                validators: Validators,
            ): FetchResult {
                fetched += url
                return FetchResult.Fetched(Feed("t", emptyList()), Validators())
            }
        }
    private val ingestor = FeedIngestor(sources, MemoryItems(), Clock.systemUTC(), quietFavicons(sources))
    private val poller = PollService(sources, fetcher, ingestor)
    private val queued = mutableListOf<Runnable>()
    private val background = BackgroundPoller(sources, poller) { queued += it }
    private val saved = mutableListOf<Triple<Long, Long, PollInterval?>>()
    private val repository =
        SourceScheduleRepository { owner, id, interval ->
            val mine = owner == OWNER && id == 1L
            if (mine) saved += Triple(owner, id, interval)
            mine
        }
    private val service = SourceScheduleService(repository, sources, background)

    @Test
    fun `an interval is saved for the user's own source only`() {
        assertTrue(service.setInterval(OWNER, 1, PollInterval.DAILY))
        assertTrue(service.setInterval(OWNER, 1, null))
        assertFalse(service.setInterval(OWNER, 2, PollInterval.DAILY))

        assertEquals(listOf(Triple(OWNER, 1L, PollInterval.DAILY), Triple(OWNER, 1L, null)), saved)
    }

    @Test
    fun `fetch now queues one background fetch, however often it is pressed while it waits`() {
        assertTrue(service.fetchNow(OWNER, 1))
        assertTrue(service.fetchNow(OWNER, 1))

        assertEquals(1, queued.size)
        assertTrue(service.isFetching(1))
        assertTrue(fetched.isEmpty())
        queued.single().run()

        assertEquals(listOf("http://a.test/f"), fetched)
        assertFalse(service.isFetching(1))
        assertTrue(service.fetchNow(OWNER, 1))
        assertEquals(2, queued.size)
    }

    @Test
    fun `another user's source is not fetched`() {
        assertFalse(service.fetchNow(OWNER, 2))
        assertTrue(queued.isEmpty())
        assertFalse(service.isFetching(2))
    }

    @Test
    fun `a fetch that fails still ends, so the source can be fetched again`() {
        val failing = BackgroundPoller(sources, PollService(sources, failingFetcher, ingestor)) { it.run() }

        failing.poll(listOf(1L))

        assertFalse(failing.isFetching(1))
        assertEquals(
            "Could not be fetched: down",
            sources.delegate.failures
                .single()
                .second,
        )
    }

    @Test
    fun `due polling skips the sources whose interval has not passed`() {
        val now = Instant.parse("2026-10-18T12:00:00Z")
        stored[0] = stored[0].copy(lastFetchedAt = now.minus(Duration.ofMinutes(10)))
        stored[1] = stored[1].copy(lastFetchedAt = now.minus(Duration.ofMinutes(10)), pollInterval = PollInterval.DAILY)
        stored += Source(3, "http://c.test/f", "C", Validators(), null, null)

        poller.pollDue(now, Duration.ofMinutes(5))

        assertEquals(listOf("http://a.test/f", "http://c.test/f"), fetched)
    }

    private val failingFetcher =
        object : FeedFetcher {
            override fun fetch(
                url: String,
                validators: Validators,
            ): FetchResult = throw FeedFetchException("down")
        }

    /** The memory store, able to look sources up by id as the real store does. */
    private class Lookup(
        val delegate: MemorySources,
    ) : SourceRepository by delegate {
        override fun findById(id: Long): Source? = delegate.findAll().firstOrNull { it.id == id }
    }
}
