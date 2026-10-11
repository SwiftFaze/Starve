package dev.starve.application

import dev.starve.domain.Feed
import dev.starve.domain.Source
import dev.starve.domain.Validators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant

class SubscriptionServiceTest {
    private val sources = MemorySources()
    private val ingestor = FeedIngestor(sources, MemoryItems(), Clock.systemUTC())

    @Test
    fun `an untitled feed is subscribed under its url`() {
        val result = subscribe(Feed(null, emptyList()))

        assertEquals("http://a.test/feed", (result as SubscribeResult.Subscribed).source.title)
    }

    @Test
    fun `a titled feed is subscribed under its title`() {
        val result = subscribe(Feed("Named", emptyList()))

        assertEquals("Named", (result as SubscribeResult.Subscribed).source.title)
    }

    @Test
    fun `the subscription returned is the stored row after ingestion`() {
        val reread = Source(1, "http://a.test/feed", "Reread", Validators(), Instant.EPOCH, null)
        sources.reread = reread

        val result = subscribe(Feed("Named", emptyList()))

        assertEquals(reread, (result as SubscribeResult.Subscribed).source)
    }

    private fun subscribe(feed: Feed): SubscribeResult {
        val fetcher =
            object : FeedFetcher {
                override fun fetch(
                    url: String,
                    validators: Validators,
                ) = FetchResult.Fetched(feed, Validators())
            }
        return SubscriptionService(sources, fetcher, ingestor).subscribe("http://a.test/feed")
    }
}
