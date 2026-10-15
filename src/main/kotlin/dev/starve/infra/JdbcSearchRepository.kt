package dev.starve.infra

import dev.starve.application.SearchRepository
import dev.starve.domain.Cursor
import dev.starve.domain.TimelineItem
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class JdbcSearchRepository(
    private val jdbc: JdbcClient,
) : SearchRepository {
    override fun search(
        owner: Long,
        match: String,
        after: Cursor?,
        limit: Int,
    ): List<TimelineItem> {
        val cursor = if (after == null) "" else "AND (i.published_at < ? OR (i.published_at = ? AND i.id < ?))"
        val params = mutableListOf<Any>(match, owner)
        if (after != null) {
            params.addAll(listOf(after.publishedAt.toEpochMilli(), after.publishedAt.toEpochMilli(), after.id))
        }
        return jdbc
            .sql(
                "SELECT $TIMELINE_COLUMNS FROM item_search " +
                    "JOIN items i ON i.id = item_search.rowid JOIN sources s ON s.id = i.source_id " +
                    "WHERE item_search MATCH ? AND s.owner_id = ? $cursor " +
                    "ORDER BY i.published_at DESC, i.id DESC LIMIT ?",
            ).params(params + limit)
            .query { rs, _ -> rs.toTimelineItem() }
            .list()
    }
}
