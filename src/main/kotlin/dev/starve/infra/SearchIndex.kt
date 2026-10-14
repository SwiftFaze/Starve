package dev.starve.infra

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Keeps `item_search` (FTS5, rowid = `items.id`) in step with `items` and `sources`. Every items write calls
 * into here inside the same transaction, because SQL triggers cannot see the source name or strip body markup.
 */
@Component
class SearchIndex(
    private val jdbc: JdbcClient,
) {
    /** Rewrites the index row of item [itemId] from its stored title, source name and body text. */
    fun index(itemId: Long) {
        val (title, source, body) =
            jdbc
                .sql(
                    "SELECT i.title, s.title AS source_title, i.body FROM items i " +
                        "JOIN sources s ON s.id = i.source_id WHERE i.id = ?",
                ).param(itemId)
                .query { rs, _ -> Triple(rs.getString("title"), rs.getString("source_title"), rs.getString("body")) }
                .single()
        jdbc.sql("DELETE FROM item_search WHERE rowid = ?").param(itemId).update()
        jdbc
            .sql("INSERT INTO item_search (rowid, title, source, body) VALUES (?, ?, ?, ?)")
            .params(itemId, title, source, SearchText.bodyText(body))
            .update()
    }

    fun renameSource(
        sourceId: Long,
        title: String,
    ) {
        jdbc
            .sql("UPDATE item_search SET source = ? WHERE rowid IN (SELECT id FROM items WHERE source_id = ?)")
            .params(title, sourceId)
            .update()
    }

    fun removeOfSource(sourceId: Long) {
        jdbc
            .sql("DELETE FROM item_search WHERE rowid IN (SELECT id FROM items WHERE source_id = ?)")
            .param(sourceId)
            .update()
    }

    fun removeFirstSeenBefore(cutoff: Instant) {
        jdbc
            .sql(PURGE_SQL)
            .param(cutoff.toEpochMilli())
            .update()
    }

    private companion object {
        const val PURGE_SQL =
            "DELETE FROM item_search WHERE rowid IN " +
                "(SELECT id FROM items WHERE first_seen_at < ? AND favourited_at IS NULL)"
    }
}
