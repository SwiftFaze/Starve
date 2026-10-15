package dev.starve.infra

import dev.starve.application.SourceRepository
import dev.starve.domain.Source
import dev.starve.domain.Validators
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.Instant

@Repository
class JdbcSourceRepository(
    private val jdbc: JdbcClient,
    private val search: SearchIndex,
) : SourceRepository {
    override fun findAll(): List<Source> =
        jdbc
            .sql("SELECT * FROM sources ORDER BY id")
            .query { rs, _ -> rs.toSource() }
            .list()

    override fun findAllOf(owner: Long): List<Source> =
        jdbc
            .sql("SELECT * FROM sources WHERE owner_id = ? ORDER BY id")
            .param(owner)
            .query { rs, _ -> rs.toSource() }
            .list()

    override fun findById(id: Long): Source? =
        jdbc
            .sql("SELECT * FROM sources WHERE id = ?")
            .param(id)
            .query { rs, _ -> rs.toSource() }
            .optional()
            .orElse(null)

    override fun existsByUrl(
        owner: Long,
        url: String,
    ): Boolean =
        jdbc
            .sql("SELECT COUNT(*) FROM sources WHERE owner_id = ? AND url = ?")
            .params(owner, url)
            .query(Long::class.java)
            .single() > 0

    override fun insert(
        owner: Long,
        url: String,
        title: String,
    ): Source {
        val id =
            jdbc
                .sql("INSERT INTO sources (owner_id, url, title) VALUES (?, ?, ?) RETURNING id")
                .params(owner, url, title)
                .query(Long::class.java)
                .single()
        return Source(id, url, title, Validators(), null, null)
    }

    /** The search rows of the source's items go with them, through the `items_search_cleanup` trigger. */
    override fun delete(
        owner: Long,
        id: Long,
    ): Boolean =
        jdbc
            .sql("DELETE FROM sources WHERE id = ? AND owner_id = ?")
            .params(id, owner)
            .update() > 0

    /** A group the owner does not own cannot be assigned: the update then matches no row. */
    override fun assignGroup(
        owner: Long,
        sourceId: Long,
        groupId: Long?,
    ): Boolean =
        jdbc
            .sql(
                "UPDATE sources SET group_id = ? WHERE id = ? AND owner_id = ? " +
                    "AND (? IS NULL OR EXISTS (SELECT 1 FROM groups WHERE id = ? AND owner_id = ?))",
            ).params(groupId, sourceId, owner, groupId, groupId, owner)
            .update() > 0

    @Transactional
    override fun recordSuccess(
        id: Long,
        title: String,
        validators: Validators,
        at: Instant,
    ) {
        val previous =
            jdbc
                .sql("SELECT title FROM sources WHERE id = ?")
                .param(id)
                .query(String::class.java)
                .single()
        jdbc
            .sql(
                "UPDATE sources SET title = ?, etag = ?, last_modified = ?, " +
                    "last_fetched_at = ?, last_error = NULL WHERE id = ?",
            ).params(listOf(title, validators.etag, validators.lastModified, at.toEpochMilli(), id))
            .update()
        if (previous != title) search.renameSource(id, title)
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
