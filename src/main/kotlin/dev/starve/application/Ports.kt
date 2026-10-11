package dev.starve.application

import dev.starve.domain.Cursor
import dev.starve.domain.Feed
import dev.starve.domain.FeedItem
import dev.starve.domain.Group
import dev.starve.domain.GroupSummary
import dev.starve.domain.Playable
import dev.starve.domain.Source
import dev.starve.domain.TimelineItem
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

/** What resolving a pasted URL to a feed URL found. */
sealed interface LocateResult {
    data class Found(
        val feedUrl: String,
    ) : LocateResult

    data object NoFeed : LocateResult

    data object Unreachable : LocateResult
}

interface FeedLocator {
    /** Resolves a pasted page or feed [url] to the URL of the feed it stands for. */
    fun locate(url: String): LocateResult
}

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

    /** Puts the source in [groupId], or ungroups it when null. */
    fun assignGroup(
        sourceId: Long,
        groupId: Long?,
    )

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

interface GroupRepository {
    /** All groups, alphabetical ignoring case. */
    fun findAll(): List<Group>

    fun findById(id: Long): Group?

    /** The group with this name, ignoring case. */
    fun findByName(name: String): Group?

    fun insert(name: String): Group

    fun rename(
        id: Long,
        name: String,
    )

    /** Deletes the group; its sources become ungrouped. */
    fun delete(id: Long)

    /** Every group (alphabetical ignoring case) with its unread item count. */
    fun unreadCounts(): List<GroupSummary>

    /** Unread items across all sources, grouped or not. */
    fun totalUnread(): Int
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

    /**
     * Newest-first items older than [after] (all when null); at most [limit]; read ones only when [showRead];
     * only those of group [groupId] when it is given.
     */
    fun timeline(
        after: Cursor?,
        showRead: Boolean,
        limit: Int,
        groupId: Long?,
    ): List<TimelineItem>

    fun findLink(id: Long): ItemLink?

    /** Marks the item read at [at] unless already read; returns false when no such item exists. */
    fun markRead(
        id: Long,
        at: Instant,
    ): Boolean

    /** Marks every unread item first seen at or before [firstSeenCutoff] as read at [at], within [groupId] if given. */
    fun markAllRead(
        firstSeenCutoff: Instant,
        at: Instant,
        groupId: Long?,
    ): Int
}

/** The target of an item, as needed to open it. */
data class ItemLink(
    val link: String?,
)

interface EmbedChecker {
    /** Whether [video] may be embedded; true whenever that cannot be determined. */
    fun allowsEmbedding(video: Playable.YouTube): Boolean
}
