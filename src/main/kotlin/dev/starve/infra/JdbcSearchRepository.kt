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
        match: String,
        after: Cursor?,
        limit: Int,
    ): List<TimelineItem> {
        val cursor = if (after == null) "" else "AND (i.published_at < ? OR (i.published_at = ? AND i.id < ?))"
        val params = mutableListOf<Any>(match)
        if (after != null) {
            params.addAll(listOf(after.publishedAt.toEpochMilli(), after.publishedAt.toEpochMilli(), after.id))
        }
        return jdbc
            .sql(
                "SELECT i.id, s.title AS source_title, i.title, i.published_at, i.thumbnail_url, i.link, " +
                    "s.favicon_url, i.read_at IS NOT NULL AS is_read, i.favourited_at IS NOT NULL AS is_favourite " +
                    "FROM item_search " +
                    "JOIN items i ON i.id = item_search.rowid JOIN sources s ON s.id = i.source_id " +
                    "WHERE item_search MATCH ? $cursor ORDER BY i.published_at DESC, i.id DESC LIMIT ?",
            ).params(params + limit)
            .query { rs, _ -> rs.toTimelineItem() }
            .list()
    }
}
