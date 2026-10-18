package dev.starve.application

import dev.starve.domain.AudioEnclosure
import dev.starve.domain.Cursor
import dev.starve.domain.FeedItem
import dev.starve.domain.Group
import dev.starve.domain.GroupSummary
import dev.starve.domain.LaterCursor
import dev.starve.domain.ReadMark
import dev.starve.domain.SavedItem
import dev.starve.domain.Source
import dev.starve.domain.Tag
import dev.starve.domain.TimelineItem
import dev.starve.domain.Validators
import java.time.Instant

/** The user the unit tests act as. */
const val OWNER = 7L

/** Another user, for tests that show one user cannot reach the other's data. */
const val OTHER_OWNER = 8L

/** Records what the application services ask of the source repository; sources belong to [OWNER] unless listed. */
class MemorySources(
    private val stored: MutableList<Source> = mutableListOf(),
    private val owners: MutableMap<Long, Long> = mutableMapOf(),
) : SourceRepository {
    data class Success(
        val id: Long,
        val title: String,
        val validators: Validators,
        val at: Instant,
        val movedTo: String? = null,
    )

    val successes = mutableListOf<Success>()
    val failures = mutableListOf<Pair<Long, String>>()
    var reread: Source? = null

    /** Who a source was inserted for, by source id. */
    val insertedFor = mutableMapOf<Long, Long>()

    private fun ownerOf(source: Source) = owners[source.id] ?: insertedFor[source.id] ?: OWNER

    override fun findAll(): List<Source> = stored.toList()

    override fun findAllOf(owner: Long): List<Source> = stored.filter { ownerOf(it) == owner }

    override fun findById(id: Long): Source? = reread

    override fun existsByUrl(
        owner: Long,
        url: String,
    ) = findAllOf(owner).any { it.url == url }

    override fun insert(
        owner: Long,
        url: String,
        title: String,
    ): Source =
        Source(stored.size + 1L, url, title, Validators(), null, null).also {
            stored += it
            insertedFor[it.id] = owner
        }

    override fun delete(
        owner: Long,
        id: Long,
    ): Boolean = stored.removeIf { it.id == id && ownerOf(it) == owner }

    val assignments = mutableListOf<Pair<Long, Long?>>()

    override fun assignGroup(
        owner: Long,
        sourceId: Long,
        groupId: Long?,
    ): Boolean {
        val owned = findAllOf(owner).any { it.id == sourceId }
        if (owned) assignments += sourceId to groupId
        return owned
    }

    override fun recordSuccess(
        id: Long,
        success: FetchSuccess,
    ) {
        successes += Success(id, success.title, success.validators, success.at, success.movedTo)
    }

    override fun recordFailure(
        id: Long,
        error: String,
        at: Instant,
    ) {
        failures += id to error
    }

    val favicons = mutableListOf<Triple<Long, String?, Instant>>()

    override fun recordFavicon(
        id: Long,
        url: String?,
        at: Instant,
    ) {
        favicons += Triple(id, url, at)
    }
}

/** A finder for tests that do not care about icons: no site has one. */
object NoIcons : FaviconFinder {
    override fun find(siteUrl: String): String? = null
}

/** Favicon handling for tests that do not care about icons; every site has none. */
fun quietFavicons(sources: SourceRepository): FaviconService = FaviconService(sources, NoIcons)

/** One article check as the article repository was asked to record it. */
data class ArticleCheck(
    val id: Long,
    val body: String?,
    val thumbnailUrl: String?,
    val at: Instant,
)

/** Records the article checks passed to the article repository. */
class MemoryArticles : ArticleRepository {
    val articleChecks = mutableListOf<ArticleCheck>()

    override fun recordArticleCheck(
        id: Long,
        body: String?,
        thumbnailUrl: String?,
        at: Instant,
    ) {
        articleChecks += ArticleCheck(id, body, thumbnailUrl, at)
    }
}

