package dev.starve.infra

import dev.starve.application.Narrowing
import dev.starve.application.TagRepository
import dev.starve.domain.Tag
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository
class JdbcTagRepository(
    private val jdbc: JdbcClient,
) : TagRepository {
    override fun findAll(owner: Long): List<Tag> =
        jdbc
            .sql("SELECT $COLUMNS FROM tags WHERE owner_id = ? ORDER BY name COLLATE NOCASE, id")
            .param(owner)
            .query { rs, _ -> rs.toTag() }
            .list()

    override fun findById(
        owner: Long,
        id: Long,
    ): Tag? =
        jdbc
            .sql("SELECT $COLUMNS FROM tags WHERE id = ? AND owner_id = ?")
            .params(id, owner)
            .query { rs, _ -> rs.toTag() }
            .optional()
            .orElse(null)

    override fun findByName(
        owner: Long,
        name: String,
    ): Tag? =
        jdbc
            .sql("SELECT $COLUMNS FROM tags WHERE owner_id = ? AND name = ? COLLATE NOCASE")
            .params(owner, name)
            .query { rs, _ -> rs.toTag() }
            .optional()
            .orElse(null)

    override fun insert(
        owner: Long,
        tag: Tag,
    ): Tag {
        val id =
            jdbc
                .sql(
                    "INSERT INTO tags (owner_id, name, terms, match_body, group_id) VALUES (?, ?, ?, ?, ?) " +
                        "RETURNING id",
                ).params(owner, tag.name, tag.termsText, tag.matchBody, tag.groupId)
                .query(Long::class.java)
                .single()
        return tag.copy(id = id)
    }

    override fun update(
        owner: Long,
        tag: Tag,
    ) {
        jdbc
            .sql("UPDATE tags SET name = ?, terms = ?, match_body = ?, group_id = ? WHERE id = ? AND owner_id = ?")
            .params(tag.name, tag.termsText, tag.matchBody, tag.groupId, tag.id, owner)
            .update()
    }

    override fun delete(
        owner: Long,
        id: Long,
    ): Boolean = jdbc.sql("DELETE FROM tags WHERE id = ? AND owner_id = ?").params(id, owner).update() > 0

    override fun unreadCount(
        owner: Long,
        within: Narrowing,
    ): Int {
        val params = mutableListOf<Any>()
        val conditions = narrowed(owner, within, params)
        return jdbc
            .sql(
                "SELECT COUNT(*) FROM items i JOIN sources s ON s.id = i.source_id " +
                    "WHERE i.read_at IS NULL AND ${conditions.joinToString(" AND ")}",
            ).params(params)
            .query(Int::class.java)
            .single()
    }

    private fun ResultSet.toTag() =
        Tag(
            getLong("id"),
            getString("name"),
            getString("terms").split(", "),
            getInt("match_body") == 1,
            getLong("group_id").takeUnless { wasNull() },
        )

    private companion object {
        const val COLUMNS = "id, name, terms, match_body, group_id"
    }
}

/**
 * The conditions (on `items i` joined to `sources s`) that keep [owner]'s rows [within] narrows to, their parameters
 * appended to [params] in the same order.
 */
internal fun narrowed(
    owner: Long,
    within: Narrowing,
    params: MutableList<Any>,
): MutableList<String> {
    val conditions = mutableListOf("s.owner_id = ?")
    params.add(owner)
    within.groupId?.let {
        conditions += "s.group_id = ?"
        params.add(it)
    }
    within.match?.let {
        conditions += "i.id IN (SELECT rowid FROM item_search WHERE item_search MATCH ?)"
        params.add(it)
    }
    return conditions
}
