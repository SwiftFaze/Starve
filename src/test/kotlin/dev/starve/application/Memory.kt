package dev.starve.application

import dev.starve.domain.Cursor
import dev.starve.domain.FeedItem
import dev.starve.domain.Group
import dev.starve.domain.GroupSummary
import dev.starve.domain.Source
import dev.starve.domain.TimelineItem
import dev.starve.domain.Validators
import java.time.Instant

/** Records what the application services ask of the source repository. */
class MemorySources(
    private val stored: MutableList<Source> = mutableListOf(),
) : SourceRepository {
    data class Success(
        val id: Long,
        val title: String,
        val validators: Validators,
        val at: Instant,
    )

    val successes = mutableListOf<Success>()
    val failures = mutableListOf<Pair<Long, String>>()
    var reread: Source? = null

    override fun findAll(): List<Source> = stored.toList()

    override fun findById(id: Long): Source? = reread

    override fun existsByUrl(url: String) = stored.any { it.url == url }

    override fun insert(
        url: String,
        title: String,
    ): Source = Source(stored.size + 1L, url, title, Validators(), null, null).also { stored += it }

    override fun delete(id: Long) {
        stored.removeIf { it.id == id }
    }

    val assignments = mutableListOf<Pair<Long, Long?>>()

    override fun assignGroup(
        sourceId: Long,
        groupId: Long?,
    ) {
        assignments += sourceId to groupId
    }

    override fun recordSuccess(
        id: Long,
        title: String,
        validators: Validators,
        at: Instant,
    ) {
        successes += Success(id, title, validators, at)
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

/** Records the items and cutoffs passed to the item repository. */
open class MemoryItems(
    private val deleted: Int = 0,
) : ItemRepository {
    val inserted = mutableListOf<FeedItem>()
    var cutoff: Instant? = null

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
        after: Cursor?,
        showRead: Boolean,
        limit: Int,
        groupId: Long?,
    ): List<TimelineItem> = emptyList()

    override fun find(id: Long): ItemRef? = null

    override fun findPost(id: Long): PostRef? = null

    override fun findDetail(id: Long): DetailRef? = null

    override fun saveOembed(
        id: Long,
        oembed: Oembed,
    ) = Unit

    override fun markRead(
        id: Long,
        at: Instant,
    ) = false

    override fun markAllRead(
        firstSeenCutoff: Instant,
        at: Instant,
        groupId: Long?,
    ) = 0
}

/** An item repository with canned timeline rows that records read-state and favourite calls. */
class TimelineItems(
    private val rows: List<TimelineItem> = emptyList(),
    private val links: Map<Long, String?> = emptyMap(),
    private val markedAllCount: Int = 0,
    private val favouriteRows: List<TimelineItem> = emptyList(),
) : MemoryItems(),
    FavouriteRepository {
    var requested: Triple<Cursor?, Boolean, Int>? = null
    var requestedGroup: Long? = null
    var markAllGroup: Long? = null
    val readCalls = mutableListOf<Pair<Long, Instant>>()
    var markAllCall: Pair<Instant, Instant>? = null
    var favouritesRequested: Pair<Cursor?, Int>? = null

    /** Each favourite call as (id, time); the time is null for an unfavourite. */
    val favouriteCalls = mutableListOf<Pair<Long, Instant?>>()

    override fun timeline(
        after: Cursor?,
        showRead: Boolean,
        limit: Int,
        groupId: Long?,
    ): List<TimelineItem> {
        requested = Triple(after, showRead, limit)
        requestedGroup = groupId
        return rows
    }

    override fun favourites(
        after: Cursor?,
        limit: Int,
    ): List<TimelineItem> {
        favouritesRequested = after to limit
        return favouriteRows
    }

    override fun favourite(
        id: Long,
        at: Instant,
    ): Boolean {
        favouriteCalls += id to at
        return links.containsKey(id)
    }

    override fun unfavourite(id: Long): Boolean {
        favouriteCalls += id to null
        return links.containsKey(id)
    }

    override fun find(id: Long): ItemRef? = if (links.containsKey(id)) ref(id, links[id]) else null

    override fun markRead(
        id: Long,
        at: Instant,
    ): Boolean {
        readCalls += id to at
        return links.containsKey(id)
    }

    override fun markAllRead(
        firstSeenCutoff: Instant,
        at: Instant,
        groupId: Long?,
    ): Int {
        markAllCall = firstSeenCutoff to at
        markAllGroup = groupId
        return markedAllCount
    }
}

/** An in-memory group store; names are matched ignoring case. */
class MemoryGroups(
    private val stored: MutableList<Group> = mutableListOf(),
    private val unread: Map<Long, Int> = emptyMap(),
    private val total: Int = 0,
) : GroupRepository {
    val deleted = mutableListOf<Long>()

    override fun findAll(): List<Group> = stored.sortedBy { it.name.lowercase() }

    override fun findById(id: Long): Group? = stored.firstOrNull { it.id == id }

    override fun findByName(name: String): Group? = stored.firstOrNull { it.name.equals(name, ignoreCase = true) }

    override fun insert(name: String): Group = Group(stored.size + 1L, name).also { stored += it }

    override fun rename(
        id: Long,
        name: String,
    ) {
        stored.replaceAll { if (it.id == id) it.copy(name = name) else it }
    }

    override fun delete(id: Long) {
        deleted += id
        stored.removeIf { it.id == id }
    }

    override fun unreadCounts(): List<GroupSummary> = findAll().map { GroupSummary(it, unread[it.id] ?: 0) }

    override fun totalUnread(): Int = total
}

/** The [ItemRef] the in-memory repositories return for item [id] with [link]. */
fun ref(
    id: Long,
    link: String?,
) = ItemRef(id, link, "Item $id", "Source", null)
