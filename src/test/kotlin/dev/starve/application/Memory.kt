package dev.starve.application

import dev.starve.domain.FeedItem
import dev.starve.domain.Source
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
}

/** Records the items and cutoffs passed to the item repository. */
class MemoryItems(
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
}
