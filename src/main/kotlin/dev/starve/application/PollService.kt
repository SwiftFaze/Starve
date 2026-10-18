package dev.starve.application

import dev.starve.domain.PollRules
import dev.starve.domain.Source
import java.time.Duration
import java.time.Instant

class PollService(
    private val sources: SourceRepository,
    private val fetcher: FeedFetcher,
    private val ingestor: FeedIngestor,
) {
    /**
     * Polls the subscriptions due at [now], each on its own interval or [default]; one source failing never stops the
     * others.
     */
    fun pollDue(
        now: Instant,
        default: Duration,
    ) = sources.findAll().filter { PollRules.isDue(it, now, default) }.forEach { pollOne(it) }

    fun pollOne(source: Source) {
        try {
            ingestor.apply(source, fetcher.fetch(source.url, source.validators))
        } catch (e: FeedFetchException) {
            ingestor.failed(source, "Could not be fetched: ${e.message}")
        } catch (e: InvalidFeedException) {
            ingestor.failed(source, "Not a valid RSS or Atom feed: ${e.message}")
        }
    }
}
