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
        after: Cursor?,
        limit: Int,
    ): List<TimelineItem> = jdbc.pageRows(mutableListOf("i.favourited_at IS NOT NULL"), mutableListOf(), after, limit)

    override fun favourite(
        id: Long,
        at: Instant,
    ): Boolean =
        jdbc
            .sql("UPDATE items SET favourited_at = COALESCE(favourited_at, ?) WHERE id = ?")
            .params(at.toEpochMilli(), id)
            .update() > 0

    override fun unfavourite(id: Long): Boolean =
        jdbc
            .sql("UPDATE items SET favourited_at = NULL WHERE id = ?")
            .param(id)
            .update() > 0
}
