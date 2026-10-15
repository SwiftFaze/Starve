package dev.starve.infra

import dev.starve.application.GroupRepository
import dev.starve.domain.Group
import dev.starve.domain.GroupSummary
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository
class JdbcGroupRepository(
    private val jdbc: JdbcClient,
) : GroupRepository {
    override fun findAll(owner: Long): List<Group> =
        jdbc
            .sql("SELECT id, name FROM groups WHERE owner_id = ? ORDER BY name COLLATE NOCASE, id")
            .param(owner)
            .query { rs, _ -> rs.toGroup() }
            .list()

    override fun findById(
        owner: Long,
        id: Long,
    ): Group? =
        jdbc
            .sql("SELECT id, name FROM groups WHERE id = ? AND owner_id = ?")
            .params(id, owner)
            .query { rs, _ -> rs.toGroup() }
            .optional()
            .orElse(null)

    override fun findByName(
        owner: Long,
        name: String,
    ): Group? =
        jdbc
            .sql("SELECT id, name FROM groups WHERE owner_id = ? AND name = ? COLLATE NOCASE")
            .params(owner, name)
            .query { rs, _ -> rs.toGroup() }
            .optional()
            .orElse(null)

    override fun insert(
        owner: Long,
        name: String,
    ): Group {
        val id =
            jdbc
                .sql("INSERT INTO groups (owner_id, name) VALUES (?, ?) RETURNING id")
                .params(owner, name)
                .query(Long::class.java)
                .single()
        return Group(id, name)
    }

    override fun rename(
        owner: Long,
        id: Long,
        name: String,
    ) {
        jdbc.sql("UPDATE groups SET name = ? WHERE id = ? AND owner_id = ?").params(name, id, owner).update()
    }

    override fun delete(
        owner: Long,
        id: Long,
    ): Boolean = jdbc.sql("DELETE FROM groups WHERE id = ? AND owner_id = ?").params(id, owner).update() > 0

    override fun unreadCounts(owner: Long): List<GroupSummary> =
        jdbc
            .sql(
                "SELECT g.id, g.name, COUNT(i.id) AS unread FROM groups g " +
                    "LEFT JOIN sources s ON s.group_id = g.id " +
                    "LEFT JOIN items i ON i.source_id = s.id AND i.read_at IS NULL " +
                    "WHERE g.owner_id = ? " +
                    "GROUP BY g.id, g.name ORDER BY g.name COLLATE NOCASE, g.id",
            ).param(owner)
            .query { rs, _ -> GroupSummary(rs.toGroup(), rs.getInt("unread")) }
            .list()

    override fun totalUnread(owner: Long): Int =
        jdbc
            .sql(
                "SELECT COUNT(*) FROM items i JOIN sources s ON s.id = i.source_id " +
                    "WHERE i.read_at IS NULL AND s.owner_id = ?",
            ).param(owner)
            .query(Int::class.java)
            .single()

    private fun ResultSet.toGroup() = Group(getLong("id"), getString("name"))
}
