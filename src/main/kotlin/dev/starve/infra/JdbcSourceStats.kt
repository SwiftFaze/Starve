package dev.starve.infra

import dev.starve.application.SourceStatsRepository
import dev.starve.domain.SourceActivity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

/** One grouped pass over the owner's items; a source with no items kept has no row and counts as empty. */
@Repository
class JdbcSourceStats(
    private val jdbc: JdbcClient,
) : SourceStatsRepository {
    override fun activity(
        owner: Long,
        windowStart: Instant,
        now: Instant,
    ): Map<Long, SourceActivity> =
        jdbc
            .sql(STATS)
            .params(windowStart.toEpochMilli(), now.toEpochMilli(), owner)
            .query { rs, _ ->
                rs.getLong("source_id") to
                    SourceActivity(
                        lastPostAt = Instant.ofEpochMilli(rs.getLong("last_post")),
                        recentPosts = rs.getInt("recent"),
                        unread = rs.getInt("unread"),
                        trackedReads = rs.getInt("tracked"),
                        skipped = rs.getInt("skipped"),
                    )
            }.list()
            .toMap()

    private companion object {
        // Reads before open tracking began (V16's opens_tracked_since) are neither reads nor skips here.
        const val STATS =
            "WITH since AS (SELECT CAST(value AS INTEGER) AS at FROM app_metadata WHERE key = 'opens_tracked_since') " +
                "SELECT i.source_id, MAX(i.published_at) AS last_post, " +
                "SUM(i.published_at > ? AND i.published_at <= ?) AS recent, " +
                "SUM(i.read_at IS NULL) AS unread, " +
                "SUM(i.read_at >= (SELECT at FROM since)) AS tracked, " +
                "SUM(i.read_at >= (SELECT at FROM since) AND i.opened_at IS NULL) AS skipped " +
                "FROM items i JOIN sources s ON s.id = i.source_id WHERE s.owner_id = ? GROUP BY i.source_id"
    }
}
