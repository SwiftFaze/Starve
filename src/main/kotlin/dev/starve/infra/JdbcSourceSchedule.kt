package dev.starve.infra

import dev.starve.application.SourceScheduleRepository
import dev.starve.domain.PollInterval
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class JdbcSourceSchedule(
    private val jdbc: JdbcClient,
) : SourceScheduleRepository {
    override fun setInterval(
        owner: Long,
        id: Long,
        interval: PollInterval?,
    ): Boolean =
        jdbc
            .sql("UPDATE sources SET poll_interval_minutes = ? WHERE id = ? AND owner_id = ?")
            .params(interval?.minutes, id, owner)
            .update() > 0
}