/** Records the items and cutoffs passed to the item repository. */
open class MemoryItems(
    private val deleted: Int = 0,
) : ItemRepository {
    val inserted = mutableListOf<FeedItem>()
    var cutoff: Instant? = null

    /** The owner each owner-scoped call was made for, most recent last. */
    val calledFor = mutableListOf<Long>()

    override fun insertNew(
        sourceId: Long,
        items: List<FeedItem>,
        firstSeenAt: Instant,
    ) {
        inserted += items
    }

    override fun deleteFirstSeenBefore(cutoff: Instant): Int {
        this.cutoff = cutoff
        return deleted
    }

    override fun timeline(
        owner: Long,
        after: Cursor?,
        filter: TimelineFilter,
        limit: Int,
    ): List<TimelineItem> = emptyList()

    override fun neighbours(
        owner: Long,
        position: ListPosition,
        filter: ListFilter,
    ): Neighbours = Neighbours(null, null)

    override fun find(
        owner: Long,
        id: Long,
    ): ItemRef? = null

    override fun findPost(
        owner: Long,
        id: Long,
    ): PostRef? = null

    override fun findDetail(
        owner: Long,
        id: Long,
    ): DetailRef? = null

    override fun saveOembed(
        id: Long,
        oembed: Oembed,
    ) = Unit

    override fun markRead(
        owner: Long,
        id: Long,
        mark: ReadMark,
    ) = false

    override fun markAllRead(
        owner: Long,
        firstSeenCutoff: Instant,
        at: Instant,
        within: Narrowing,
    ) = 0
}

/** An item repository with canned timeline rows that records read-state, favourite and later calls. */
class TimelineItems(
    private val rows: List<TimelineItem> = emptyList(),
    private val links: Map<Long, String?> = emptyMap(),
    private val markedAllCount: Int = 0,
    private val favouriteRows: List<TimelineItem> = emptyList(),
    private val savedRows: List<SavedItem> = emptyList(),
    private val audio: Map<Long, AudioEnclosure> = emptyMap(),
) : MemoryItems(),
    FavouriteRepository,
    SearchRepository,
    LaterRepository {
    var requested: Triple<Cursor?, Boolean, Int>? = null
    var requestedGroup: Long? = null
    var requestedMatch: String? = null
    var markAllWithin: Narrowing? = null
    val readCalls = mutableListOf<Pair<Long, ReadMark>>()
    var markAllCall: Pair<Instant, Instant>? = null
    var favouritesRequested: Pair<Cursor?, Int>? = null
    var laterRequested: Pair<LaterCursor?, Int>? = null

    /** Each favourite call as (id, time); the time is null for an unfavourite. */
    val favouriteCalls = mutableListOf<Pair<Long, Instant?>>()
    var searched: Triple<String, Cursor?, Int>? = null

    /** Each later call as (id, time); the time is null for a clear. */
    val laterCalls = mutableListOf<Pair<Long, Instant?>>()

    override fun later(
        owner: Long,
        after: LaterCursor?,
        limit: Int,
    ): List<SavedItem> {
        calledFor += owner
        laterRequested = after to limit
        return savedRows
    }

    override fun save(
        owner: Long,
        id: Long,
        at: Instant,
    ): Boolean {
        calledFor += owner
        laterCalls += id to at
        return links.containsKey(id)
    }

    override fun clear(
        owner: Long,
        id: Long,
    ): Boolean {
        calledFor += owner
        laterCalls += id to null
        return links.containsKey(id)
    }

    override fun timeline(
        owner: Long,
        after: Cursor?,
        filter: TimelineFilter,
        limit: Int,
    ): List<TimelineItem> {
        calledFor += owner
        requested = Triple(after, filter.showRead, limit)
        requestedGroup = filter.groupId
        requestedMatch = filter.match
        return rows
    }

    override fun favourites(
        owner: Long,
        after: Cursor?,
        limit: Int,
    ): List<TimelineItem> {
        calledFor += owner
        favouritesRequested = after to limit
        return favouriteRows
    }

    override fun favourite(
        owner: Long,
        id: Long,
        at: Instant,
    ): Boolean {
        calledFor += owner
        favouriteCalls += id to at
        return links.containsKey(id)
    }

    override fun unfavourite(
        owner: Long,
        id: Long,
    ): Boolean {
        calledFor += owner
        favouriteCalls += id to null
        return links.containsKey(id)
    }

    override fun search(
        owner: Long,
        match: String,
        after: Cursor?,
        limit: Int,
    ): List<TimelineItem> {
        calledFor += owner
        searched = Triple(match, after, limit)
        return rows
    }

    override fun find(
        owner: Long,
        id: Long,
    ): ItemRef? {
        calledFor += owner
        return if (links.containsKey(id)) ref(id, links[id]).copy(audio = audio[id]) else null
    }

    override fun markRead(
        owner: Long,
        id: Long,
        mark: ReadMark,
    ): Boolean {
        calledFor += owner
        readCalls += id to mark
        return links.containsKey(id)
    }

    override fun markAllRead(
        owner: Long,
        firstSeenCutoff: Instant,
        at: Instant,
        within: Narrowing,
    ): Int {
        calledFor += owner
        markAllCall = firstSeenCutoff to at
        markAllWithin = within
        return markedAllCount
    }
}

