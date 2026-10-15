package dev.starve.infra

import dev.starve.application.FavouriteRepository
import dev.starve.domain.Cursor
import dev.starve.domain.TimelineItem
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class JdbcFavouriteRepository(
    private val jdbc: JdbcClient,
) : FavouriteRepository {
    override fun favourites(
        owner: Long,
        after: Cursor?,
        limit: Int,
    ): List<TimelineItem> =
        jdbc.pageRows(
            mutableListOf("s.owner_id = ?", "i.favourited_at IS NOT NULL"),
            mutableListOf(owner),
            after,
            limit,
        )

    override fun favourite(
        owner: Long,
        id: Long,
        at: Instant,
    ): Boolean =
        jdbc
            .sql("UPDATE items SET favourited_at = COALESCE(favourited_at, ?) WHERE id = ? AND $OWNED")
            .params(at.toEpochMilli(), id, owner)
            .update() > 0

    override fun unfavourite(
        owner: Long,
        id: Long,
    ): Boolean =
        jdbc
            .sql("UPDATE items SET favourited_at = NULL WHERE id = ? AND $OWNED")
            .params(id, owner)
            .update() > 0
}
