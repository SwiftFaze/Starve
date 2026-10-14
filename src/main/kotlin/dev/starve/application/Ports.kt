package dev.starve.application

import dev.starve.domain.Cursor
import dev.starve.domain.Feed
import dev.starve.domain.FeedItem
import dev.starve.domain.Group
import dev.starve.domain.GroupSummary
import dev.starve.domain.LaterCursor
import dev.starve.domain.ParsedImport
import dev.starve.domain.Playable
import dev.starve.domain.SavedItem
import dev.starve.domain.Source
import dev.starve.domain.TimelineItem
import dev.starve.domain.User
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

    /** Stores the site icon found for the source ([url] null when there is none) and stamps the check time. */
    fun recordFavicon(
        id: Long,
        url: String?,
        at: Instant,
    )
}

/** Finds a site's icon; throws [FeedFetchException] when the site cannot be reached in time. */
interface FaviconFinder {
    /** The icon URL for the site at [siteUrl]: its icon link, else its /favicon.ico; null when it has none. */
    fun find(siteUrl: String): String?
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

/** Persistence of the favourite mark and the Favourites view. */
interface FavouriteRepository {
    /** Newest-first favourites older than [after] (all when null), read or not; at most [limit]. */
    fun favourites(
        after: Cursor?,
        limit: Int,
    ): List<TimelineItem>

    /** Marks the item a favourite at [at] unless it already is; returns false when no such item exists. */
    fun favourite(
        id: Long,
        at: Instant,
    ): Boolean

    /** Clears the favourite mark; returns false when no such item exists. */
    fun unfavourite(id: Long): Boolean
}

/** Persistence of full-text search over titles, source names and post bodies. */
interface SearchRepository {
    /**
     * Items whose title, source name or body matches the FTS5 [match] expression, newest first, older than [after]
     * (all when null); at most [limit]. Read state, group and the show setting do not apply.
     */
    fun search(
        match: String,
        after: Cursor?,
        limit: Int,
    ): List<TimelineItem>
}

/** Persistence of the article-page check a teaser-only post goes through when it is opened. */
interface ArticleRepository {
    /**
     * Records an article check at [at]: stores [body] when it is not null (and
     * re-indexes the item for search), and [thumbnailUrl] only where the item has no thumbnail yet.
     */
    fun recordArticleCheck(
        id: Long,
        body: String?,
        thumbnailUrl: String?,
        at: Instant,
    )
}

/** Persistence of the read-later mark and the Later view. */
interface LaterRepository {
    /** Saved items earliest-saved first (ties by id ascending), after [after] (from the start when null). */
    fun later(
        after: LaterCursor?,
        limit: Int,
    ): List<SavedItem>

    /** Saves the item for later at [at] unless it already is saved; returns false when no such item exists. */
    fun save(
        id: Long,
        at: Instant,
    ): Boolean

    /** Clears the saved mark; returns false when no such item exists. */
    fun clear(id: Long): Boolean
}

interface ItemRepository {
    /** Stores items whose key is new for the source; a known key only gains a thumbnail or body it lacked. */
    fun insertNew(
        sourceId: Long,
        items: List<FeedItem>,
        firstSeenAt: Instant,
    )

    /**
     * Deletes items first seen before [cutoff] that are neither favourites nor saved for later; returns how many
     * were removed.
     */
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

    fun find(id: Long): ItemRef?

    /** The item with its published date, stored body and favourite and saved-for-later state, for the reader. */
    fun findPost(id: Long): PostRef?

    /** The item with its feed data and stored oEmbed details, for the detail view. */
    fun findDetail(id: Long): DetailRef?

    /** Stores the oEmbed details of an item, replacing any stored before. */
    fun saveOembed(
        id: Long,
        oembed: Oembed,
    )

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

/** One item as needed to open it, or to name it while it plays. */
data class ItemRef(
    val id: Long,
    val link: String?,
    val title: String,
    val sourceTitle: String,
    val thumbnailUrl: String?,
)

/**
 * One item as the reader shows it; [body] is already-sanitised HTML, or null when none was stored.
 * [thumbnailUrl] is the item's feed image and [faviconUrl] its source's site icon, each null when absent.
 */
data class PostRef(
    val id: Long,
    val link: String?,
    val title: String,
    val sourceTitle: String,
    val publishedAt: Instant,
    val body: String?,
    val thumbnailUrl: String? = null,
    val faviconUrl: String? = null,
    val favourite: Boolean = false,
    /** When the article page was last fetched for this item; null when never. */
    val articleCheckedAt: Instant? = null,
    val later: Boolean = false,
)

/** SoundCloud's public oEmbed details of a track; a field is null when oEmbed did not give it. */
data class Oembed(
    val authorName: String?,
    val thumbnailUrl: String?,
    val description: String?,
)

/**
 * One item as the detail view needs it: its feed data, the oEmbed details once they are stored, and whether it is
 * saved for later.
 */
data class DetailRef(
    val id: Long,
    val link: String?,
    val title: String,
    val sourceTitle: String,
    val publishedAt: Instant,
    val author: String?,
    val thumbnailUrl: String?,
    val body: String?,
    val oembed: Oembed?,
    val later: Boolean = false,
)

/** What the detail view shows: the feed data with SoundCloud's oEmbed details laid over it. */
data class ItemDetail(
    val id: Long,
    val link: String?,
    val title: String,
    val sourceTitle: String,
    val publishedAt: Instant,
    val artist: String?,
    val thumbnailUrl: String?,
    val description: String?,
    val later: Boolean = false,
)

fun interface SoundCloudOembed {
    /** The oEmbed details of [trackUrl]; null when the fetch fails, times out or its body cannot be read. */
    fun fetch(trackUrl: String): Oembed?
}

/** What a fetched article page offers: its sanitised [body] (null when no text was found) and og:image [imageUrl]. */
data class FetchedArticle(
    val body: String?,
    val imageUrl: String?,
)

fun interface ArticleFetcher {
    /** Fetches the page at the http(s) [url] and extracts its article; null when the page could not be fetched. */
    fun fetch(url: String): FetchedArticle?
}

/** Reads the plain text of stored, already-sanitised HTML. */
fun interface HtmlText {
    fun plainText(html: String): String
}

interface EmbedChecker {
    /** Whether [video] may be embedded; true whenever that cannot be determined. */
    fun allowsEmbedding(video: Playable.YouTube): Boolean
}

fun interface OpmlReader {
    /** Reads [text] as OPML, or returns null when it is not an OPML document (a DOCTYPE counts as not OPML). */
    fun read(text: String): ParsedImport?
}

/** Runs work off the calling thread. */
fun interface BackgroundRunner {
    fun run(task: Runnable)
}

interface UserRepository {
    /** Whether any account exists. */
    fun exists(): Boolean

    /** The account whose username matches [username] ignoring case, or null. */
    fun findByUsername(username: String): User?

    /** Stores [username] as the admin only while no account exists; false when one already does. */
    fun insertFirstAdmin(
        username: String,
        passwordHash: String,
        at: Instant,
    ): Boolean
}

/** Hashes a raw password for storage. */
fun interface PasswordHasher {
    fun hash(password: String): String
}

/** Small installation values kept in the database (the `app_metadata` table). */
interface AppMetadata {
    fun find(key: String): String?

    /** Stores [value] only while [key] is unset; false when a value was already there. */
    fun putIfAbsent(
        key: String,
        value: String,
    ): Boolean
}