/**
 * An in-memory group store; names are matched ignoring case. Groups belong to [OWNER] unless [owners] says otherwise
 * (by group id), and [created] records who each inserted group was made for.
 */
class MemoryGroups(
    private val stored: MutableList<Group> = mutableListOf(),
    private val unread: Map<Long, Int> = emptyMap(),
    private val total: Int = 0,
    private val owners: MutableMap<Long, Long> = mutableMapOf(),
) : GroupRepository {
    val deleted = mutableListOf<Long>()
    val created = mutableMapOf<Long, Long>()

    private fun ownerOf(group: Group) = owners[group.id] ?: created[group.id] ?: OWNER

    private fun of(owner: Long) = stored.filter { ownerOf(it) == owner }

    override fun findAll(owner: Long): List<Group> = of(owner).sortedBy { it.name.lowercase() }

    override fun findById(
        owner: Long,
        id: Long,
    ): Group? = of(owner).firstOrNull { it.id == id }

    override fun findByName(
        owner: Long,
        name: String,
    ): Group? = of(owner).firstOrNull { it.name.equals(name, ignoreCase = true) }

    override fun insert(
        owner: Long,
        name: String,
    ): Group =
        Group(stored.size + 1L, name).also {
            stored += it
            created[it.id] = owner
        }

    override fun rename(
        owner: Long,
        id: Long,
        name: String,
    ) {
        stored.replaceAll { if (it.id == id && ownerOf(it) == owner) it.copy(name = name) else it }
    }

    override fun delete(
        owner: Long,
        id: Long,
    ): Boolean {
        val owned = of(owner).any { it.id == id }
        if (owned) deleted += id
        stored.removeIf { it.id == id && ownerOf(it) == owner }
        return owned
    }

    override fun unreadCounts(owner: Long) = findAll(owner).map { GroupSummary(it, unread[it.id] ?: 0) }

    override fun totalUnread(owner: Long): Int = total
}

/** The [ItemRef] the in-memory repositories return for item [id] with [link]. */
fun ref(
    id: Long,
    link: String?,
) = ItemRef(id, link, "Item $id", "Source", null)

/**
 * An in-memory tag store; names are matched ignoring case. Tags belong to [OWNER] unless inserted for another owner,
 * and [unread] gives the unread count each match expression answers with.
 */
class MemoryTags(
    private val unread: Map<String, Int> = emptyMap(),
) : TagRepository {
    private val stored = mutableListOf<Pair<Long, Tag>>()
    val counted = mutableListOf<Narrowing>()

    private fun of(owner: Long) = stored.filter { it.first == owner }.map { it.second }

    override fun findAll(owner: Long): List<Tag> = of(owner).sortedBy { it.name.lowercase() }

    override fun findById(
        owner: Long,
        id: Long,
    ): Tag? = of(owner).firstOrNull { it.id == id }

    override fun findByName(
        owner: Long,
        name: String,
    ): Tag? = of(owner).firstOrNull { it.name.equals(name, ignoreCase = true) }

    override fun insert(
        owner: Long,
        tag: Tag,
    ): Tag = tag.copy(id = stored.size + 1L).also { stored += owner to it }

    override fun update(
        owner: Long,
        tag: Tag,
    ) {
        stored.replaceAll { if (it.first == owner && it.second.id == tag.id) owner to tag else it }
    }

    override fun delete(
        owner: Long,
        id: Long,
    ): Boolean = stored.removeIf { it.first == owner && it.second.id == id }

    override fun unreadCount(
        owner: Long,
        within: Narrowing,
    ): Int {
        counted += within
        return unread[within.match] ?: 0
    }
}
