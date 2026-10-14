package dev.starve.infra

import dev.starve.application.SourceRepository
import dev.starve.domain.Source
import dev.starve.domain.Validators
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant

@Repository
class JdbcSourceRepository(
    private val jdbc: JdbcClient,
) : SourceRepository {
    override fun findAll(): List<Source> =
        jdbc
            .sql("SELECT * FROM sources ORDER BY id")
            .query { rs, _ -> rs.toSource() }
            .list()

    override fun findById(id: Long): Source? =
        jdbc
            .sql("SELECT * FROM sources WHERE id = ?")
            .param(id)
            .query { rs, _ -> rs.toSource() }
            .optional()
            .orElse(null)

    override fun existsByUrl(url: String): Boolean =
        jdbc
            .sql("SELECT COUNT(*) FROM sources WHERE url = ?")
            .param(url)
            .query(Long::class.java)
            .single() > 0

    override fun insert(
        url: String,
        title: String,
    ): Source {
        val id =
            jdbc
                .sql("INSERT INTO sources (url, title) VALUES (?, ?) RETURNING id")
                .params(url, title)
                .query(Long::class.java)
                .single()
        return Source(id, url, title, Validators(), null, null)
    }

    override fun delete(id: Long) {
        jdbc.sql("DELETE FROM sources WHERE id = ?").param(id).update()
    }

    override fun assignGroup(
        sourceId: Long,
        groupId: Long?,
    ) {
        jdbc.sql("UPDATE sources SET group_id = ? WHERE id = ?").params(groupId, sourceId).update()
    }

    override fun recordSuccess(
        id: Long,
        title: String,
        validators: Validators,
        at: Instant,
    ) {
        jdbc
            .sql(
                "UPDATE sources SET title = ?, etag = ?, last_modified = ?, " +
                    "last_fetched_at = ?, last_error = NULL WHERE id = ?",
            ).params(listOf(title, validators.etag, validators.lastModified, at.toEpochMilli(), id))
            .update()
    }

    override fun recordFailure(
        id: Long,
        error: String,
        at: Instant,
    ) {
        jdbc
            .sql("UPDATE sources SET last_fetched_at = ?, last_error = ? WHERE id = ?")
            .params(at.toEpochMilli(), error, id)
            .update()
    }

    override fun recordFavicon(
        id: Long,
        url: String?,
        at: Instant,
    ) {
        jdbc
            .sql("UPDATE sources SET favicon_url = ?, favicon_checked_at = ? WHERE id = ?")
            .params(listOf(url, at.toEpochMilli(), id))
            .update()
    }
}

private fun ResultSet.toSource() =
    Source(
        id = getLong("id"),
        url = getString("url"),
        title = getString("title"),
        validators = Validators(getString("etag"), getString("last_modified")),
        lastFetchedAt = getString("last_fetched_at")?.let { Instant.ofEpochMilli(it.toLong()) },
        lastError = getString("last_error"),
        groupId = getLong("group_id").takeUnless { wasNull() },
        faviconUrl = getString("favicon_url"),
        faviconCheckedAt = getString("favicon_checked_at")?.let { Instant.ofEpochMilli(it.toLong()) },
    )
