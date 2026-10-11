package dev.starve.application

import dev.starve.domain.Source
import java.time.Clock

/** Records the outcome of one fetch of one source. */
class FeedIngestor(
    private val sources: SourceRepository,
    private val items: ItemRepository,
    private val clock: Clock,
) {
    fun apply(
        source: Source,
        result: FetchResult,
    ) {
        val now = clock.instant()
        when (result) {
            FetchResult.NotModified -> {
                sources.recordSuccess(source.id, source.title, source.validators, now)
            }

            is FetchResult.Fetched -> {
                val stamped = result.feed.items.map { it.copy(publishedAt = it.publishedAt ?: now) }
                items.insertNew(source.id, stamped, now)
                val title = result.feed.title?.takeIf { it.isNotBlank() } ?: source.title
                sources.recordSuccess(source.id, title, result.validators, now)
            }
        }
    }

    fun failed(
        source: Source,
        error: String,
    ) = sources.recordFailure(source.id, error, clock.instant())
}
