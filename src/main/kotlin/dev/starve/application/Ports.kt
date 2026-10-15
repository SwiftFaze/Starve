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
import dev.starve.domain.UserAccount
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
    /** Every source of every owner: what the background poll covers. */
    fun findAll(): List<Source>

    /** The sources owned by user [owner], oldest first. */
    fun findAllOf(owner: Long): List<Source>

    /** The source with this id whoever owns it: for background work, never for a request. */
    fun findById(id: Long): Source?

    fun existsByUrl(
        owner: Long,
        url: String,
    ): Boolean

    fun insert(
        owner: Long,
        url: String,
        title: String,
    ): Source

    /** Deletes [owner]'s source [id]; false when [owner] has no such source. */
    fun delete(
        owner: Long,
        id: Long,
    ): Boolean

    /**
     * Puts [owner]'s source in [groupId] (one of [owner]'s groups), or ungroups it when null; false when [owner]
     * has no such source or group.
     */
    fun assignGroup(
        owner: Long,
        sourceId: Long,
        groupId: Long?,
    ): Boolean

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
    /** [owner]'s groups, alphabetical ignoring case. */
    fun findAll(owner: Long): List<Group>

    /** [owner]'s group [id], or null when [owner] has no such group. */
    fun findById(
        owner: Long,
        id: Long,
    ): Group?

    /** [owner]'s group with this name, ignoring case. */
    fun findByName(
        owner: Long,
        name: String,
    ): Group?

    fun insert(
        owner: Long,
        name: String,
    ): Group

    fun rename(
        owner: Long,
        id: Long,
        name: String,
    )

    /** Deletes [owner]'s group, whose sources become ungrouped; false when [owner] has no such group. */
    fun delete(
        owner: Long,
        id: Long,
    ): Boolean

    /** Every group of [owner] (alphabetical ignoring case) with its unread item count. */
    fun unreadCounts(owner: Long): List<GroupSummary>

    /** [owner]'s unread items across all their sources, grouped or not. */
    fun totalUnread(owner: Long): Int
}

/** Persistence of the favourite mark and the Favourites view. */
interface FavouriteRepository {
    /** [owner]'s newest-first favourites older than [after] (all when null), read or not; at most [limit]. */
    fun favourites(
        owner: Long,
        after: Cursor?,
        limit: Int,
    ): List<TimelineItem>

    /** Marks [owner]'s item a favourite at [at] unless it already is; returns false when [owner] has no such item. */
    fun favourite(
        owner: Long,
        id: Long,
        at: Instant,
    ): Boolean

    /** Clears the favourite mark; returns false when [owner] has no such item. */
    fun unfavourite(
        owner: Long,
        id: Long,
    ): Boolean
}

/** Persistence of full-text search over titles, source names and post bodies. */
interface SearchRepository {
    /**
     * [owner]'s items whose title, source name or body matches the FTS5 [match] expression, newest first, older than
     * [after] (all when null); at most [limit]. Read state, group and the show setting do not apply.
     */
    fun search(
        owner: Long,
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
    /** [owner]'s saved items earliest-saved first (ties by id ascending), after [after] (from the start when null). */
    fun later(
        owner: Long,
        after: LaterCursor?,
        limit: Int,
    ): List<SavedItem>

    /** Saves [owner]'s item for later at [at] unless it already is saved; false when [owner] has no such item. */
    fun save(
        owner: Long,
        id: Long,
        at: Instant,
    ): Boolean

    /** Clears the saved mark; returns false when [owner] has no such item. */
    fun clear(
        owner: Long,
        id: Long,
    ): Boolean
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

    /** [owner]'s newest-first items older than [after] (all when null), narrowed by [filter]; at most [limit]. */
    fun timeline(
        owner: Long,
        after: Cursor?,
        filter: TimelineFilter,
        limit: Int,
    ): List<TimelineItem>

    /** [owner]'s item [id], or null when [owner] has no such item. */
    fun find(
        owner: Long,
        id: Long,
    ): ItemRef?

    /** [owner]'s item with its published date, stored body and favourite and saved-for-later state, for the reader. */
    fun findPost(
        owner: Long,
        id: Long,
    ): PostRef?

    /** [owner]'s item with its feed data and stored oEmbed details, for the detail view. */
    fun findDetail(
        owner: Long,
        id: Long,
    ): DetailRef?

    /** Stores the oEmbed details of an item, replacing any stored before. */
    fun saveOembed(
        id: Long,
        oembed: Oembed,
    )

    /** Marks [owner]'s item read at [at] unless already read; returns false when [owner] has no such item. */
    fun markRead(
        owner: Long,
        id: Long,
        at: Instant,
    ): Boolean

    /**
     * Marks every unread item of [owner] first seen at or before [firstSeenCutoff] as read at [at], within
     * [groupId] if given.
     */
    fun markAllRead(
        owner: Long,
        firstSeenCutoff: Instant,
        at: Instant,
        groupId: Long?,
    ): Int
}

/** Narrows a timeline: read items are listed only when [showRead]; only group [groupId] when it is given. */
data class TimelineFilter(
    val showRead: Boolean,
    val groupId: Long? = null,
)

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

    /** Stores [username] as an ordinary (non-admin) account; the username must be unused. */
    fun insert(
        username: String,
        passwordHash: String,
        at: Instant,
    ): User

    /** Stores [username] as an ordinary account unless the name is taken (ignoring case); null when it is. */
    fun insertIfFree(
        username: String,
        passwordHash: String,
        at: Instant,
    ): User?
}

/** What an admin and a signed-in user can do to stored accounts beyond signing up. */
interface UserAdminRepository {
    /** Every account, oldest first. */
    fun listAll(): List<UserAccount>

    fun findById(id: Long): User?

    fun updatePasswordHash(
        id: Long,
        passwordHash: String,
    )

    /** Deletes the account (its sources, groups and items go with it); false when there is none. */
    fun delete(id: Long): Boolean
}

/** Hashes a raw password for storage. */
fun interface PasswordHasher {
    fun hash(password: String): String
}

/** Checks a raw password against a stored hash. */
fun interface PasswordMatcher {
    fun matches(
        password: String,
        hash: String,
    ): Boolean
}

/** Small installation values kept in the database (the `app_metadata` table). */
interface AppMetadata {
    fun find(key: String): String?

    /** Stores [value] only while [key] is unset; false when a value was already there. */
    fun putIfAbsent(
        key: String,
        value: String,
    ): Boolean

    /** Stores [value] under [key], replacing any value already there. */
    fun put(
        key: String,
        value: String,
    )
}
