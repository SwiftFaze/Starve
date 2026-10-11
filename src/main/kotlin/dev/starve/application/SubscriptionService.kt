package dev.starve.application

import dev.starve.domain.FeedRules
import dev.starve.domain.Source
import dev.starve.domain.Validators

sealed interface SubscribeResult {
    data class Subscribed(
        val source: Source,
    ) : SubscribeResult

    data object NotHttpUrl : SubscribeResult

    data object AlreadySubscribed : SubscribeResult

    data object NotAFeed : SubscribeResult

    data object NoFeedFound : SubscribeResult

    data object Unreachable : SubscribeResult
}

class SubscriptionService(
    private val sources: SourceRepository,
    private val locator: FeedLocator,
    private val fetcher: FeedFetcher,
    private val ingestor: FeedIngestor,
) {
    fun list(): List<Source> = sources.findAll()

    fun unsubscribe(id: Long) = sources.delete(id)

    /** Resolves [rawUrl] to a feed and fetches it; stores the subscription only if it yields a feed. */
    fun subscribe(rawUrl: String): SubscribeResult {
        val url = rawUrl.trim()
        return when {
            !FeedRules.isHttpUrl(url) -> SubscribeResult.NotHttpUrl
            sources.existsByUrl(url) -> SubscribeResult.AlreadySubscribed
            else -> subscribeResolved(url)
        }
    }

    private fun subscribeResolved(url: String): SubscribeResult =
        when (val located = locator.locate(url)) {
            LocateResult.NoFeed -> {
                SubscribeResult.NoFeedFound
            }

            LocateResult.Unreachable -> {
                SubscribeResult.Unreachable
            }

            is LocateResult.Found -> {
                if (sources.existsByUrl(located.feedUrl)) {
                    SubscribeResult.AlreadySubscribed
                } else {
                    fetchAndStore(located.feedUrl)
                }
            }
        }

    private fun fetchAndStore(url: String): SubscribeResult =
        try {
            when (val result = fetcher.fetch(url, Validators())) {
                FetchResult.NotModified -> SubscribeResult.Unreachable
                is FetchResult.Fetched -> SubscribeResult.Subscribed(store(url, result))
            }
        } catch (_: FeedFetchException) {
            SubscribeResult.Unreachable
        } catch (_: InvalidFeedException) {
            SubscribeResult.NotAFeed
        }

    private fun store(
        url: String,
        fetched: FetchResult.Fetched,
    ): Source {
        val title = fetched.feed.title?.takeIf { it.isNotBlank() } ?: url
        val source = sources.insert(url, title)
        ingestor.apply(source, fetched)
        return sources.findById(source.id) ?: source
    }
}
