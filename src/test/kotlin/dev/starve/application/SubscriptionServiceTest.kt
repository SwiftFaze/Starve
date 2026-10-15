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
    private val ingestor = FeedIngestor(sources, MemoryItems(), Clock.systemUTC(), quietFavicons(sources))

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

    @Test
    fun `the resolved feed url is fetched and stored instead of the pasted one`() {
        val fetched = mutableListOf<String>()

        val result = subscribe(Feed("Named", emptyList()), LocateResult.Found("http://a.test/real.xml"), fetched)

        assertEquals("http://a.test/real.xml", (result as SubscribeResult.Subscribed).source.url)
        assertEquals(listOf("http://a.test/real.xml"), fetched)
    }

    @Test
    fun `a resolved feed that is already subscribed is not fetched again`() {
        sources.insert(OWNER, "http://a.test/real.xml", "Existing")
        val fetched = mutableListOf<String>()

        val result = subscribe(Feed("Named", emptyList()), LocateResult.Found("http://a.test/real.xml"), fetched)

        assertEquals(SubscribeResult.AlreadySubscribed, result)
        assertEquals(emptyList<String>(), fetched)
    }

    @Test
    fun `a pasted url that is already subscribed is not even located`() {
        sources.insert(OWNER, "http://a.test/feed", "Existing")
        val located = mutableListOf<String>()
        val locator =
            object : FeedLocator {
                override fun locate(url: String): LocateResult {
                    located += url
                    return LocateResult.NoFeed
                }
            }

        val service = SubscriptionService(sources, locator, fetcherOf(Feed(null, emptyList())), ingestor)

        val result = service.subscribe(OWNER, PASTED)

        assertEquals(SubscribeResult.AlreadySubscribed, result)
        assertEquals(emptyList<String>(), located)
    }

    @Test
    fun `location failures map to their results`() {
        assertEquals(SubscribeResult.NoFeedFound, subscribe(Feed(null, emptyList()), LocateResult.NoFeed))
        assertEquals(SubscribeResult.Unreachable, subscribe(Feed(null, emptyList()), LocateResult.Unreachable))
    }

    @Test
    fun `a non http url is rejected before locating`() {
        val result = SubscriptionService(sources, FailingLocator, fetcherOf(Feed(null, emptyList())), ingestor)
        assertEquals(SubscribeResult.NotHttpUrl, result.subscribe(OWNER, "ftp://a.test/x"))
    }

    @Test
    fun `fetch failures of the resolved feed are reported`() {
        val failing = { error: Exception ->
            object : FeedFetcher {
                override fun fetch(
                    url: String,
                    validators: Validators,
                ): FetchResult = throw error
            }
        }
        val locator = locatorOf(LocateResult.Found("http://a.test/real.xml"))

        val unreachable = SubscriptionService(sources, locator, failing(FeedFetchException("x")), ingestor)
        val invalid = SubscriptionService(sources, locator, failing(InvalidFeedException("x")), ingestor)

        assertEquals(SubscribeResult.Unreachable, unreachable.subscribe(OWNER, PASTED))
        assertEquals(SubscribeResult.NotAFeed, invalid.subscribe(OWNER, PASTED))
    }

    private fun subscribe(
        feed: Feed,
        located: LocateResult = LocateResult.Found(PASTED),
        fetched: MutableList<String> = mutableListOf(),
    ): SubscribeResult {
        val fetcher =
            object : FeedFetcher {
                override fun fetch(
                    url: String,
                    validators: Validators,
                ): FetchResult {
                    fetched += url
                    return FetchResult.Fetched(feed, Validators())
                }
            }
        return SubscriptionService(sources, locatorOf(located), fetcher, ingestor).subscribe(OWNER, PASTED)
    }

    private fun fetcherOf(feed: Feed) =
        object : FeedFetcher {
            override fun fetch(
                url: String,
                validators: Validators,
            ) = FetchResult.Fetched(feed, Validators())
        }

    private fun locatorOf(result: LocateResult) =
        object : FeedLocator {
            override fun locate(url: String) = result
        }

    private object FailingLocator : FeedLocator {
        override fun locate(url: String): LocateResult = error("must not be called")
    }

    private companion object {
        const val PASTED = "http://a.test/feed"
    }

    @Test
    fun `a url another user follows does not count as already subscribed`() {
        sources.insert(OTHER_OWNER, "http://a.test/feed", "Theirs")

        val result = subscribe(Feed("Mine", emptyList()))

        assertEquals("Mine", (result as SubscribeResult.Subscribed).source.title)
        assertEquals(OWNER, sources.insertedFor.getValue(2))
    }

    @Test
    fun `only a user's own sources are listed and unsubscribed`() {
        sources.insert(OWNER, "http://a.test/one", "One")
        sources.insert(OTHER_OWNER, "http://a.test/two", "Two")
        val service = SubscriptionService(sources, FailingLocator, fetcherOf(Feed(null, emptyList())), ingestor)

        assertEquals(listOf("One"), service.list(OWNER).map { it.title })
        assertEquals(false, service.unsubscribe(OWNER, 2))
        assertEquals(true, service.unsubscribe(OWNER, 1))
        assertEquals(listOf("Two"), service.list(OTHER_OWNER).map { it.title })
    }
}
