package dev.starve.infra

import dev.starve.application.DetailRef
import dev.starve.application.ItemRef
import dev.starve.application.ItemRepository
import dev.starve.application.ListFilter
import dev.starve.application.ListPosition
import dev.starve.application.Narrowing
import dev.starve.application.Neighbours
import dev.starve.application.Oembed
import dev.starve.application.PostRef
import dev.starve.application.TimelineFilter
import dev.starve.domain.AudioEnclosure
import dev.starve.domain.Cursor
import dev.starve.domain.FeedItem
import dev.starve.domain.Playable
import dev.starve.domain.ReadMark
import dev.starve.domain.TimelineItem
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.Instant

@Repository
class JdbcItemRepository(
    private val jdbc: JdbcClient,
    private val search: SearchIndex,
) : ItemRepository {
    @Transactional
    override fun insertNew(
        sourceId: Long,
        items: List<FeedItem>,
        firstSeenAt: Instant,
    ) {
        items.forEach { jdbc.upsertItem(search, sourceId, it, firstSeenAt) }
    }

    override fun deleteFirstSeenBefore(cutoff: Instant): Int =
        jdbc
            .sql("DELETE FROM items WHERE first_seen_at < ? AND favourited_at IS NULL AND later_at IS NULL")
            .param(cutoff.toEpochMilli())
            .update()

    override fun timeline(
        owner: Long,
        after: Cursor?,
        filter: TimelineFilter,
        limit: Int,
    ): List<TimelineItem> {
        val params = mutableListOf<Any>()
        val conditions = narrowed(owner, Narrowing(filter.groupId, filter.match), params)
        if (!filter.showRead) conditions += "i.read_at IS NULL"
        return jdbc.pageRows(conditions, params, after, limit)
    }

    override fun neighbours(
        owner: Long,
        position: ListPosition,
        filter: ListFilter,
    ): Neighbours =
        Neighbours(
            previous = jdbc.neighbourRow(owner, position, filter, newer = true),
            next = jdbc.neighbourRow(owner, position, filter, newer = false),
        )

    override fun find(
        owner: Long,
        id: Long,
    ): ItemRef? =
        jdbc
            .sql(
                "SELECT i.id, i.link, i.title, s.title AS source_title, i.thumbnail_url, " +
                    "i.favourited_at IS NOT NULL AS is_favourite, i.audio_url, i.audio_duration, i.play_position " +
                    "FROM items i JOIN sources s ON s.id = i.source_id WHERE i.id = ? AND s.owner_id = ?",
            ).params(id, owner)
            .query { rs, _ ->
                ItemRef(
                    rs.getLong("id"),
                    rs.getString("link"),
                    rs.getString("title"),
                    rs.getString("source_title"),
                    rs.getString("thumbnail_url"),
                    favourite = rs.getInt("is_favourite") == 1,
                    audio = rs.audio(),
                    position = rs.getLong("play_position").takeUnless { rs.wasNull() },
                )
            }.optional()
            .orElse(null)

    override fun findPost(
        owner: Long,
        id: Long,
    ): PostRef? =
        jdbc
            .sql(
                "SELECT i.id, i.link, i.title, s.title AS source_title, i.published_at, i.body, " +
                    "i.thumbnail_url, s.favicon_url, i.favourited_at IS NOT NULL AS is_favourite, " +
                    "i.article_checked_at, i.later_at IS NOT NULL AS is_later, s.url AS source_url, i.audio_url " +
                    "FROM items i JOIN sources s ON s.id = i.source_id WHERE i.id = ? AND s.owner_id = ?",
            ).params(id, owner)
            .query { rs, _ ->
                PostRef(
                    rs.getLong("id"),
                    rs.getString("link"),
                    rs.getString("title"),
                    rs.getString("source_title"),
                    Instant.ofEpochMilli(rs.getLong("published_at")),
                    rs.getString("body"),
                    rs.getString("thumbnail_url"),
                    rs.getString("favicon_url"),
                    rs.getInt("is_favourite") == 1,
                    rs.getLong("article_checked_at").takeUnless { rs.wasNull() }?.let(Instant::ofEpochMilli),
                    rs.getInt("is_later") == 1,
                    rs.getString("source_url"),
                    rs.getString("audio_url"),
                )
            }.optional()
            .orElse(null)

    override fun findDetail(
        owner: Long,
        id: Long,
    ): DetailRef? =
        jdbc
            .sql(
                "SELECT i.id, i.link, i.title, s.title AS source_title, i.published_at, i.author, " +
                    "i.thumbnail_url, i.body, i.oembed_json, i.later_at IS NOT NULL AS is_later, " +
                    "i.favourited_at IS NOT NULL AS is_favourite, s.url AS source_url, i.audio_url " +
                    "FROM items i JOIN sources s ON s.id = i.source_id WHERE i.id = ? AND s.owner_id = ?",
            ).params(id, owner)
            .query { rs, _ ->
                DetailRef(
                    rs.getLong("id"),
                    rs.getString("link"),
                    rs.getString("title"),
                    rs.getString("source_title"),
                    Instant.ofEpochMilli(rs.getLong("published_at")),
                    rs.getString("author"),
                    rs.getString("thumbnail_url"),
                    rs.getString("body"),
                    OembedJson.decode(rs.getString("oembed_json")),
                    rs.getInt("is_later") == 1,
                    rs.getString("source_url"),
                    favourite = rs.getInt("is_favourite") == 1,
                    audioUrl = rs.getString("audio_url"),
                )
            }.optional()
            .orElse(null)

    override fun saveOembed(
        id: Long,
        oembed: Oembed,
    ) {
        jdbc
            .sql("UPDATE items SET oembed_json = ? WHERE id = ?")
            .params(OembedJson.encode(oembed), id)
            .update()
    }

    override fun markRead(
        owner: Long,
        id: Long,
        mark: ReadMark,
    ): Boolean {
        val at = mark.at.toEpochMilli()
        return jdbc
            .sql(
                "UPDATE items SET read_at = COALESCE(read_at, ?), opened_at = COALESCE(opened_at, ?) " +
                    "WHERE id = ? AND $OWNED",
            ).params(at, if (mark.opened) at else null, id, owner)
            .update() > 0
    }

    override fun markAllRead(
        owner: Long,
        firstSeenCutoff: Instant,
        at: Instant,
        within: Narrowing,
    ): Int {
        val params = mutableListOf<Any>(at.toEpochMilli(), firstSeenCutoff.toEpochMilli())
        val conditions = narrowed(owner, within, params)
        return jdbc
            .sql(
                "UPDATE items SET read_at = ? WHERE id IN (SELECT i.id $TIMELINE_FROM " +
                    "WHERE i.read_at IS NULL AND i.first_seen_at <= ? AND ${conditions.joinToString(" AND ")})",
            ).params(params)
            .update()
    }
}

