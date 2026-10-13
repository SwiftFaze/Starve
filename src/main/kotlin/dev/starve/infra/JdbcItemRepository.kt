package dev.starve.infra

import dev.starve.application.DetailRef
import dev.starve.application.ItemRef
import dev.starve.application.ItemRepository
import dev.starve.application.Oembed
import dev.starve.application.PostRef
import dev.starve.domain.Cursor
import dev.starve.domain.FeedItem
import dev.starve.domain.Playable
import dev.starve.domain.TimelineItem
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant

@Repository
class JdbcItemRepository(
    private val jdbc: JdbcClient,
) : ItemRepository {
    override fun insertNew(
        sourceId: Long,
        items: List<FeedItem>,
        firstSeenAt: Instant,
    ) {
        items.forEach { upsert(sourceId, it, firstSeenAt) }
    }

    override fun deleteFirstSeenBefore(cutoff: Instant): Int =
        jdbc
            .sql("DELETE FROM items WHERE first_seen_at < ?")
            .param(cutoff.toEpochMilli())
            .update()

    override fun timeline(
        after: Cursor?,
        showRead: Boolean,
        limit: Int,
        groupId: Long?,
    ): List<TimelineItem> {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()
        if (!showRead) conditions += "i.read_at IS NULL"
        if (groupId != null) {
            conditions += "s.group_id = ?"
            params += groupId
        }
        if (after != null) {
            conditions += "(i.published_at < ? OR (i.published_at = ? AND i.id < ?))"
            params.addAll(listOf(after.publishedAt.toEpochMilli(), after.publishedAt.toEpochMilli(), after.id))
        }
        val where = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")
        return jdbc
            .sql(
                "SELECT i.id, s.title AS source_title, i.title, i.published_at, i.thumbnail_url, i.link, " +
                    "s.favicon_url, i.read_at IS NOT NULL AS is_read " +
                    "FROM items i JOIN sources s ON s.id = i.source_id " +
                    "$where ORDER BY i.published_at DESC, i.id DESC LIMIT ?",
            ).params(params + limit)
            .query { rs, _ -> rs.toTimelineItem() }
            .list()
    }

    override fun find(id: Long): ItemRef? =
        jdbc
            .sql(
                "SELECT i.id, i.link, i.title, s.title AS source_title, i.thumbnail_url " +
                    "FROM items i JOIN sources s ON s.id = i.source_id WHERE i.id = ?",
            ).param(id)
            .query { rs, _ ->
                ItemRef(
                    rs.getLong("id"),
                    rs.getString("link"),
                    rs.getString("title"),
                    rs.getString("source_title"),
                    rs.getString("thumbnail_url"),
                )
            }.optional()
            .orElse(null)

    override fun findPost(id: Long): PostRef? =
        jdbc
            .sql(
                "SELECT i.id, i.link, i.title, s.title AS source_title, i.published_at, i.body, " +
                    "i.thumbnail_url, s.favicon_url FROM items i JOIN sources s ON s.id = i.source_id WHERE i.id = ?",
            ).param(id)
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
                )
            }.optional()
            .orElse(null)

    override fun findDetail(id: Long): DetailRef? =
        jdbc
            .sql(
                "SELECT i.id, i.link, i.title, s.title AS source_title, i.published_at, i.author, " +
                    "i.thumbnail_url, i.body, i.oembed_json " +
                    "FROM items i JOIN sources s ON s.id = i.source_id WHERE i.id = ?",
            ).param(id)
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
        id: Long,
        at: Instant,
    ): Boolean =
        jdbc
            .sql("UPDATE items SET read_at = COALESCE(read_at, ?) WHERE id = ?")
            .params(at.toEpochMilli(), id)
            .update() > 0

    override fun markAllRead(
        firstSeenCutoff: Instant,
        at: Instant,
        groupId: Long?,
    ): Int =
        jdbc
            .sql(
                "UPDATE items SET read_at = ? WHERE read_at IS NULL AND first_seen_at <= ? " +
                    "AND (? IS NULL OR source_id IN (SELECT id FROM sources WHERE group_id = ?))",
            ).params(at.toEpochMilli(), firstSeenCutoff.toEpochMilli(), groupId, groupId)
            .update()

    /** Inserts a new item; for a known one only fills in a thumbnail or body it did not have yet. */
    private fun upsert(
        sourceId: Long,
        item: FeedItem,
        firstSeenAt: Instant,
    ) {
        jdbc
            .sql(
                "INSERT INTO items (source_id, item_key, title, link, published_at, " +
                    "author, thumbnail_url, body, first_seen_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (source_id, item_key) DO UPDATE " +
                    "SET thumbnail_url = COALESCE(items.thumbnail_url, excluded.thumbnail_url), " +
                    "body = COALESCE(items.body, excluded.body)",
            ).params(
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
                ),
            ).update()
    }
}

/** One timeline row from a joined query: the item, its source title, and whether it is read or playable. */
private fun ResultSet.toTimelineItem() =
    TimelineItem(
        getLong("id"),
        getString("source_title"),
        getString("title"),
        Instant.ofEpochMilli(getLong("published_at")),
        getString("thumbnail_url"),
        getInt("is_read") == 1,
        Playable.of(getString("link")) != null,
        getString("favicon_url"),
    )
