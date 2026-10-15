package dev.starve.infra

import dev.starve.application.LaterRepository
import dev.starve.domain.LaterCursor
import dev.starve.domain.SavedItem
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class JdbcLaterRepository(
    private val jdbc: JdbcClient,
) : LaterRepository {
    override fun later(
        owner: Long,
        after: LaterCursor?,
        limit: Int,
    ): List<SavedItem> {
        val params = mutableListOf<Any>(owner)
        val cursor =
            if (after == null) {
                ""
            } else {
                params.addAll(listOf(after.savedAt.toEpochMilli(), after.savedAt.toEpochMilli(), after.id))
                " AND (i.later_at > ? OR (i.later_at = ? AND i.id > ?))"
            }
        params.add(limit)
        val sql =
            "SELECT $TIMELINE_COLUMNS, i.later_at AS saved_at $TIMELINE_FROM " +
                "WHERE s.owner_id = ? AND i.later_at IS NOT NULL$cursor ORDER BY i.later_at ASC, i.id ASC LIMIT ?"
        return jdbc
            .sql(sql)
            .params(params)
            .query { rs, _ -> SavedItem(rs.toTimelineItem(), Instant.ofEpochMilli(rs.getLong("saved_at"))) }
            .list()
    }

    override fun save(
        owner: Long,
        id: Long,
        at: Instant,
    ): Boolean =
        jdbc
            .sql("UPDATE items SET later_at = COALESCE(later_at, ?) WHERE id = ? AND $OWNED")
            .params(at.toEpochMilli(), id, owner)
            .update() > 0

    override fun clear(
        owner: Long,
        id: Long,
    ): Boolean =
        jdbc
            .sql("UPDATE items SET later_at = NULL WHERE id = ? AND $OWNED")
            .params(id, owner)
            .update() > 0
}