/** Restricts an `items` write to the rows of one owner's sources; its parameter is the owner id. */
internal const val OWNED = "source_id IN (SELECT id FROM sources WHERE owner_id = ?)"

/** The rows matching [conditions] and the cursor, newest first, at most [limit] of them. */
internal fun JdbcClient.pageRows(
    conditions: MutableList<String>,
    params: MutableList<Any>,
    after: Cursor?,
    limit: Int,
): List<TimelineItem> {
    if (after != null) {
        conditions += "(i.published_at < ? OR (i.published_at = ? AND i.id < ?))"
        params.addAll(listOf(after.publishedAt.toEpochMilli(), after.publishedAt.toEpochMilli(), after.id))
    }
    val where = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")
    return sql(
        "SELECT $TIMELINE_COLUMNS $TIMELINE_FROM $where ORDER BY i.published_at DESC, i.id DESC LIMIT ?",
    ).params(params + limit)
        .query { rs, _ -> rs.toTimelineItem() }
        .list()
}

/** The columns every timeline row query selects, in the shape [toTimelineItem] reads. */
internal const val TIMELINE_COLUMNS =
    "i.id, s.title AS source_title, i.title, i.published_at, i.thumbnail_url, i.link, s.favicon_url, " +
        "i.read_at IS NOT NULL AS is_read, i.favourited_at IS NOT NULL AS is_favourite, " +
        "i.later_at IS NOT NULL AS is_later, i.audio_url, i.play_position"

/** The tables a timeline row query reads, with the source join. */
internal const val TIMELINE_FROM = "FROM items i JOIN sources s ON s.id = i.source_id"

