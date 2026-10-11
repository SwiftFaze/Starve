package dev.starve.application

import dev.starve.domain.Feed
import dev.starve.domain.FeedItem
import dev.starve.domain.Source
import dev.starve.domain.Validators
import java.time.Instant

/** The server could not be reached or answered with an error status. */
class FeedFetchException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** The server answered, but the body is not a parseable RSS/Atom feed. */
class InvalidFeedException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

sealed interface FetchResult {
    data class Fetched(
        val feed: Feed,
        val validators: Validators,
    ) : FetchResult

    data object NotModified : FetchResult
}

interface FeedFetcher {
    /** Conditional GET of [url]; throws [FeedFetchException] or [InvalidFeedException] on failure. */
    fun fetch(
        url: String,
        validators: Validators,
    ): FetchResult
}

interface SourceRepository {
    fun findAll(): List<Source>

    fun findById(id: Long): Source?

    fun existsByUrl(url: String): Boolean

    fun insert(
        url: String,
        title: String,
    ): Source

    fun delete(id: Long)

    fun recordSuccess(
        id: Long,
        title: String,
        validators: Validators,
        at: Instant,
    )

    fun recordFailure(
        id: Long,
        error: String,
        at: Instant,
    )
}

interface ItemRepository {
    /** Stores items whose key is new for the source; existing keys are left untouched. */
    fun insertNew(
        sourceId: Long,
        items: List<FeedItem>,
        firstSeenAt: Instant,
    )

    /** Deletes items first seen before [cutoff]; returns how many were removed. */
    fun deleteFirstSeenBefore(cutoff: Instant): Int
}
