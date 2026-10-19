package dev.starve.infra

import dev.starve.application.PositionRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class JdbcPositionRepository(
    private val jdbc: JdbcClient,
) : PositionRepository {
    override fun save(
        owner: Long,
        id: Long,
        seconds: Long?,
    ): Boolean =
        jdbc
            .sql("UPDATE items SET play_position = ? WHERE id = ? AND $OWNED")
            .params(seconds, id, owner)
            .update() > 0
}
