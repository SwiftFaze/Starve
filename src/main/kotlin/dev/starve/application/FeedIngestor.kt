package dev.starve.application

import dev.starve.domain.Source
import java.time.Clock
import java.time.Instant

/** Records the outcome of one fetch of one source, then looks up its site icon when that is due. */
class FeedIngestor(
    private val sources: SourceRepository,
    private val items: ItemRepository,
    private val clock: Clock,
    private val favicons: FaviconService,
) {
    fun apply(
        source: Source,
        result: FetchResult,
    ) {
        val now = clock.instant()
        val siteLink =
            when (result) {
                FetchResult.NotModified -> {
                    sources.recordSuccess(source.id, source.title, source.validators, now)
                    null
                }

                is FetchResult.Fetched -> {
                    store(source, result, now)
                    result.feed.siteLink
                }
            }
        favicons.refresh(source, siteLink, now)
    }

    fun failed(
        source: Source,
        error: String,
    ) = sources.recordFailure(source.id, error, clock.instant())

    private fun store(
        source: Source,
        result: FetchResult.Fetched,
        now: Instant,
    ) {
        val stamped = result.feed.items.map { it.copy(publishedAt = it.publishedAt ?: now) }
        items.insertNew(source.id, stamped, now)
        val title = result.feed.title?.takeIf { it.isNotBlank() } ?: source.title
        sources.recordSuccess(source.id, title, result.validators, now)
    }
}