/** One timeline row from a joined query: the item, its source icon, and its read, favourite and later state. */
internal fun ResultSet.toTimelineItem() =
    TimelineItem(
        getLong("id"),
        getString("source_title"),
        getString("title"),
        Instant.ofEpochMilli(getLong("published_at")),
        getString("thumbnail_url"),
        getInt("is_read") == 1,
        Playable.of(getString("link"), getString("audio_url")) != null,
        getString("favicon_url"),
        getInt("is_favourite") == 1,
        getInt("is_later") == 1,
        getLong("play_position").takeUnless { wasNull() },
    )

/**
 * The nearest row of the list [filter] narrows to on one side of [position], in the list's newest-first order:
 * the row just above it when [newer], just below it otherwise.
 */
private fun JdbcClient.neighbourRow(
    owner: Long,
    position: ListPosition,
    filter: ListFilter,
    newer: Boolean,
): TimelineItem? {
    val params = mutableListOf<Any>()
    val conditions = listedConditions(owner, filter, params)
    val at = position.publishedAt.toEpochMilli()
    conditions +=
        if (newer) {
            "(i.published_at > ? OR (i.published_at = ? AND i.id > ?))"
        } else {
            "(i.published_at < ? OR (i.published_at = ? AND i.id < ?))"
        }
    params.addAll(listOf(at, at, position.id))
    val order = if (newer) "ASC" else "DESC"
    return sql(
        "SELECT $TIMELINE_COLUMNS $TIMELINE_FROM WHERE ${conditions.joinToString(" AND ")} " +
            "ORDER BY i.published_at $order, i.id $order LIMIT 1",
    ).params(params)
        .query { rs, _ -> rs.toTimelineItem() }
        .optional()
        .orElse(null)
}

/** The conditions that keep the rows [filter] lists, their parameters appended to [params] in the same order. */
private fun listedConditions(
    owner: Long,
    filter: ListFilter,
    params: MutableList<Any>,
): MutableList<String> {
    if (filter.favourites) {
        params.add(owner)
        return mutableListOf("s.owner_id = ?", "i.favourited_at IS NOT NULL")
    }
    val conditions = narrowed(owner, Narrowing(filter.groupId, filter.match), params)
    if (!filter.showRead) {
        val seen = filter.seen
        if (seen == null) {
            conditions += "i.read_at IS NULL"
        } else {
            conditions += "(i.read_at IS NULL OR i.read_at >= ?)"
            params.add(seen)
        }
    }
    return conditions
}

private const val UPSERT_SQL =
    "INSERT INTO items (source_id, item_key, title, link, published_at, author, thumbnail_url, body, first_seen_at, " +
        "audio_url, audio_duration) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (source_id, item_key) DO UPDATE " +
        "SET thumbnail_url = COALESCE(items.thumbnail_url, excluded.thumbnail_url), " +
        "body = COALESCE(items.body, excluded.body) RETURNING id"

/**
 * Inserts a new item; for a known one only fills in a thumbnail or body it did not have yet. The search index
 * row is written for a new item, and again when a body is filled in (the only change that alters indexed text).
 */
private fun JdbcClient.upsertItem(
    search: SearchIndex,
    sourceId: Long,
    item: FeedItem,
    firstSeenAt: Instant,
) {
    val storedBodies =
        this
            .sql("SELECT body FROM items WHERE source_id = ? AND item_key = ?")
            .params(sourceId, item.key)
            .query { rs, _ -> rs.getString("body") }
            .list()
    val id =
        this
            .sql(UPSERT_SQL)
            .params(upsertParams(sourceId, item, firstSeenAt))
            .query(Long::class.java)
            .single()
    val isNew = storedBodies.isEmpty()
    val bodyFilledIn = storedBodies.singleOrNull() == null && item.body != null
    if (isNew || bodyFilledIn) search.index(id)
}

/** The values of [UPSERT_SQL]'s placeholders, in order. */
private fun upsertParams(
    sourceId: Long,
    item: FeedItem,
    firstSeenAt: Instant,
): List<Any?> =
    listOf(
        sourceId,
        item.key,
        item.title,
        item.link,
        (item.publishedAt ?: firstSeenAt).toEpochMilli(),
        item.author,
        item.thumbnailUrl,
        item.body,
        firstSeenAt.toEpochMilli(),
        item.audio?.url,
        item.audio?.durationSeconds,
    )

/** The audio enclosure of an item row that selected `audio_url` and `audio_duration`; null when it has none. */
private fun ResultSet.audio(): AudioEnclosure? =
    getString("audio_url")?.let { AudioEnclosure(it, getLong("audio_duration").takeUnless { wasNull() }) }
