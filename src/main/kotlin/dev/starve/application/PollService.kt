package dev.starve.application

import dev.starve.domain.Source

class PollService(
    private val sources: SourceRepository,
    private val fetcher: FeedFetcher,
    private val ingestor: FeedIngestor,
) {
    /** Polls every subscription; one source failing never stops the others. */
    fun pollAll() = sources.findAll().forEach { pollOne(it) }

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
